package com.webify.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.webify.app.AppContainer
import com.webify.app.WebifyApplication
import com.webify.app.build.BuildManager
import com.webify.app.crypto.KeystoreManager
import com.webify.app.crypto.ReleaseKeystoreRequest
import com.webify.app.data.db.BuildRecordEntity
import com.webify.app.data.db.KeystoreEntity
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.data.repo.KeystoreRepository
import com.webify.app.data.repo.ProjectArchiveManager
import com.webify.app.data.repo.ProjectRepository
import com.webify.app.domain.BuildType
import com.webify.app.domain.SourceType
import com.webify.app.domain.Validators
import com.webify.app.build.GoogleServicesParser
import com.webify.app.data.web.WebsiteMetadataExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = (LocalContext.current.applicationContext as WebifyApplication).container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

// =====================================================================================
// Dashboard
// =====================================================================================
data class DashboardState(
    val projects: List<WebProjectEntity> = emptyList(),
    val query: String = "",
    val totalProjects: Int = 0,
    val successfulBuilds: Int = 0,
    val keystoreCount: Int = 0,
    val loaded: Boolean = false
)

class DashboardViewModel(
    private val repo: ProjectRepository,
    keystores: KeystoreRepository,
    private val buildManager: BuildManager,
    private val archive: ProjectArchiveManager
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val state: StateFlow<DashboardState> = combine(
        repo.observeAll(), query, repo.observeSuccessCount(), keystores.observeAll()
    ) { all, q, success, ks ->
        val filtered = if (q.isBlank()) all else all.filter {
            it.appName.contains(q, true) || it.packageName.contains(q, true) || it.targetUrl.contains(q, true)
        }
        DashboardState(filtered, q, all.size, success, ks.size, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    fun setQuery(q: String) { query.value = q }

    fun create(
        name: String,
        url: String,
        pkg: String,
        template: (WebProjectEntity) -> WebProjectEntity = { it },
        onCreated: (String) -> Unit
    ) = viewModelScope.launch {
        try {
            if (repo.isPackageTaken(pkg, "")) { _messages.emit("Package name sudah dipakai proyek lain"); return@launch }
            val p = repo.create(name, url, pkg)
            val templated = template(p)
            if (templated != p) repo.save(templated)
            onCreated(p.id)
        } catch (e: Exception) {
            _messages.emit(e.message ?: "Gagal membuat proyek")
        }
    }

    fun clone(p: WebProjectEntity) = viewModelScope.launch {
        runCatching { repo.clone(p) }
            .onSuccess { _messages.emit("Proyek diduplikasi: ${it.appName}") }
            .onFailure { _messages.emit("Gagal duplikasi: ${it.message}") }
    }

    /** A3: ekspor .webify → callback dengan file untuk dibagikan. */
    fun export(p: WebProjectEntity, onReady: (java.io.File) -> Unit) = viewModelScope.launch {
        runCatching { archive.export(p) }
            .onSuccess(onReady)
            .onFailure { _messages.emit("Ekspor gagal: ${it.message}") }
    }

    /** A3: impor .webify sebagai proyek baru. */
    fun importBundle(uri: Uri, onImported: (String) -> Unit) = viewModelScope.launch {
        runCatching { archive.importBundle(uri) }
            .onSuccess { _messages.emit("Proyek diimpor: ${it.appName}"); onImported(it.id) }
            .onFailure { _messages.emit("Impor gagal: ${it.message}") }
    }

    fun delete(p: WebProjectEntity) = viewModelScope.launch {
        runCatching { repo.delete(p) }
            .onSuccess { _messages.emit("${p.appName} dihapus") }
            .onFailure { _messages.emit("Gagal menghapus: ${it.message}") }
    }

    /** @return true jika build dimulai. */
    fun quickBuild(p: WebProjectEntity): Boolean {
        val started = buildManager.start(p.id, BuildType.APK_DEBUG)
        if (!started) viewModelScope.launch { _messages.emit("Build lain sedang berjalan") }
        return started
    }

    companion object {
        fun suggestPackage(name: String): String {
            val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "").take(20).ifEmpty { "app" }
            val first = if (slug.first().isDigit()) "a$slug" else slug
            return "com.webify.$first"
        }
    }
}

// =====================================================================================
// Builder Studio
// =====================================================================================
data class StudioErrors(
    val packageName: String? = null,
    val url: String? = null,
    val versionName: String? = null,
    val colors: Map<String, String> = emptyMap(),
    val sdk: String? = null
) {
    val hasErrors get() = packageName != null || url != null || versionName != null || colors.isNotEmpty() || sdk != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModel(
    private val projectId: String,
    private val repo: ProjectRepository,
    keystoreRepo: KeystoreRepository,
    private val buildManager: BuildManager,
    private val appScope: CoroutineScope
) : ViewModel() {

    private val _draft = MutableStateFlow<WebProjectEntity?>(null)
    val draft: StateFlow<WebProjectEntity?> = _draft.asStateFlow()
    private val _errors = MutableStateFlow(StudioErrors())
    val errors: StateFlow<StudioErrors> = _errors.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val keystores: StateFlow<List<KeystoreEntity>> = keystoreRepo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val builds: StateFlow<List<BuildRecordEntity>> = repo.observeBuilds(projectId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var saveJob: Job? = null
    @Volatile private var dirty = false

    init { reload() }

    /** Muat ulang dari DB (mis. setelah AI menerapkan CSS/JS) bila tidak ada perubahan tertunda. */
    fun reload() = viewModelScope.launch {
        if (dirty) return@launch
        val p = repo.observe(projectId).first()
        _draft.value = p
        p?.let { validate(it) }
    }

    fun update(transform: (WebProjectEntity) -> WebProjectEntity) {
        val cur = _draft.value ?: return
        val next = transform(cur)
        _draft.value = next
        validate(next)
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(450)
            persist()
        }
    }

    private suspend fun persist() {
        val p = _draft.value ?: return
        _saving.value = true
        try {
            if (Validators.packageNameError(p.packageName) == null && repo.isPackageTaken(p.packageName, p.id)) {
                _errors.value = _errors.value.copy(packageName = "Package name sudah dipakai proyek lain")
            }
            repo.save(p)
            dirty = false
        } catch (e: Exception) {
            _messages.emit("Gagal menyimpan: ${e.message}")
        } finally {
            _saving.value = false
        }
    }

    suspend fun flush() {
        saveJob?.cancel()
        if (dirty) persist()
    }

    private fun validate(p: WebProjectEntity) {
        val colorFields = mapOf(
            "adaptiveBgColor" to p.adaptiveBgColor, "primaryColorHex" to p.primaryColorHex,
            "statusBarColorHex" to p.statusBarColorHex, "navigationBarColorHex" to p.navigationBarColorHex,
            "splashBgColorHex" to p.splashBgColorHex, "progressBarColorHex" to p.progressBarColorHex
        ).filterValues { !Validators.isHexColor(it.trim()) }.mapValues { "Format #RRGGBB" }
        _errors.value = StudioErrors(
            packageName = Validators.packageNameError(p.packageName),
            url = if (p.sourceType == SourceType.URL) Validators.urlError(p.targetUrl) else
                if (p.localZipPath == null) "Pilih file ZIP proyek web" else null,
            versionName = if (p.versionName.isBlank()) "Wajib diisi" else null,
            colors = colorFields,
            sdk = when {
                p.admobEnabled && !p.admobAppId.trim().matches(Regex("^ca-app-pub-\\d+~\\d+$")) -> "AdMob App ID tidak valid"
                p.fcmEnabled && p.googleServicesJsonPath == null -> "google-services.json belum diunggah"
                else -> null
            }
        )
    }

    fun setIcon(uri: Uri) = viewModelScope.launch {
        runCatching { repo.importIcon(projectId, uri) }
            .onSuccess { path -> update { it.copy(iconUri = path) } }
            .onFailure { _messages.emit("Ikon gagal diimpor: ${it.message}") }
    }

    fun setSiteZip(uri: Uri) = viewModelScope.launch {
        runCatching { repo.importSiteZip(projectId, uri) }
            .onSuccess { path -> update { it.copy(localZipPath = path, sourceType = SourceType.LOCAL_HTML_ZIP) }; _messages.emit("ZIP proyek web diimpor") }
            .onFailure { _messages.emit("ZIP tidak valid: ${it.message}") }
    }

    // ------------------------------------------------------------------ A1: auto-fill dari URL
    private val _autofilling = MutableStateFlow(false)
    val autofilling: StateFlow<Boolean> = _autofilling.asStateFlow()

    fun autofill(canChangePackage: Boolean) = viewModelScope.launch {
        val p = _draft.value ?: return@launch
        val url = p.targetUrl.trim()
        Validators.urlError(url)?.let { _messages.emit(it); return@launch }
        _autofilling.value = true
        try {
            val iconFile = repo.projectAssetFile(projectId, "icon_auto_${System.currentTimeMillis()}")
            val meta = WebsiteMetadataExtractor().extract(url, iconFile)
            val filled = mutableListOf<String>()
            update { cur ->
                var n = cur
                meta.displayName?.let { n = n.copy(appName = it); filled += "nama" }
                meta.themeColor?.let { n = n.copy(primaryColorHex = it, statusBarColorHex = it, progressBarColorHex = it); filled += "warna tema" }
                (meta.backgroundColor ?: meta.themeColor)?.let { n = n.copy(splashBgColorHex = it) }
                meta.backgroundColor?.let { n = n.copy(adaptiveBgColor = it) }
                meta.iconFile?.let { n = n.copy(iconUri = it.absolutePath); filled += "ikon" }
                if (canChangePackage && meta.displayName != null) {
                    val suggestion = WebsiteMetadataExtractor.suggestPackage(meta.displayName!!)
                    if (Validators.packageNameError(suggestion) == null) { n = n.copy(packageName = suggestion); filled += "package" }
                }
                n
            }
            _messages.emit(
                if (filled.isEmpty()) "Tidak ada metadata yang bisa diambil dari website"
                else "Terisi otomatis: ${filled.joinToString()}" + if (meta.manifestFound) " (dari manifest PWA)" else ""
            )
        } catch (e: Exception) {
            _messages.emit("Gagal mengambil info website: ${e.message}")
        } finally {
            _autofilling.value = false
        }
    }

    // ------------------------------------------------------------------ Plugin SDK
    fun setGoogleServices(uri: Uri) = viewModelScope.launch {
        val p = _draft.value ?: return@launch
        try {
            val f = repo.importAsset(projectId, uri, "google-services.json")
            val cfg = GoogleServicesParser.parse(f.readText(), p.packageName)
            update { it.copy(googleServicesJsonPath = f.absolutePath, fcmEnabled = true) }
            _messages.emit("google-services.json valid • project ${cfg.projectId}")
        } catch (e: Exception) {
            _messages.emit("google-services.json ditolak: ${e.message}")
        }
    }

    fun addLocalLibrary(uri: Uri) = viewModelScope.launch {
        val name = repo.displayName(uri) ?: "library_${System.currentTimeMillis()}.aar"
        if (!name.endsWith(".aar", true) && !name.endsWith(".jar", true)) {
            _messages.emit("Hanya file .aar atau .jar yang didukung")
            return@launch
        }
        try {
            val f = repo.importAsset(projectId, uri, "lib_" + name)
            java.util.zip.ZipFile(f).close() // validasi arsip
            update { cur ->
                val list = cur.localLibraries.lines().filter { it.isNotBlank() && it != f.absolutePath } + f.absolutePath
                cur.copy(localLibraries = list.joinToString("\n"))
            }
            _messages.emit("Library ditambahkan: $name")
        } catch (e: Exception) {
            _messages.emit("File library tidak valid: ${e.message}")
        }
    }

    fun removeLocalLibrary(path: String) {
        update { cur -> cur.copy(localLibraries = cur.localLibraries.lines().filter { it.isNotBlank() && it != path }.joinToString("\n")) }
        runCatching { java.io.File(path).delete() }
    }

    /** Simpan lalu mulai build. @return true bila build dimulai. */
    fun startBuild(type: BuildType, onStarted: () -> Unit) = viewModelScope.launch {
        flush()
        val e = _errors.value
        if (e.hasErrors) {
            _messages.emit("Perbaiki isian yang ditandai merah sebelum build")
            return@launch
        }
        if (buildManager.start(projectId, type)) onStarted() else _messages.emit("Build lain sedang berjalan")
    }

    override fun onCleared() {
        // simpan perubahan terakhir secara sinkron di scope aplikasi bila masih dirty
        val p = _draft.value
        if (dirty && p != null) appScope.launch { runCatching { repo.save(p) } }
        super.onCleared()
    }
}

// =====================================================================================
// Keystore Vault
// =====================================================================================
class VaultViewModel(private val manager: KeystoreManager, repo: KeystoreRepository) : ViewModel() {
    val keystores: StateFlow<List<KeystoreEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch { runCatching { manager.ensureDebugKeystore() } }
    }

    private fun work(success: String? = null, onDone: () -> Unit = {}, block: suspend () -> Unit) = viewModelScope.launch {
        _busy.value = true
        try {
            block()
            success?.let { _messages.emit(it) }
            onDone()
        } catch (e: Exception) {
            _messages.emit(e.message ?: "Terjadi kesalahan")
        } finally {
            _busy.value = false
        }
    }

    fun generate(req: ReleaseKeystoreRequest, onDone: () -> Unit) =
        work("Keystore '${req.name}' berhasil dibuat", onDone) { manager.generateReleaseKeystore(req) }

    fun importKeystore(uri: Uri, name: String, storePw: String, alias: String, keyPw: String, onDone: () -> Unit) =
        work("Keystore diimpor", onDone) { manager.importKeystore(uri, name, storePw, alias, keyPw) }

    suspend fun aliases(uri: Uri, storePw: String): Result<List<String>> = runCatching { manager.listAliases(uri, storePw) }

    fun export(entity: KeystoreEntity, target: Uri) = work("Keystore diekspor") { manager.exportKeystore(entity, target) }

    fun delete(entity: KeystoreEntity) = work("Keystore dihapus") { manager.delete(entity) }
}
