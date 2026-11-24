package com.webify.app.build

import android.content.Context
import com.webify.app.crypto.KeystoreManager
import com.webify.app.data.db.BuildRecordEntity
import com.webify.app.data.repo.ProjectRepository
import com.webify.app.domain.BuildStatus
import com.webify.app.domain.BuildType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BuildSession(
    val projectId: String,
    val projectName: String,
    val buildType: BuildType,
    val running: Boolean = true,
    val percent: Int = 0,
    val activeStage: BuildStage? = null,
    val completedStages: Set<BuildStage> = emptySet(),
    val failedStage: BuildStage? = null,
    val logs: List<LogLine> = emptyList(),
    val result: BuildResult? = null
)

/**
 * Pengelola build tingkat aplikasi: build tetap berjalan walau pengguna berpindah layar.
 * Hanya satu build aktif pada satu waktu.
 */
class BuildManager(
    context: Context,
    private val scope: CoroutineScope,
    private val projects: ProjectRepository,
    keystoreManager: KeystoreManager,
    toolchain: Toolchain
) {
    private val pipeline = WebifyBuildPipeline(context.applicationContext, toolchain, keystoreManager)
    private val notifier = BuildNotificationHelper(context.applicationContext)
    private val _session = MutableStateFlow<BuildSession?>(null)
    val session: StateFlow<BuildSession?> = _session.asStateFlow()
    private var job: Job? = null

    val isRunning: Boolean get() = _session.value?.running == true

    /** @return false jika build lain sedang berjalan. */
    fun start(projectId: String, type: BuildType): Boolean {
        if (isRunning) return false
        job = scope.launch {
            val project = projects.get(projectId)
            if (project == null) {
                _session.value = BuildSession(projectId, "?", type, running = false,
                    result = BuildResult.Failure(null, "Proyek tidak ditemukan", 0))
                return@launch
            }
            _session.value = BuildSession(project.id, project.appName, type)
            pipeline.build(project, type).collect { ev -> reduce(ev) }
            // persist riwayat build
            val s = _session.value ?: return@launch
            val r = s.result
            withContext(NonCancellable) {
                // A4: auto-increment versionCode setelah build rilis sukses
                if (r is BuildResult.Success && type.isRelease) {
                    val fresh = projects.get(project.id)
                    if (fresh != null && fresh.autoIncrementVersionCode) {
                        val nextName = bumpPatch(fresh.versionName)
                        projects.save(fresh.copy(versionCode = fresh.versionCode + 1, versionName = nextName))
                        appendLog(LogLine(LogLevel.INFO, "Auto-increment: versionCode ${fresh.versionCode} → ${fresh.versionCode + 1}, versionName ${fresh.versionName} → $nextName (untuk build berikutnya)"))
                    }
                }
                // A5: notifikasi bila Webify sedang di latar belakang
                val inBackground = withContext(Dispatchers.Main) {
                    !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                }
                if (inBackground) {
                    when (r) {
                        is BuildResult.Success -> notifier.notifySuccess(project.appName, r)
                        is BuildResult.Failure -> if (r.summary != "Dibatalkan") notifier.notifyFailure(project.appName, r)
                        null -> Unit
                    }
                }
                val log = s.logs.joinToString("\n") { it.format() }
                when (r) {
                    is BuildResult.Success -> projects.insertBuild(
                        BuildRecordEntity(
                            projectId = project.id, buildType = type, status = BuildStatus.SUCCESS,
                            outputFilePath = r.outputFile.absolutePath, fileSizeBytes = r.sizeBytes,
                            durationMs = r.durationMs, fullConsoleLog = log.takeLast(MAX_LOG_CHARS)
                        )
                    )
                    is BuildResult.Failure -> projects.insertBuild(
                        BuildRecordEntity(
                            projectId = project.id, buildType = type, status = BuildStatus.FAILED,
                            durationMs = r.durationMs, fullConsoleLog = log.takeLast(MAX_LOG_CHARS),
                            errorSummary = "[${r.stage?.label ?: "-"}] ${r.summary}".take(4000)
                        )
                    )
                    null -> Unit
                }
            }
        }
        return true
    }

    fun cancel() {
        job?.cancel()
        _session.update { it?.copy(running = false) }
    }

    fun dismiss() {
        if (!isRunning) _session.value = null
    }

    private fun reduce(ev: BuildProgressEvent) {
        _session.update { s ->
            s ?: return@update null
            when (ev) {
                is BuildProgressEvent.StageStarted -> s.copy(activeStage = ev.stage, percent = ev.percent)
                is BuildProgressEvent.StageCompleted -> s.copy(completedStages = s.completedStages + ev.stage, percent = ev.percent)
                is BuildProgressEvent.Progress -> s.copy(percent = maxOf(s.percent, ev.percent))
                is BuildProgressEvent.Log -> s.copy(logs = (s.logs + ev.line).takeLast(MAX_LOG_LINES))
                is BuildProgressEvent.Finished -> when (val r = ev.result) {
                    is BuildResult.Success -> s.copy(running = false, result = r, percent = 100, activeStage = null)
                    is BuildResult.Failure -> s.copy(running = false, result = r, failedStage = r.stage, activeStage = null)
                }
            }
        }
    }

    fun outputExists(path: String?) = path != null && File(path).exists()

    private fun appendLog(line: LogLine) {
        _session.update { it?.copy(logs = (it.logs + line).takeLast(MAX_LOG_LINES)) }
    }

    companion object {
        /** "1.0.9" → "1.0.10"; versi non-semver dibiarkan. */
        fun bumpPatch(v: String): String {
            val m = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(.*)$").find(v.trim()) ?: return v
            val (maj, min, patch, rest) = m.destructured
            return "$maj.$min.${patch.toInt() + 1}$rest"
        }

        private const val MAX_LOG_LINES = 3000
        private const val MAX_LOG_CHARS = 400_000
    }
}
