package com.webify.app.build

import org.eclipse.jdt.internal.compiler.batch.Main
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.io.Writer

/**
 * Tahap 5: Kompilasi Java in-process memakai Eclipse Compiler for Java (ecj batch Main).
 */
class EcjCompilerWrapper(private val androidJar: File) {

    fun compile(ws: Workspace, log: BuildLogger, libraryJars: List<File> = emptyList()): Int {
        val sources = ws.src.walkTopDown().filter { it.isFile && it.extension == "java" }.map { it.absolutePath }.toList()
        if (sources.isEmpty()) throw BuildStageException(BuildStage.ECJ, "Tidak ada file .java untuk dikompilasi")
        ws.classes.deleteRecursively(); ws.classes.mkdirs()

        val args = mutableListOf(
            "-1.8", "-source", "1.8", "-target", "1.8",
            "-nowarn",
            "-proc:none",
            "-encoding", "UTF-8",
            "-g:source,lines",
            "-d", ws.classes.absolutePath,
            "-bootclasspath", androidJar.absolutePath,
            "-classpath", (listOf(androidJar) + libraryJars).joinToString(File.pathSeparator) { it.absolutePath },
            "-sourcepath", ws.src.absolutePath
        )
        args += sources
        log.info("ecj: mengompilasi ${sources.size} file Java (target 1.8)" + if (libraryJars.isNotEmpty()) " • classpath +${libraryJars.size} JAR" else "")

        val outBuffer = StringWriter()
        val errBuffer = StringWriter()
        val outWriter = PrintWriter(LineTee(outBuffer) { if (it.isNotBlank()) log.debug("[ecj] $it") }, true)
        val errWriter = PrintWriter(errBuffer, true)

        val ok = try {
            val main = Main(outWriter, errWriter, false, null, null)
            main.compile(args.toTypedArray())
        } catch (e: Throwable) {
            throw BuildStageException(BuildStage.ECJ, "ecj crash: ${e.javaClass.simpleName}: ${e.message}", e)
        } finally {
            outWriter.flush(); errWriter.flush()
        }

        val errText = errBuffer.toString()
        val errors = parseProblems(errText)
        errors.filter { !it.isError }.forEach { log.warn("${it.file}:${it.line} ${it.message}") }
        if (!ok || errors.any { it.isError }) {
            errors.filter { it.isError }.forEach { log.error("${it.file}:${it.line} → ${it.message}") }
            if (errors.isEmpty() && errText.isNotBlank()) errText.lines().filter { it.isNotBlank() }.take(30).forEach { log.error("[ecj] $it") }
            val n = errors.count { it.isError }
            throw BuildStageException(BuildStage.ECJ, "Kompilasi Java gagal: $n error", compileErrors = errors)
        }
        val classCount = ws.classes.walkTopDown().count { it.isFile && it.extension == "class" }
        log.info("ecj selesai → $classCount file .class")
        return classCount
    }

    /**
     * Parser format masalah ecj:
     * ----------
     * 1. ERROR in /path/MainActivity.java (at line 42)
     *     kode
     *     ^^^^
     * Pesan error
     * ----------
     */
    private fun parseProblems(text: String): List<CompileError> {
        val result = mutableListOf<CompileError>()
        val header = Regex("""^\d+\.\s+(ERROR|WARNING|INFO)\s+in\s+(.+?)\s+\(at line (\d+)\)\s*$""")
        val blocks = text.split(Regex("(?m)^-{10}\\s*$"))
        for (block in blocks) {
            val lines = block.lines().filter { it.isNotEmpty() }
            val idx = lines.indexOfFirst { header.matches(it.trim()) }
            if (idx < 0) continue
            val m = header.matchEntire(lines[idx].trim()) ?: continue
            val (kind, file, line) = m.destructured
            // pesan = baris terakhir yang bukan kode/penanda caret
            val msg = lines.drop(idx + 1).lastOrNull { !it.trim().matches(Regex("^\\^+$")) }?.trim() ?: "Unknown"
            result += CompileError(File(file).name, line.toIntOrNull() ?: 0, msg, kind == "ERROR")
        }
        return result
    }

    /** Writer yang meneruskan ke buffer sambil memancarkan per-baris ke log. */
    private class LineTee(private val target: Writer, private val onLine: (String) -> Unit) : Writer() {
        private val sb = StringBuilder()
        override fun write(cbuf: CharArray, off: Int, len: Int) {
            target.write(cbuf, off, len)
            for (i in off until off + len) {
                val c = cbuf[i]
                if (c == '\n') { onLine(sb.toString().trimEnd('\r')); sb.setLength(0) } else sb.append(c)
            }
        }
        override fun flush() { target.flush() }
        override fun close() { if (sb.isNotEmpty()) onLine(sb.toString()); sb.setLength(0); target.close() }
    }
}
