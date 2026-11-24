package com.webify.app.data.crash

import android.content.Context
import java.io.File

/** Laporan crash dari aplikasi hasil build (debug) yang dikirim balik ke Webify. */
data class CrashReport(val file: File, val packageName: String, val timestamp: Long, val text: String) {
    /** Baris pengecualian utama, mis. "java.lang.NullPointerException: …". */
    val headline: String
        get() = text.lines().firstOrNull { l ->
            val t = l.trim()
            t.isNotEmpty() && !t.startsWith("at ") && (t.contains("Exception") || t.contains("Error"))
        }?.trim()?.take(160) ?: "Crash"
}

class CrashReportStore(context: Context) {

    private val dir = File(context.filesDir, "crash_reports").apply { mkdirs() }

    fun save(packageName: String, text: String): File {
        val safePkg = packageName.replace(Regex("[^A-Za-z0-9._]"), "_").ifEmpty { "unknown" }
        val f = File(dir, "${System.currentTimeMillis()}__$safePkg.txt")
        f.writeText(text.take(MAX_CHARS))
        // simpan maksimal 50 laporan terbaru
        list().drop(MAX_REPORTS).forEach { it.file.delete() }
        return f
    }

    fun list(): List<CrashReport> = dir.listFiles { f -> f.extension == "txt" }.orEmpty()
        .mapNotNull { f ->
            val parts = f.nameWithoutExtension.split("__", limit = 2)
            val ts = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            CrashReport(f, parts.getOrElse(1) { "?" }, ts, runCatching { f.readText() }.getOrDefault(""))
        }
        .sortedByDescending { it.timestamp }

    fun delete(r: CrashReport) { r.file.delete() }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }

    companion object {
        private const val MAX_CHARS = 200_000
        private const val MAX_REPORTS = 50
    }
}
