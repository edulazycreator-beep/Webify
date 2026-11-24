package com.webify.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Mesin regex Android (ICU) lebih ketat daripada java.util.regex di JVM:
 * '{' / '}' literal WAJIB di-escape di luar kelas karakter. Unit test JVM tidak menangkap ini,
 * jadi lint ini memindai semua literal regex di kode Kotlin & template Java secara statis.
 */
class IcuRegexLintTest {

    /** @return pesan kesalahan, atau null bila aman untuk ICU. */
    private fun icuProblem(regex: String): String? {
        var i = 0
        var inClass = false
        while (i < regex.length) {
            val c = regex[i]
            when {
                c == '\\' -> i++ // lewati karakter yang di-escape
                inClass -> if (c == ']') inClass = false
                c == '[' -> {
                    inClass = true
                    // ']' tepat setelah '[' atau '[^' adalah literal
                    if (regex.getOrNull(i + 1) == '^') i++
                    if (regex.getOrNull(i + 1) == ']') i++
                }
                c == '{' -> {
                    val m = Regex("^\\{\\d+(,\\d*)?\\}").find(regex.substring(i))
                        ?: return "'{' literal tidak di-escape di indeks $i"
                    i += m.value.length - 1
                }
                c == '}' -> return "'}' literal tidak di-escape di indeks $i"
            }
            i++
        }
        return null
    }

    /** Ambil isi literal string (bukan raw string) mulai dari tanda kutip pembuka di [start]. */
    private fun readLiteral(src: String, start: Int): String? {
        if (src.startsWith("\"\"\"", start)) return null
        val sb = StringBuilder()
        var i = start + 1
        while (i < src.length) {
            val c = src[i]
            if (c == '\\' && i + 1 < src.length) {
                when (val n = src[i + 1]) {
                    '\\' -> sb.append('\\')
                    '"' -> sb.append('"')
                    '$' -> sb.append('$')
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    '\'' -> sb.append('\'')
                    else -> sb.append('\\').append(n)
                }
                i += 2
                continue
            }
            if (c == '"') return sb.toString()
            if (c == '\n') return null
            sb.append(c)
            i++
        }
        return null
    }

    private fun scan(file: File, markers: List<String>): List<String> {
        val src = file.readText()
        val out = ArrayList<String>()
        for (marker in markers) {
            var idx = src.indexOf(marker)
            while (idx >= 0) {
                val quote = idx + marker.length - 1
                readLiteral(src, quote)?.let { lit ->
                    icuProblem(lit)?.let { problem ->
                        val line = src.substring(0, idx).count { it == '\n' } + 1
                        out += "${file.path}:$line  \"$lit\"  → $problem"
                    }
                }
                idx = src.indexOf(marker, idx + 1)
            }
        }
        return out
    }

    @Test
    fun checkerDetectsKnownBadPattern() {
        assertNotNull(icuProblem("\\$\\{([^}]+)}"))
        assertNull(icuProblem("\\$\\{([^}]+)\\}"))
        assertNull(icuProblem("^#([0-9a-f]{6})([0-9a-f]{2})?$"))
        assertNull(icuProblem("[{}]+"))
        assertNotNull(icuProblem("a{b}"))
    }

    @Test
    fun allRegexLiteralsAreIcuSafe() {
        val root = listOf(File("src/main"), File("app/src/main")).firstOrNull { it.isDirectory }
            ?: error("Folder src/main tidak ditemukan dari ${File(".").absolutePath}")
        val kotlinMarkers = listOf("Regex(\"", "Pattern.compile(\"")
        val javaMarkers = listOf("Pattern.compile(\"", ".matches(\"", ".replaceAll(\"", ".replaceFirst(\"", ".split(\"")
        var scanned = 0
        val problems = root.walkTopDown().filter { it.isFile }.flatMap { f ->
            when (f.extension) {
                "kt" -> { scanned++; scan(f, kotlinMarkers) }
                "java" -> { scanned++; scan(f, javaMarkers) }
                else -> emptyList()
            }
        }.toList()
        assertTrue("Tidak ada file sumber yang dipindai", scanned > 10)
        assertEquals("Regex tidak kompatibel ICU (Android):\n" + problems.joinToString("\n"), 0, problems.size)
    }
}
