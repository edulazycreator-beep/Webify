package com.webify.app

import android.app.Application
import android.util.Log
import com.webify.app.ai.AiService
import com.webify.app.ai.CopilotBus
import com.webify.app.build.BuildManager
import com.webify.app.build.Toolchain
import com.webify.app.crypto.KeystoreManager
import com.webify.app.data.db.WebifyDatabase
import com.webify.app.data.prefs.SecurePrefs
import com.webify.app.data.repo.ChatRepository
import com.webify.app.data.repo.KeystoreRepository
import com.webify.app.data.repo.ProjectRepository
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Kontainer dependensi manual (tanpa framework DI agar ringan). */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> Log.e("Webify", "Uncaught coroutine error", e) }
    )
    val database = WebifyDatabase.create(app)
    val securePrefs = SecurePrefs(app)
    val projectRepository = ProjectRepository(app, database.projectDao(), database.buildRecordDao())
    val keystoreRepository = KeystoreRepository(database.keystoreDao())
    val chatRepository = ChatRepository(database.chatDao())
    val keystoreManager = KeystoreManager(app, database.keystoreDao(), database.projectDao(), securePrefs)
    val toolchain = Toolchain(app)
    val aiService = AiService(securePrefs)
    val copilotBus = CopilotBus()
    /** Permintaan navigasi dari luar Compose (notifikasi build → konsol). */
    val navRequests = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val archiveManager = com.webify.app.data.repo.ProjectArchiveManager(app, database.projectDao())
    val buildManager = BuildManager(app, appScope, projectRepository, keystoreManager, toolchain)
}

class WebifyApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Auto-provisioning debug keystore & ekstraksi android.jar di latar belakang.
        container.appScope.launch(Dispatchers.IO) {
            runCatching { container.keystoreManager.ensureDebugKeystore() }
                .onFailure { Log.e("Webify", "Gagal membuat debug keystore", it) }
            runCatching { container.toolchain.prepare() }
                .onFailure { Log.w("Webify", "Toolchain belum siap: ${it.message}") }
        }
    }
}
