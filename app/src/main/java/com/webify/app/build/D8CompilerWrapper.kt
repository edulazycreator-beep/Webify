package com.webify.app.build

import com.android.tools.r8.CompilationFailedException
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.Diagnostic
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.OutputMode
import java.io.File
import java.nio.file.Path

/**
 * Tahap 6: Konversi bytecode JVM (.class) menjadi DEX memakai D8 (in-process).
 */
class D8CompilerWrapper(private val androidJar: File) {

    /**
     * Pre-dex library saja ke [outDir] (dipakai ulang lintas build lewat [BuildCache]).
     */
    fun dexLibraries(jars: List<File>, outDir: File, minSdk: Int, release: Boolean, log: BuildLogger) {
        outDir.deleteRecursively(); outDir.mkdirs()
        val handler = handler(log, mutableListOf())
        log.info("d8: pre-dex ${jars.size} JAR library (sekali, lalu di-cache)…")
        try {
            D8.run(
                D8Command.builder(handler)
                    .addProgramFiles(jars.map { it.toPath() })
                    .addLibraryFiles(androidJar.toPath())
                    .setMinApiLevel(minSdk)
                    .setMode(if (release) CompilationMode.RELEASE else CompilationMode.DEBUG)
                    .setOutput(outDir.toPath(), OutputMode.DexIndexed)
                    .build()
            )
        } catch (e: CompilationFailedException) {
            throw BuildStageException(BuildStage.D8, "D8 (library) gagal: ${e.message}", e)
        } catch (e: Throwable) {
            throw BuildStageException(BuildStage.D8, "D8 (library) crash: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private fun handler(log: BuildLogger, errors: MutableList<String>) = object : DiagnosticsHandler {
        override fun error(d: Diagnostic) {
            val msg = describe(d)
            errors += msg
            log.error("[d8] $msg")
        }
        override fun warning(d: Diagnostic) { log.debug("[d8] ${describe(d)}") }
        override fun info(d: Diagnostic) { log.debug("[d8] ${describe(d)}") }
    }

    /**
     * @param preDexed file .dex hasil [dexLibraries]; bila diisi, [libraryJars] hanya dipakai sebagai classpath.
     */
    fun dex(
        ws: Workspace,
        minSdk: Int,
        release: Boolean,
        log: BuildLogger,
        libraryJars: List<File> = emptyList(),
        preDexed: List<File> = emptyList()
    ): List<File> {
        val classFiles: List<Path> = ws.classes.walkTopDown()
            .filter { it.isFile && it.extension == "class" }
            .map { it.toPath() }
            .toList()
        if (classFiles.isEmpty()) throw BuildStageException(BuildStage.D8, "Tidak ada file .class untuk di-dex")
        ws.dex.deleteRecursively(); ws.dex.mkdirs()

        val errors = mutableListOf<String>()
        val handler = handler(log, errors)

        val mode = if (release) CompilationMode.RELEASE else CompilationMode.DEBUG
        log.info("d8: ${classFiles.size} class" +
            (if (preDexed.isNotEmpty()) " + ${preDexed.size} DEX library (cache)" else if (libraryJars.isNotEmpty()) " + ${libraryJars.size} JAR library" else "") +
            " → DEX (mode=$mode, minApi=$minSdk, multi-dex otomatis)")
        try {
            val builder = D8Command.builder(handler).addProgramFiles(classFiles)
            if (preDexed.isNotEmpty()) {
                builder.addProgramFiles(preDexed.map { it.toPath() })
                builder.addClasspathFiles(libraryJars.map { it.toPath() })
            } else {
                builder.addProgramFiles(libraryJars.map { it.toPath() })
            }
            val command = builder
                .addLibraryFiles(androidJar.toPath())
                .setMinApiLevel(minSdk)
                .setMode(mode)
                .setOutput(ws.dex.toPath(), OutputMode.DexIndexed)
                .build()
            D8.run(command)
        } catch (e: CompilationFailedException) {
            throw BuildStageException(BuildStage.D8, "D8 gagal: " + (errors.lastOrNull() ?: e.message ?: "unknown"), e)
        } catch (e: Throwable) {
            throw BuildStageException(BuildStage.D8, "D8 crash: ${e.javaClass.simpleName}: ${e.message}", e)
        }

        val dexFiles = ws.dex.listFiles { f -> f.name.matches(Regex("classes\\d*\\.dex")) }?.sortedBy { it.name }.orEmpty()
        if (dexFiles.isEmpty()) throw BuildStageException(BuildStage.D8, "D8 tidak menghasilkan classes.dex")
        dexFiles.forEach { log.info("d8 → ${it.name} (${it.length() / 1024} KB)") }
        return dexFiles
    }

    private fun describe(d: Diagnostic): String {
        val origin = d.origin?.toString()?.substringAfterLast('/')?.takeIf { it.isNotBlank() && it != "Unknown" }
        return if (origin != null) "$origin: ${d.diagnosticMessage}" else d.diagnosticMessage
    }
}
