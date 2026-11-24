package com.webify.app.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.webify.app.data.db.ChatMessageEntity
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.data.prefs.AiProvider
import com.webify.app.data.prefs.AiSettings
import com.webify.app.data.prefs.SecurePrefs
import com.webify.app.data.repo.ChatRepository
import com.webify.app.data.repo.ProjectRepository
import com.webify.app.domain.ChatRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** Permintaan dari layar lain (mis. tombol "Analisis Error dengan AI" di konsol build). */
data class CopilotRequest(val projectId: String?, val prompt: String, val displayText: String)

/** Bus sederhana antar-layar untuk membuka Co-Pilot dengan prompt siap kirim. */
class CopilotBus {
    private val _pending = MutableStateFlow<CopilotRequest?>(null)
    val pending: StateFlow<CopilotRequest?> = _pending.asStateFlow()
    fun post(request: CopilotRequest) { _pending.value = request }
    fun consume(): CopilotRequest? = _pending.value.also { _pending.value = null }
}

enum class CodeTarget(val label: String) { CSS("CSS kustom"), JS_FINISHED("JS onPageFinished") }

data class CodeBlock(val language: String, val code: String) {
    val target: CodeTarget?
        get() = when (language.lowercase()) {
            "css", "scss" -> CodeTarget.CSS
            "js", "javascript", "ecmascript" -> CodeTarget.JS_FINISHED
            else -> null
        }
}

sealed class MessagePart {
    data class Text(val markdown: String) : MessagePart()
    data class Code(val block: CodeBlock) : MessagePart()
}

object MarkdownParser {
    private val fence = Regex("```([A-Za-z0-9_+-]*)[ \\t]*\\n([\\s\\S]*?)(```|$)")

    fun split(text: String): List<MessagePart> {
        val parts = mutableListOf<MessagePart>()
        var idx = 0
        for (m in fence.findAll(text)) {
            if (m.range.first > idx) text.substring(idx, m.range.first).takeIf { it.isNotBlank() }?.let { parts += MessagePart.Text(it.trim()) }
            parts += MessagePart.Code(CodeBlock(m.groupValues[1].ifBlank { "text" }, m.groupValues[2].trimEnd()))
            idx = m.range.last + 1
        }
        if (idx < text.length) text.substring(idx).takeIf { it.isNotBlank() }?.let { parts += MessagePart.Text(it.trim()) }
        return parts
    }
}

data class AiUiState(
    val projects: List<WebProjectEntity> = emptyList(),
    val activeProject: WebProjectEntity? = null,
    val messages: List<ChatMessageEntity> = emptyList(),
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val error: String? = null,
    val settings: AiSettings = AiSettings()
)

@OptIn(ExperimentalCoroutinesApi::class)
class AiAssistantViewModel(
    private val ai: AiService,
    private val chat: ChatRepository,
    private val projectsRepo: ProjectRepository,
    private val prefs: SecurePrefs,
    private val bus: CopilotBus
) : ViewModel() {

    private val activeProjectId = MutableStateFlow<String?>(null)
    private val streamState = MutableStateFlow(Triple("", false, null as String?))
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()
    private var streamJob: Job? = null

    private val conversationId get() = activeProjectId.value?.let { "project:$it" } ?: "general"

    private val projectsFlow = projectsRepo.observeAll()
    private val activeProjectFlow = activeProjectId.flatMapLatest { id -> if (id == null) flowOf(null) else projectsRepo.observe(id) }
    private val messagesFlow = activeProjectId.flatMapLatest { id -> chat.observe(id?.let { "project:$it" } ?: "general") }

    val state: StateFlow<AiUiState> = combine(
        projectsFlow, activeProjectFlow, messagesFlow, streamState, prefs.aiSettings
    ) { projects, active, messages, stream, settings ->
        AiUiState(projects, active, messages, stream.first, stream.second, stream.third, settings)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiUiState())

    init {
        consumePending()
    }

    fun consumePending() {
        bus.consume()?.let { req ->
            if (req.projectId != null) activeProjectId.value = req.projectId
            send(req.prompt, req.displayText)
        }
    }

    fun selectProject(id: String?) {
        if (streamState.value.second) stop()
        activeProjectId.value = id
    }

    fun send(prompt: String, displayText: String = prompt) {
        val text = prompt.trim()
        if (text.isEmpty() || streamState.value.second) return
        val convo = conversationId
        val projectId = activeProjectId.value
        streamJob = viewModelScope.launch {
            chat.add(ChatMessageEntity(conversationId = convo, projectId = projectId, role = ChatRole.USER, content = displayText.trim()))
            val project = projectId?.let { projectsRepo.get(it) }
            val history = chat.recent(convo, HISTORY_LIMIT)
                .map { AiMessage(it.role, it.content) }
                .toMutableList()
            // pesan terakhir: kirim prompt lengkap (bisa lebih panjang dari teks yang ditampilkan)
            if (history.isNotEmpty() && history.last().role == ChatRole.USER) history[history.lastIndex] = AiMessage(ChatRole.USER, text)
            streamState.value = Triple("", true, null)
            val sb = StringBuilder()
            try {
                ai.stream(PromptBuilder.system(project), history).collect { delta ->
                    sb.append(delta)
                    streamState.update { it.copy(first = sb.toString()) }
                }
                streamState.value = Triple("", false, null)
            } catch (e: CancellationException) {
                streamState.value = Triple("", false, null)
                if (sb.isNotEmpty()) withContext(NonCancellable) {
                    chat.add(ChatMessageEntity(conversationId = convo, projectId = projectId, role = ChatRole.ASSISTANT, content = sb.toString() + "\n\n_(dihentikan)_"))
                }
                throw e
            } catch (e: Exception) {
                streamState.value = Triple("", false, e.message ?: e.javaClass.simpleName)
            }
            if (sb.isNotEmpty()) chat.add(ChatMessageEntity(conversationId = convo, projectId = projectId, role = ChatRole.ASSISTANT, content = sb.toString()))
        }
    }

    fun stop() {
        streamJob?.cancel()
        streamJob = null
    }

    fun dismissError() = streamState.update { it.copy(third = null) }

    fun clearConversation() = viewModelScope.launch {
        stop()
        chat.clear(conversationId)
    }

    // ------------------------------------------------------------------ Aksi cepat
    fun quickCssJs(instruction: String) = send(PromptBuilder.cssJsInjector(instruction), "🎨 $instruction")
    fun quickAso() = send(PromptBuilder.asoGenerator(state.value.activeProject), "📝 Generate Deskripsi Play Store")
    fun quickPrivacy() = send(PromptBuilder.privacyPolicy(state.value.activeProject), "🔒 Buat Privacy Policy")
    fun quickBridge(question: String) = send(PromptBuilder.bridgeAdvisor(question), "🌉 $question")

    /** "Terapkan ke Proyek": mengisi field customCssCode / customJsOnPageFinished. */
    fun applyCode(block: CodeBlock, append: Boolean) = viewModelScope.launch {
        val project = state.value.activeProject
        if (project == null) {
            _events.emit("Pilih proyek aktif terlebih dahulu")
            return@launch
        }
        when (block.target) {
            CodeTarget.CSS -> {
                val v = if (append && project.customCssCode.isNotBlank()) project.customCssCode.trimEnd() + "\n\n" + block.code else block.code
                projectsRepo.updateCss(project.id, v)
                _events.emit("CSS diterapkan ke ${project.appName}")
            }
            CodeTarget.JS_FINISHED -> {
                val v = if (append && project.customJsOnPageFinished.isNotBlank()) project.customJsOnPageFinished.trimEnd() + "\n\n" + block.code else block.code
                projectsRepo.updateJsFinished(project.id, v)
                _events.emit("JavaScript diterapkan ke ${project.appName}")
            }
            null -> _events.emit("Blok ${block.language} tidak dapat diterapkan otomatis")
        }
    }

    // ------------------------------------------------------------------ Pengaturan
    fun settingsFor(provider: AiProvider) = prefs.aiSettingsFor(provider)

    fun saveSettings(s: AiSettings) {
        prefs.saveAi(s)
        viewModelScope.launch { _events.emit("Pengaturan AI disimpan") }
    }

    fun testSettings(s: AiSettings) = viewModelScope.launch {
        try {
            val r = ai.testConnection(s)
            _events.emit("Koneksi berhasil: \"$r\"")
        } catch (e: Exception) {
            _events.emit("Gagal: ${e.message}")
        }
    }

    companion object {
        private const val HISTORY_LIMIT = 20
    }
}
