package com.webify.app.ui.copilot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webify.app.AppContainer
import com.webify.app.ai.AiAssistantViewModel
import com.webify.app.ai.AiUiState
import com.webify.app.ai.CodeBlock
import com.webify.app.ai.MarkdownParser
import com.webify.app.ai.MessagePart
import com.webify.app.data.prefs.AiProvider
import com.webify.app.data.prefs.AiSettings
import com.webify.app.domain.ChatRole
import com.webify.app.ui.appViewModel
import com.webify.app.ui.components.LabeledField
import com.webify.app.ui.dashboard.ProjectIcon
import com.webify.app.ui.theme.TerminalColors
import com.webify.app.ui.theme.WebifyTheme
import com.webify.app.util.FileActions

private enum class InputMode(val placeholder: String) {
    CHAT("Tanyakan apa saja tentang proyek Anda…"),
    HIDE_ELEMENT("Elemen apa yang ingin disembunyikan? (mis. banner cookie, header, tombol chat)"),
    CSS_JS("Jelaskan perubahan tampilan/perilaku website…"),
    BRIDGE("Fitur native apa yang ingin dipanggil dari website?")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopilotScreen(container: AppContainer) {
    val vm = appViewModel { AiAssistantViewModel(it.aiService, it.chatRepository, it.projectRepository, it.securePrefs, it.copilotBus) }
    val state by vm.state.collectAsStateWithLifecycle()
    val pending by container.copilotBus.pending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var input by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(InputMode.CHAT) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(pending) { if (pending != null) vm.consumePending() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar("AI: $it"); vm.dismissError() } }
    val itemCount = state.messages.size + if (state.isStreaming) 1 else 0
    LaunchedEffect(itemCount, state.streamingText.length / 200) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1 + 1)
    }

    fun submit() {
        val t = input.trim()
        if (t.isEmpty()) return
        when (mode) {
            InputMode.CHAT -> vm.send(t)
            InputMode.HIDE_ELEMENT -> vm.quickCssJs("Sembunyikan elemen berikut dari website menggunakan CSS (dan JS bila perlu untuk elemen dinamis): $t")
            InputMode.CSS_JS -> vm.quickCssJs(t)
            InputMode.BRIDGE -> vm.quickBridge(t)
        }
        input = ""
        mode = InputMode.CHAT
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(
                                Brush.linearGradient(listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary))
                            ), contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onTertiary) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("AI Co-Pilot", style = MaterialTheme.typography.titleMedium)
                            Text("${state.settings.provider.label} • ${state.settings.model}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.clearConversation() }) { Icon(Icons.Rounded.DeleteSweep, "Hapus percakapan") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, "Pengaturan AI") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).imePadding()) {
            ProjectSelector(state, onSelect = vm::selectProject)
            if (!state.settings.isConfigured) {
                Surface(
                    onClick = { showSettings = true },
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(10.dp))
                        Text("Masukkan API key Gemini/OpenAI atau endpoint kustom untuk mengaktifkan Co-Pilot.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (state.messages.isEmpty() && !state.isStreaming) {
                    item { Welcome() }
                }
                items(state.messages, key = { it.id }) { m ->
                    if (m.role == ChatRole.USER) UserBubble(m.content)
                    else AssistantMessage(m.content, canApply = state.activeProject != null, onApply = { b, append -> vm.applyCode(b, append) })
                }
                if (state.isStreaming) {
                    item(key = "streaming") {
                        AssistantMessage(state.streamingText.ifEmpty { "▍" }, canApply = false, onApply = { _, _ -> }, streaming = true)
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
            PromptChips(
                onHide = { mode = InputMode.HIDE_ELEMENT },
                onCss = { mode = InputMode.CSS_JS },
                onAso = { vm.quickAso() },
                onPrivacy = { vm.quickPrivacy() },
                onBridge = { mode = InputMode.BRIDGE },
                enabled = !state.isStreaming
            )
            InputBar(
                value = input, onChange = { input = it }, mode = mode,
                onClearMode = { mode = InputMode.CHAT },
                streaming = state.isStreaming,
                onSend = ::submit, onStop = vm::stop
            )
        }
    }

    if (showSettings) AiSettingsSheet(
        initial = state.settings,
        settingsFor = vm::settingsFor,
        onDismiss = { showSettings = false },
        onSave = { vm.saveSettings(it); showSettings = false },
        onTest = { vm.testSettings(it) }
    )
}

@Composable
private fun ProjectSelector(state: AiUiState, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        FilterChip(
            selected = state.activeProject != null,
            onClick = { open = true },
            label = { Text(state.activeProject?.let { "Konteks: ${it.appName}" } ?: "Tanpa konteks proyek") },
            leadingIcon = { state.activeProject?.let { ProjectIcon(it, 20) } }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Tanpa konteks proyek") }, onClick = { onSelect(null); open = false })
            state.projects.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.appName) },
                    leadingIcon = { ProjectIcon(p, 24) },
                    trailingIcon = { if (p.id == state.activeProject?.id) Icon(Icons.Rounded.Check, null) },
                    onClick = { onSelect(p.id); open = false }
                )
            }
        }
    }
}

@Composable
private fun Welcome() {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.tertiary) }
        Text("Co-Pilot siap membantu", style = MaterialTheme.typography.titleLarge)
        Text(
            "Buat CSS/JS injeksi, diagnosis error build, materi listing Play Store, kebijakan privasi, dan integrasi WebifyBridge.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
            modifier = Modifier.widthIn(max = 520.dp)
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun AssistantMessage(text: String, canApply: Boolean, onApply: (CodeBlock, Boolean) -> Unit, streaming: Boolean = false) {
    val parts = remember(text) { MarkdownParser.split(text) }
    Row(Modifier.fillMaxWidth()) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiary) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            parts.forEach { part ->
                when (part) {
                    is MessagePart.Text -> SelectionContainer { MarkdownText(part.markdown) }
                    is MessagePart.Code -> CodeCard(part.block, canApply && !streaming, onApply)
                }
            }
        }
    }
}

@Composable
private fun MarkdownText(md: String) {
    val codeBg = MaterialTheme.colorScheme.surfaceContainer
    val primary = MaterialTheme.colorScheme.primary
    val mono = WebifyTheme.fonts.mono
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        md.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(2.dp))
                line.startsWith("### ") -> Text(inline(line.removePrefix("### "), codeBg, primary, mono), style = MaterialTheme.typography.titleSmall)
                line.startsWith("## ") -> Text(inline(line.removePrefix("## "), codeBg, primary, mono), style = MaterialTheme.typography.titleMedium)
                line.startsWith("# ") -> Text(inline(line.removePrefix("# "), codeBg, primary, mono), style = MaterialTheme.typography.titleLarge)
                line.trimStart().matches(Regex("^[-*•]\\s+.*")) -> {
                    val indent = (line.length - line.trimStart().length) / 2
                    Row(Modifier.padding(start = (indent * 12).dp)) {
                        Text("•  ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                        Text(inline(line.trimStart().drop(1).trimStart(), codeBg, primary, mono), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                line.trimStart().matches(Regex("^\\d+[.)]\\s+.*")) -> {
                    val num = line.trimStart().takeWhile { it.isDigit() }
                    Row {
                        Text("$num. ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
                        Text(inline(line.trimStart().dropWhile { it.isDigit() }.drop(1).trimStart(), codeBg, primary, mono), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                line.startsWith("> ") -> Text(inline(line.removePrefix("> "), codeBg, primary, mono), style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                line.matches(Regex("^-{3,}$")) -> Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                else -> Text(inline(line, codeBg, primary, mono), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Markdown inline: **tebal**, *miring* / _miring_, `kode`, [tautan](url). */
private fun inline(text: String, codeBg: androidx.compose.ui.graphics.Color, link: androidx.compose.ui.graphics.Color, mono: androidx.compose.ui.text.font.FontFamily): AnnotatedString =
    buildAnnotatedString {
        val pattern = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\(([^)]+)\)|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])|(?<!\w)_(?!\s)(.+?)(?<!\s)_(?!\w)""")
        var idx = 0
        for (m in pattern.findAll(text)) {
            append(text.substring(idx, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[1]) }
                g[2].isNotEmpty() -> withStyle(SpanStyle(fontFamily = mono, background = codeBg)) { append(" ${g[2]} ") }
                g[3].isNotEmpty() -> withStyle(SpanStyle(color = link, textDecoration = TextDecoration.Underline)) { append(g[3]) }
                g[5].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[5]) }
                g[6].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]) }
                else -> append(m.value)
            }
            idx = m.range.last + 1
        }
        if (idx < text.length) append(text.substring(idx))
    }

@Composable
private fun CodeCard(block: CodeBlock, canApply: Boolean, onApply: (CodeBlock, Boolean) -> Unit) {
    val ctx = LocalContext.current
    Surface(color = TerminalColors.Background, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, TerminalColors.Border), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().background(TerminalColors.Surface).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(block.language.uppercase(), style = WebifyTheme.mono.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize, fontWeight = FontWeight.Bold), color = TerminalColors.Info, modifier = Modifier.weight(1f))
                IconButton(onClick = { FileActions.copy(ctx, "Kode", block.code) }) { Icon(Icons.Rounded.ContentCopy, "Salin", Modifier.size(18.dp), tint = TerminalColors.Timestamp) }
            }
            SelectionContainer {
                Text(
                    block.code,
                    style = WebifyTheme.mono.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                    color = TerminalColors.Text,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)
                )
            }
            val target = block.target
            if (target != null && canApply) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onApply(block, false) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Terapkan ke Proyek") }
                    TextButton(onClick = { onApply(block, true) }) { Text("Tambahkan", color = TerminalColors.Info) }
                }
                Text("→ ${target.label}", style = MaterialTheme.typography.labelSmall, color = TerminalColors.Timestamp, modifier = Modifier.padding(start = 12.dp, bottom = 10.dp))
            }
        }
    }
}

@Composable
private fun PromptChips(onHide: () -> Unit, onCss: () -> Unit, onAso: () -> Unit, onPrivacy: () -> Unit, onBridge: () -> Unit, enabled: Boolean) {
    val colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.tertiary)
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(onClick = onHide, enabled = enabled, label = { Text("Buat JS Sembunyikan Elemen") }, leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null, Modifier.size(18.dp)) }, colors = colors)
        AssistChip(onClick = onCss, enabled = enabled, label = { Text("Smart CSS & JS") }, leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)) }, colors = colors)
        AssistChip(onClick = onAso, enabled = enabled, label = { Text("Generate Deskripsi Play Store") }, leadingIcon = { Icon(Icons.Rounded.Storefront, null, Modifier.size(18.dp)) }, colors = colors)
        AssistChip(onClick = onPrivacy, enabled = enabled, label = { Text("Buat Privacy Policy") }, leadingIcon = { Icon(Icons.Rounded.Policy, null, Modifier.size(18.dp)) }, colors = colors)
        AssistChip(onClick = onBridge, enabled = enabled, label = { Text("Bridge Advisor") }, leadingIcon = { Icon(Icons.Rounded.Hub, null, Modifier.size(18.dp)) }, colors = colors)
    }
}

@Composable
private fun InputBar(
    value: String, onChange: (String) -> Unit, mode: InputMode, onClearMode: () -> Unit,
    streaming: Boolean, onSend: () -> Unit, onStop: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        if (mode != InputMode.CHAT) {
            FilterChip(selected = true, onClick = onClearMode, label = { Text(when (mode) {
                InputMode.HIDE_ELEMENT -> "Mode: Sembunyikan elemen ✕"
                InputMode.CSS_JS -> "Mode: Smart CSS & JS ✕"
                InputMode.BRIDGE -> "Mode: Bridge Advisor ✕"
                InputMode.CHAT -> ""
            }) })
        }
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = value, onValueChange = onChange,
                placeholder = { Text(mode.placeholder) },
                modifier = Modifier.weight(1f),
                maxLines = 5,
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            if (streaming) {
                FilledIconButton(onClick = onStop, modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Rounded.Stop, "Hentikan")
                }
            } else {
                FilledIconButton(onClick = onSend, enabled = value.isNotBlank(), modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)) {
                    Icon(Icons.AutoMirrored.Rounded.Send, "Kirim")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSettingsSheet(
    initial: AiSettings,
    settingsFor: (AiProvider) -> AiSettings,
    onDismiss: () -> Unit,
    onSave: (AiSettings) -> Unit,
    onTest: (AiSettings) -> Unit
) {
    var s by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Pengaturan AI", style = MaterialTheme.typography.headlineSmall)
            Text("API key disimpan terenkripsi (EncryptedSharedPreferences + Android Keystore).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { p ->
                    FilterChip(selected = s.provider == p, onClick = { s = settingsFor(p) }, label = { Text(p.label) })
                }
            }
            androidx.compose.material3.OutlinedTextField(
                value = s.apiKey, onValueChange = { s = s.copy(apiKey = it) },
                label = { Text(if (s.provider == AiProvider.CUSTOM) "API key (opsional)" else "API key") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
            )
            LabeledField("Base URL", s.baseUrl, { s = s.copy(baseUrl = it.trim()) }, mono = true)
            LabeledField("Model", s.model, { s = s.copy(model = it.trim()) }, mono = true,
                supporting = when (s.provider) {
                    AiProvider.GEMINI -> "Nama model Gemini yang aktif di akun Anda"
                    AiProvider.OPENAI -> "Nama model chat OpenAI"
                    AiProvider.CUSTOM -> "Endpoint harus kompatibel /chat/completions (Ollama, LM Studio, OpenRouter, dll.)"
                })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onTest(s) }, modifier = Modifier.weight(1f)) { Text("Uji koneksi") }
                Button(onClick = { onSave(s) }, modifier = Modifier.weight(1f)) { Text("Simpan") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
