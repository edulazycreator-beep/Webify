package com.webify.app.build

import com.webify.app.data.db.WebProjectEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Tahap 3: Packaging resource memakai binary native aapt2 (libaapt2.so di nativeLibraryDir).
 */
class Aapt2Wrapper(private val aapt2: File, private val androidJar: File) {

    suspend fun compile(ws: Workspace, log: BuildLogger) {
        ensureBinary()
        run(
            listOf(aapt2.absolutePath, "compile", "--dir", ws.res.absolutePath, "-o", ws.compiledResZip.absolutePath),
            ws.root, log, "aapt2 compile"
        )
        if (!ws.compiledResZip.exists()) throw BuildStageException(BuildStage.AAPT2, "aapt2 compile tidak menghasilkan resources.zip")
        log.info("aapt2 compile → ${ws.compiledResZip.name} (${ws.compiledResZip.length() / 1024} KB)")
    }

    /**
     * @param protoFormat true untuk Android App Bundle (menghasilkan proto_resources.ap_ berisi
     * AndroidManifest.xml format protobuf & resources.pb).
     */
    /** Kompilasi folder res milik library AAR menjadi arsip flat (dipakai sebagai overlay saat link). */
    suspend fun compileDir(resDir: File, outZip: File, log: BuildLogger) {
        ensureBinary()
        outZip.parentFile?.mkdirs()
        run(listOf(aapt2.absolutePath, "compile", "--dir", resDir.absolutePath, "-o", outZip.absolutePath),
            resDir.parentFile ?: resDir, log, "aapt2 compile (${resDir.parentFile?.name})")
        if (!outZip.exists()) throw BuildStageException(BuildStage.DEPENDENCIES, "aapt2 compile gagal untuk ${resDir.path}")
    }

    suspend fun link(
        project: WebProjectEntity,
        ws: Workspace,
        protoFormat: Boolean,
        log: BuildLogger,
        deps: DependencySet = DependencySet.EMPTY
    ): File {
        ensureBinary()
        val out = if (protoFormat) ws.protoResourcesAp else ws.resourcesAp
        val args = mutableListOf(
            aapt2.absolutePath, "link",
            "-I", androidJar.absolutePath,
            "--manifest", ws.manifest.absolutePath,
            "--auto-add-overlay",
            "--java", ws.src.absolutePath,
            "--min-sdk-version", project.minSdk.toString(),
            "--target-sdk-version", project.targetSdk.toString(),
            "--version-code", project.versionCode.toString(),
            "--version-name", project.versionName,
            "--no-version-vectors",
            "-o", out.absolutePath
        )
        // Resource library sebagai overlay (dependensi terdalam dulu), resource aplikasi paling akhir agar selalu menang
        deps.compiledResZips.forEach { args += listOf("-R", it.absolutePath) }
        args += listOf("-R", ws.compiledResZip.absolutePath)
        val extra = deps.extraPackages.filter { it != project.packageName }
        if (extra.isNotEmpty()) {
            args += listOf("--extra-packages", extra.joinToString(":"))
            log.info("aapt2 link: ${deps.compiledResZips.size} overlay library, R.java untuk ${extra.size} paket tambahan")
        }
        if (protoFormat) args += "--proto-format"
        run(args, ws.root, log, if (protoFormat) "aapt2 link (proto/AAB)" else "aapt2 link (APK)")
        if (!out.exists()) throw BuildStageException(BuildStage.AAPT2, "aapt2 link tidak menghasilkan ${out.name}")
        log.info("aapt2 link → ${out.name} (${out.length() / 1024} KB), R.java dibuat")
        return out
    }

    private fun ensureBinary() {
        if (!aapt2.exists()) throw BuildStageException(
            BuildStage.AAPT2,
            "Binary aapt2 tidak ditemukan di ${aapt2.absolutePath}. Pastikan APK Webify dibangun setelah " +
                "scripts/prepare_toolchain.sh menempatkan libaapt2.so di jniLibs untuk ABI perangkat ini."
        )
        if (!androidJar.exists()) throw BuildStageException(BuildStage.AAPT2, "android.jar tidak ditemukan: ${androidJar.absolutePath}")
    }

    private suspend fun run(cmd: List<String>, workDir: File, log: BuildLogger, label: String) {
        log.debug("$ " + cmd.mapIndexed { i, s -> if (i == 0) "aapt2" else s.replace(workDir.absolutePath + "/", "") }.joinToString(" "))
        val process = try {
            ProcessBuilder(cmd).directory(workDir).redirectErrorStream(false).start()
        } catch (e: Exception) {
            throw BuildStageException(BuildStage.AAPT2, "Gagal mengeksekusi aapt2: ${e.message}", e)
        }
        val stderrLines = java.util.Collections.synchronizedList(mutableListOf<String>())
        val tOut = pump(process.inputStream) { log.info("[aapt2] $it") }
        val tErr = pump(process.errorStream) { line ->
            stderrLines += line
            if (line.contains("error", ignoreCase = true)) log.error("[aapt2] $line") else log.warn("[aapt2] $line")
        }
        try {
            while (!process.waitFor(200, TimeUnit.MILLISECONDS)) {
                currentCoroutineContext().ensureActive()
            }
        } catch (e: CancellationException) {
            process.destroyForcibly()
            throw e
        }
        tOut.join(2000); tErr.join(2000)
        val code = process.exitValue()
        if (code != 0) {
            val detail = stderrLines.filter { it.contains("error", true) }.takeLast(8).joinToString("\n")
                .ifEmpty { stderrLines.takeLast(8).joinToString("\n") }
            throw BuildStageException(BuildStage.AAPT2, "$label gagal (exit $code)\n$detail")
        }
    }

    private fun pump(stream: InputStream, onLine: (String) -> Unit): Thread = thread(isDaemon = true, name = "aapt2-pump") {
        try {
            stream.bufferedReader().use { r: BufferedReader ->
                while (true) {
                    val line = r.readLine() ?: break
                    if (line.isNotBlank()) onLine(line)
                }
            }
        } catch (_: Exception) { /* stream ditutup saat proses dihentikan */ }
    }
}
