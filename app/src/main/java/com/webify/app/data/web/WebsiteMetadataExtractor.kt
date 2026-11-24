package com.webify.app.data.web

import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Hasil ekstraksi metadata website. */
data class WebsiteMetadata(
    val finalUrl: String,
    val name: String?,
    val shortName: String?,
    val description: String?,
    val themeColor: String?,
    val backgroundColor: String?,
    val iconFile: File?,
    val iconSource: String?,
    val manifestFound: Boolean
) {
    val displayName: String? get() = (shortName?.takeIf { it.length in 2..30 } ?: name)?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Auto-fill metadata dari URL: <title>, meta application-name / og:title / og:site_name / theme-color,
 * manifest PWA (name, short_name, theme_color, background_color, icons[] terbesar), apple-touch-icon & favicon.
 */
class WebsiteMetadataExtractor(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun extract(url: String, iconTarget: File): WebsiteMetadata = withContext(Dispatchers.IO) {
        val (finalUrl, html) = fetchHtml(url)
        val head = html.substringBefore("</head>", html).take(400_000)
        val metas = tags(head, "meta")
        val links = tags(head, "link")

        fun meta(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
            metas.firstOrNull { (it["name"] ?: it["property"])?.equals(k, true) == true }?.get("content")?.let(::decodeEntities)?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

        val title = Regex("<title[^>]*>([\\s\\S]*?)</title>", RegexOption.IGNORE_CASE).find(head)?.groupValues?.get(1)
            ?.let(::decodeEntities)?.trim()?.replace(Regex("\\s+"), " ")
        var name = meta("application-name", "og:site_name", "apple-mobile-web-app-title", "og:title") ?: title
        var shortName: String? = null
        var theme = meta("theme-color")?.let(::normalizeColor)
        var background: String? = null
        val description = meta("description", "og:description")
        val iconCandidates = ArrayList<Pair<String, Int>>() // url, size

        // PWA manifest
        var manifestFound = false
        links.firstOrNull { it["rel"]?.lowercase()?.split(' ')?.contains("manifest") == true }?.get("href")?.let { href ->
            val manifestUrl = resolve(finalUrl, href) ?: return@let
            try {
                val m = json.parseToJsonElement(fetchText(manifestUrl)).jsonObject
                manifestFound = true
                m["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { name = it }
                shortName = m["short_name"]?.jsonPrimitive?.contentOrNull
                m["theme_color"]?.jsonPrimitive?.contentOrNull?.let(::normalizeColor)?.let { theme = it }
                background = m["background_color"]?.jsonPrimitive?.contentOrNull?.let(::normalizeColor)
                m["icons"]?.jsonArray?.forEach { el ->
                    val o = el as? JsonObject ?: return@forEach
                    val src = o["src"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                    val type = o["type"]?.jsonPrimitive?.contentOrNull ?: ""
                    if (type.contains("svg") || src.lowercase(Locale.ROOT).substringBefore('?').endsWith(".svg")) return@forEach
                    val abs = resolve(manifestUrl, src) ?: return@forEach
                    iconCandidates += abs to parseSize(o["sizes"]?.jsonPrimitive?.contentOrNull)
                }
            } catch (_: Exception) {
                // manifest rusak/tidak tersedia: lanjut dengan meta tag
            }
        }

        // apple-touch-icon & ikon <link>
        links.filter { l ->
            val rel = l["rel"]?.lowercase() ?: ""
            rel.contains("apple-touch-icon") || rel.split(' ').contains("icon")
        }.forEach { l ->
            val href = l["href"] ?: return@forEach
            if (href.lowercase(Locale.ROOT).substringBefore('?').endsWith(".svg")) return@forEach
            val abs = resolve(finalUrl, href) ?: return@forEach
            val size = parseSize(l["sizes"]).let { if (it == 0 && (l["rel"] ?: "").contains("apple")) 180 else it }
            iconCandidates += abs to size
        }
        resolve(finalUrl, "/favicon.ico")?.let { iconCandidates += it to 16 }

        // Unduh ikon terbesar yang bisa didekode
        var savedIcon: File? = null
        var iconSource: String? = null
        for ((iconUrl, _) in iconCandidates.distinctBy { it.first }.sortedByDescending { it.second }) {
            try {
                val bytes = fetchBytes(iconUrl, 5_000_000)
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                if (opts.outWidth <= 0 || opts.outHeight <= 0) continue
                iconTarget.parentFile?.mkdirs()
                iconTarget.writeBytes(bytes)
                savedIcon = iconTarget
                iconSource = "$iconUrl (${opts.outWidth}×${opts.outHeight})"
                break
            } catch (_: Exception) {
                continue
            }
        }

        WebsiteMetadata(
            finalUrl = finalUrl,
            name = name?.let(::cleanTitle),
            shortName = shortName,
            description = description,
            themeColor = theme,
            backgroundColor = background,
            iconFile = savedIcon,
            iconSource = iconSource,
            manifestFound = manifestFound
        )
    }

    // ------------------------------------------------------------------ HTTP
    private fun request(url: String) = Request.Builder().url(url)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 WebifyBot")
        .header("Accept-Language", "id,en;q=0.8")
        .build()

    private fun fetchHtml(url: String): Pair<String, String> {
        client.newCall(request(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} saat membuka $url")
            val body = resp.body ?: throw IOException("Respons kosong")
            val bytes = body.byteStream().use { it.readNBytesCompat(2_000_000) }
            return resp.request.url.toString() to String(bytes, Charsets.UTF_8)
        }
    }

    private fun fetchText(url: String): String = String(fetchBytes(url, 1_000_000), Charsets.UTF_8)

    private fun fetchBytes(url: String, limit: Int): ByteArray {
        client.newCall(request(url)).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return resp.body?.byteStream()?.use { it.readNBytesCompat(limit) } ?: throw IOException("Respons kosong")
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val r = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (r < 0) break
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }

    companion object {
        private val ATTR = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""")

        /** Semua tag <name ...> beserta atribut (key lowercase). */
        fun tags(html: String, name: String): List<Map<String, String>> =
            Regex("<$name\\b([^>]*)>", RegexOption.IGNORE_CASE).findAll(html).map { m ->
                ATTR.findAll(m.groupValues[1]).associate { a ->
                    a.groupValues[1].lowercase(Locale.ROOT) to (a.groupValues[2].ifEmpty { a.groupValues[3] }.ifEmpty { a.groupValues[4] })
                }
            }.toList()

        fun resolve(base: String, href: String): String? =
            base.toHttpUrlOrNull()?.resolve(decodeEntities(href.trim()))?.toString()

        fun parseSize(sizes: String?): Int {
            if (sizes.isNullOrBlank()) return 0
            if (sizes.trim().equals("any", true)) return 1024
            return sizes.split(' ').mapNotNull { s -> s.lowercase(Locale.ROOT).split('x').firstOrNull()?.toIntOrNull() }.maxOrNull() ?: 0
        }

        /** Normalisasi warna CSS ke #RRGGBB (mendukung #rgb, #rrggbb, #rrggbbaa, rgb(), rgba(), beberapa nama). */
        fun normalizeColor(raw: String): String? {
            val v = raw.trim().lowercase(Locale.ROOT)
            val named = mapOf("white" to "#FFFFFF", "black" to "#000000", "red" to "#FF0000", "blue" to "#0000FF",
                "green" to "#008000", "gray" to "#808080", "grey" to "#808080", "orange" to "#FFA500", "purple" to "#800080",
                "navy" to "#000080", "teal" to "#008080", "yellow" to "#FFFF00")
            named[v]?.let { return it }
            Regex("^#([0-9a-f]{3})$").find(v)?.let { m ->
                val (r, g, b) = m.groupValues[1].toCharArray().map { "$it$it" }
                return "#$r$g$b".uppercase(Locale.ROOT)
            }
            Regex("^#([0-9a-f]{6})([0-9a-f]{2})?$").find(v)?.let { return "#" + it.groupValues[1].uppercase(Locale.ROOT) }
            Regex("^rgba?\\(\\s*(\\d{1,3})\\s*[, ]\\s*(\\d{1,3})\\s*[, ]\\s*(\\d{1,3})").find(v)?.let { m ->
                val c = m.groupValues.drop(1).map { it.toInt().coerceIn(0, 255) }
                return String.format(Locale.ROOT, "#%02X%02X%02X", c[0], c[1], c[2])
            }
            return null
        }

        fun decodeEntities(s: String): String = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")
            .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }

        /** Buang sufiks umum judul: "Toko Saya | Belanja Online" → "Toko Saya". */
        fun cleanTitle(t: String): String {
            val parts = t.split(Regex("\\s+[|–—·•:-]\\s+"))
            return (parts.firstOrNull()?.trim()?.takeIf { it.length >= 2 } ?: t.trim()).take(40)
        }

        /** Saran package dari nama: "Toko Saya!" → com.webify.tokosaya. */
        fun suggestPackage(name: String): String {
            val slug = name.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "").take(24).ifEmpty { "app" }
            return "com.webify." + if (slug.first().isDigit()) "a$slug" else slug
        }
    }
}
