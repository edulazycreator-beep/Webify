package com.webify.app.build

import com.webify.app.domain.BuildType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class BuildStage(val label: String, val weight: Int) {
    RESOURCES("Resources", 10),
    MANIFEST("Manifest", 5),
    AAPT2("AAPT2", 20),
    JAVA_GEN("Java Gen", 5),
    ECJ("ECJ", 25),
    D8("D8 DEX", 20),
    PACKAGE("Package & Sign", 15);

    /** Persentase kumulatif saat tahap ini dimulai. */
    val startPercent: Int get() = entries.takeWhile { it != this }.sumOf { it.weight }
    val endPercent: Int get() = startPercent + weight
}

enum class LogLevel { INFO, WARN, ERROR, SUCCESS, DEBUG }

data class LogLine(
    val level: LogLevel,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val stage: BuildStage? = null
) {
    fun format(): String = "[${TIME_FMT.get()!!.format(Date(timestamp))}] ${level.name.padEnd(7)} $message"

    companion object {
        private val TIME_FMT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue() = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        }
    }
}

/** Satu error kompilasi Java yang sudah di-parse dari keluaran ecj. */
data class CompileError(val file: String, val line: Int, val message: String, val isError: Boolean)

sealed class BuildResult {
    data class Success(
        val outputFile: File,
        val buildType: BuildType,
        val sizeBytes: Long,
        val durationMs: Long
    ) : BuildResult()

    data class Failure(
        val stage: BuildStage?,
        val summary: String,
        val durationMs: Long,
        val compileErrors: List<CompileError> = emptyList()
    ) : BuildResult()
}

sealed class BuildProgressEvent {
    data class StageStarted(val stage: BuildStage, val percent: Int) : BuildProgressEvent()
    data class StageCompleted(val stage: BuildStage, val percent: Int, val durationMs: Long) : BuildProgressEvent()
    data class Progress(val percent: Int) : BuildProgressEvent()
    data class Log(val line: LogLine) : BuildProgressEvent()
    data class Finished(val result: BuildResult) : BuildProgressEvent()
}

/** Exception terstruktur untuk kegagalan tahap pipeline. */
class BuildStageException(
    val stage: BuildStage,
    message: String,
    cause: Throwable? = null,
    val compileErrors: List<CompileError> = emptyList()
) : Exception(message, cause)

/** Sink log yang dipakai seluruh wrapper toolchain. */
fun interface BuildLogger {
    fun log(level: LogLevel, message: String)

    fun info(m: String) = log(LogLevel.INFO, m)
    fun warn(m: String) = log(LogLevel.WARN, m)
    fun error(m: String) = log(LogLevel.ERROR, m)
    fun success(m: String) = log(LogLevel.SUCCESS, m)
    fun debug(m: String) = log(LogLevel.DEBUG, m)
}

/** Struktur direktori workspace build per proyek. */
class Workspace(val root: File) {
    val res = File(root, "res")
    val src = File(root, "src")
    val assets = File(root, "assets")
    val compiledRes = File(root, "compiled_res")
    val classes = File(root, "classes")
    val dex = File(root, "dex")
    val bundleStaging = File(root, "bundle_staging")
    val output = File(root, "output")
    val manifest = File(root, "AndroidManifest.xml")

    val compiledResZip = File(compiledRes, "resources.zip")
    val resourcesAp = File(output, "resources.ap_")
    val protoResourcesAp = File(output, "proto_resources.ap_")

    fun prepareClean() {
        if (root.exists()) root.deleteRecursively()
        listOf(
            "res/values", "res/xml", "res/drawable",
            "res/mipmap-mdpi", "res/mipmap-hdpi", "res/mipmap-xhdpi", "res/mipmap-xxhdpi", "res/mipmap-xxxhdpi",
            "res/mipmap-anydpi-v26", "src", "assets", "compiled_res", "classes", "dex", "bundle_staging", "output"
        ).forEach { File(root, it).mkdirs() }
    }
}
