package com.webify.app.build

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.domain.SourceType
import com.webify.app.domain.Validators
import java.io.File
import java.util.zip.ZipFile

/**
 * Tahap 1: Menyiapkan workspace & menghasilkan resource dinamis aplikasi output
 * (ikon multi-density + adaptive icon, values, xml, aset web lokal).
 */
class ResourceGenerator(private val context: Context) {

    private data class Density(val qualifier: String, val legacyPx: Int, val foregroundPx: Int)

    private val densities = listOf(
        Density("mdpi", 48, 108),
        Density("hdpi", 72, 162),
        Density("xhdpi", 96, 216),
        Density("xxhdpi", 144, 324),
        Density("xxxhdpi", 192, 432)
    )

    fun generate(project: WebProjectEntity, ws: Workspace, log: BuildLogger) {
        ws.prepareClean()
        log.info("Workspace dibersihkan: ${ws.root.absolutePath}")
        generateIcons(project, ws, log)
        generateValues(project, ws)
        generateXml(project, ws)
        log.info("values/strings.xml, colors.xml, styles.xml, xml/network_security_config.xml dibuat")
        if (project.sourceType == SourceType.LOCAL_HTML_ZIP) extractLocalSite(project, ws, log)
    }

    // ------------------------------------------------------------------ Icons
    private fun generateIcons(project: WebProjectEntity, ws: Workspace, log: BuildLogger) {
        val source: Bitmap = loadIconBitmap(project.iconUri, log) ?: run {
            log.warn("Ikon tidak dipilih/tidak terbaca — membuat ikon huruf otomatis")
            letterIcon(project.appName, parseColor(project.primaryColorHex, Color.parseColor("#2563EB")))
        }
        try {
            for (d in densities) {
                val dir = File(ws.res, "mipmap-${d.qualifier}").apply { mkdirs() }
                savePng(squareScaled(source, d.legacyPx), File(dir, "ic_launcher.png"))
                savePng(roundCrop(squareScaled(source, d.legacyPx)), File(dir, "ic_launcher_round.png"))
                savePng(foreground(source, d.foregroundPx), File(dir, "ic_launcher_foreground.png"))
            }
            val adaptive = """
                <?xml version="1.0" encoding="utf-8"?>
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@color/ic_launcher_background"/>
                    <foreground android:drawable="@mipmap/ic_launcher_foreground"/>
                </adaptive-icon>
            """.trimIndent()
            File(ws.res, "mipmap-anydpi-v26/ic_launcher.xml").writeText(adaptive)
            File(ws.res, "mipmap-anydpi-v26/ic_launcher_round.xml").writeText(adaptive)
            log.info("Ikon dibuat: 48/72/96/144/192 px + foreground adaptif 108dp (5 density)")
        } finally {
            source.recycle()
        }
    }

    private fun loadIconBitmap(path: String?, log: BuildLogger): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val f = File(path)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath, opts)
            else context.contentResolver.openInputStream(Uri.parse(path))?.use { BitmapFactory.decodeStream(it, null, opts) }
        } catch (e: Exception) {
            log.warn("Gagal decode ikon: ${e.message}")
            null
        }
    }

    private fun squareScaled(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val side = minOf(src.width, src.height)
        val sr = Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2)
        c.drawBitmap(src, sr, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun roundCrop(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawOval(RectF(0f, 0f, src.width.toFloat(), src.height.toFloat()), p)
        p.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        c.drawBitmap(src, 0f, 0f, p)
        src.recycle()
        return out
    }

    /** Lapisan foreground 108dp: ikon ditempatkan di safe-zone 72dp (66.67%) di tengah. */
    private fun foreground(src: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val inner = (size * 72f / 108f).toInt()
        val off = (size - inner) / 2
        val scaled = squareScaled(src, inner)
        c.drawBitmap(scaled, off.toFloat(), off.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        scaled.recycle()
        return out
    }

    private fun letterIcon(name: String, color: Int): Bitmap {
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(color)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            textSize = size * 0.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "W"
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText(letter, size / 2f, y, p)
        return bmp
    }

    private fun savePng(bmp: Bitmap, file: File) {
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
    }

    // ------------------------------------------------------------------ values/
    private fun generateValues(p: WebProjectEntity, ws: Workspace) {
        val values = File(ws.res, "values").apply { mkdirs() }
        File(values, "strings.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">${xmlString(p.appName)}</string>
                <string name="offline_title">Tidak ada koneksi</string>
                <string name="retry">Coba Lagi</string>
            </resources>
            """.trimIndent()
        )
        File(values, "colors.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <color name="ic_launcher_background">${hex(p.adaptiveBgColor, "#FFFFFF")}</color>
                <color name="colorPrimary">${hex(p.primaryColorHex, "#2563EB")}</color>
                <color name="statusBar">${hex(p.statusBarColorHex, "#2563EB")}</color>
                <color name="navigationBar">${hex(p.navigationBarColorHex, "#FFFFFF")}</color>
                <color name="splashBackground">${hex(p.splashBgColorHex, "#FFFFFF")}</color>
                <color name="progressBar">${hex(p.progressBarColorHex, "#2563EB")}</color>
            </resources>
            """.trimIndent()
        )
        val lightStatus = p.statusBarStyle == com.webify.app.domain.StatusBarStyle.DARK_ICONS
        val windowBg = if (p.enableSplash) "@color/splashBackground" else "@android:color/white"
        File(values, "styles.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <style name="WebifyTheme" parent="android:Theme.Material.Light.NoActionBar">
                    <item name="android:colorPrimary">@color/colorPrimary</item>
                    <item name="android:colorPrimaryDark">@color/statusBar</item>
                    <item name="android:colorAccent">@color/colorPrimary</item>
                    <item name="android:statusBarColor">@color/statusBar</item>
                    <item name="android:navigationBarColor">@color/navigationBar</item>
                    <item name="android:windowLightStatusBar">$lightStatus</item>
                    <item name="android:windowBackground">$windowBg</item>
                    <item name="android:windowFullscreen">${p.fullscreenMode}</item>
                    <item name="android:windowContentOverlay">@null</item>
                </style>
            </resources>
            """.trimIndent()
        )
        // windowLightNavigationBar tersedia sejak API 27
        val v27 = File(ws.res, "values-v27").apply { mkdirs() }
        File(v27, "styles.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <style name="WebifyTheme" parent="android:Theme.Material.Light.NoActionBar">
                    <item name="android:colorPrimary">@color/colorPrimary</item>
                    <item name="android:colorPrimaryDark">@color/statusBar</item>
                    <item name="android:colorAccent">@color/colorPrimary</item>
                    <item name="android:statusBarColor">@color/statusBar</item>
                    <item name="android:navigationBarColor">@color/navigationBar</item>
                    <item name="android:windowLightStatusBar">$lightStatus</item>
                    <item name="android:windowLightNavigationBar">${isLightColor(p.navigationBarColorHex)}</item>
                    <item name="android:windowBackground">$windowBg</item>
                    <item name="android:windowFullscreen">${p.fullscreenMode}</item>
                    <item name="android:windowContentOverlay">@null</item>
                </style>
            </resources>
            """.trimIndent()
        )
    }

    // ------------------------------------------------------------------ xml/
    private fun generateXml(p: WebProjectEntity, ws: Workspace) {
        val xml = File(ws.res, "xml").apply { mkdirs() }
        File(xml, "network_security_config.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <network-security-config>
                <base-config cleartextTrafficPermitted="${p.allowMixedContent}">
                    <trust-anchors>
                        <certificates src="system"/>
                    </trust-anchors>
                </base-config>
            </network-security-config>
            """.trimIndent()
        )
        File(xml, "file_paths.xml").writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <paths>
                <cache-path name="webify_cache" path="webify_capture/"/>
            </paths>
            """.trimIndent()
        )
    }

    // ------------------------------------------------------------------ assets/www
    private fun extractLocalSite(p: WebProjectEntity, ws: Workspace, log: BuildLogger) {
        val zipPath = p.localZipPath ?: throw BuildStageException(BuildStage.RESOURCES, "ZIP proyek web lokal belum dipilih")
        val zipFile = File(zipPath)
        if (!zipFile.exists()) throw BuildStageException(BuildStage.RESOURCES, "File ZIP tidak ditemukan: $zipPath")
        val www = File(ws.assets, "www").apply { mkdirs() }
        val wwwCanonical = www.canonicalPath + File.separator
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries().toList().filter { !it.isDirectory && !it.name.startsWith("__MACOSX/") }
            // Tentukan root: folder terdangkal yang memuat index.html
            val index = entries.map { it.name }.filter { it.substringAfterLast('/').equals("index.html", true) }
                .minByOrNull { it.count { c -> c == '/' } }
                ?: throw BuildStageException(BuildStage.RESOURCES, "index.html tidak ditemukan di ZIP")
            val prefix = index.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
            var count = 0
            for (e in entries) {
                if (!e.name.startsWith(prefix)) continue
                val rel = e.name.removePrefix(prefix)
                if (rel.isEmpty()) continue
                val out = File(www, rel)
                // Proteksi Zip-Slip
                if (!out.canonicalPath.startsWith(wwwCanonical))
                    throw BuildStageException(BuildStage.RESOURCES, "Entri ZIP berbahaya terdeteksi: ${e.name}")
                out.parentFile?.mkdirs()
                zip.getInputStream(e).use { ins -> out.outputStream().use { ins.copyTo(it) } }
                count++
            }
            val indexRel = index.removePrefix(prefix)
            if (!indexRel.equals("index.html", ignoreCase = false)) {
                File(www, indexRel).renameTo(File(www, "index.html"))
            }
            log.info("Proyek web lokal diekstrak ke assets/www ($count file)")
        }
    }

    // ------------------------------------------------------------------ helpers
    private fun hex(v: String, fallback: String) = if (Validators.isHexColor(v.trim())) v.trim().uppercase() else fallback

    private fun parseColor(v: String, fallback: Int) = try { Color.parseColor(v) } catch (_: Exception) { fallback }

    private fun isLightColor(v: String): Boolean {
        val c = parseColor(v, Color.WHITE)
        val lum = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
        return lum > 0.6
    }

    companion object {
        /** Escape string untuk resource XML Android (aapt2). */
        fun xmlString(s: String): String {
            val sb = StringBuilder()
            s.forEachIndexed { i, ch ->
                when (ch) {
                    '&' -> sb.append("&amp;")
                    '<' -> sb.append("&lt;")
                    '>' -> sb.append("&gt;")
                    '"' -> sb.append("\\\"")
                    '\'' -> sb.append("\\'")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '@', '?' -> if (i == 0) sb.append('\\').append(ch) else sb.append(ch)
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        fun xmlAttr(s: String): String = s.replace("&", "&amp;").replace("\"", "&quot;")
            .replace("<", "&lt;").replace(">", "&gt;").replace("'", "&apos;")
    }
}
