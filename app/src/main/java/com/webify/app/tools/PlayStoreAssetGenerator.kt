package com.webify.app.tools

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.webify.app.data.db.WebProjectEntity
import java.io.File

/**
 * Generator aset grafis Google Play (dirender lokal dengan Canvas):
 *  - Hi-res icon 512×512 (PNG 32-bit, full-bleed; Play menerapkan mask sendiri)
 *  - Feature graphic 1024×500
 *  - Screenshot 1080×1920 dalam bingkai ponsel + judul promosi
 */
object PlayStoreAssetGenerator {

    // ------------------------------------------------------------------ util warna & sumber ikon
    fun color(hex: String, fallback: Int): Int = try { Color.parseColor(hex.trim()) } catch (_: Exception) { fallback }

    private fun darken(c: Int, f: Float): Int = Color.rgb(
        (Color.red(c) * f).toInt().coerceIn(0, 255), (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255)
    )

    private fun isLight(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0 > 0.6

    /** Bitmap ikon sumber proyek; bila tidak ada, ikon huruf berwarna primer. */
    fun sourceIcon(p: WebProjectEntity, size: Int = 1024): Bitmap {
        val path = p.iconUri
        if (path != null && File(path).exists()) {
            BitmapFactory.decodeFile(path)?.let { return it }
        }
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(color(p.primaryColorHex, Color.parseColor("#2563EB")))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val letter = p.appName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "W"
        c.drawText(letter, size / 2f, size / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
        return bmp
    }

    private fun drawCentered(canvas: Canvas, src: Bitmap, dst: RectF) {
        val side = minOf(src.width, src.height)
        val sr = Rect((src.width - side) / 2, (src.height - side) / 2, (src.width + side) / 2, (src.height + side) / 2)
        canvas.drawBitmap(src, sr, dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    }

    // ------------------------------------------------------------------ 1. Hi-res icon 512
    fun hiResIcon(p: WebProjectEntity): Bitmap {
        val size = 512
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val bg = color(p.adaptiveBgColor, Color.WHITE)
        c.drawColor(bg)
        val src = sourceIcon(p)
        // Ikon pengguna biasanya sudah full-bleed; jika ada transparansi di tepi, gunakan safe-zone 66%.
        val hasAlphaEdge = src.hasAlpha() && Color.alpha(src.getPixel(0, 0)) < 250
        val inset = if (hasAlphaEdge) size * (1f - 72f / 108f) / 2f else 0f
        drawCentered(c, src, RectF(inset, inset, size - inset, size - inset))
        return out
    }

    // ------------------------------------------------------------------ 2. Feature graphic 1024x500
    fun featureGraphic(p: WebProjectEntity, tagline: String): Bitmap {
        val w = 1024
        val h = 500
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val primary = color(p.primaryColorHex, Color.parseColor("#2563EB"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), primary, darken(primary, 0.55f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Ornamen lingkaran lembut
        paint.color = Color.argb(28, 255, 255, 255)
        c.drawCircle(w * 0.88f, h * 0.12f, 210f, paint)
        c.drawCircle(w * 0.72f, h * 1.05f, 160f, paint)

        // Ikon dengan bayangan
        val iconSize = 220f
        val left = 72f
        val top = (h - iconSize) / 2f
        paint.color = Color.argb(70, 0, 0, 0)
        c.drawRoundRect(RectF(left + 6, top + 10, left + iconSize + 6, top + iconSize + 10), 52f, 52f, paint)
        val iconRect = RectF(left, top, left + iconSize, top + iconSize)
        val save = c.save()
        val clip = Path().apply { addRoundRect(iconRect, 52f, 52f, Path.Direction.CW) }
        c.clipPath(clip)
        paint.color = color(p.adaptiveBgColor, Color.WHITE)
        c.drawRect(iconRect, paint)
        drawCentered(c, sourceIcon(p), iconRect)
        c.restoreToCount(save)

        // Teks
        val fg = if (isLight(primary)) Color.parseColor("#0F172A") else Color.WHITE
        val textLeft = left + iconSize + 56f
        val textWidth = (w - textLeft - 56f).toInt()
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = 68f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, Color.red(fg), Color.green(fg), Color.blue(fg))
            textSize = 34f
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val titleLayout = staticLayout(p.appName, titlePaint, textWidth, 2)
        val tagLayout = staticLayout(tagline.ifBlank { "Kini hadir sebagai aplikasi Android" }, tagPaint, textWidth, 3)
        val blockH = titleLayout.height + 18 + tagLayout.height
        var y = (h - blockH) / 2f
        c.save(); c.translate(textLeft, y); titleLayout.draw(c); c.restore()
        y += titleLayout.height + 18
        c.save(); c.translate(textLeft, y); tagLayout.draw(c); c.restore()
        return out
    }

    // ------------------------------------------------------------------ 3. Screenshot dalam bingkai ponsel
    fun framedScreenshot(p: WebProjectEntity, capture: Bitmap, headline: String): Bitmap {
        val w = 1080
        val h = 1920
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val primary = color(p.primaryColorHex, Color.parseColor("#2563EB"))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), primary, darken(primary, 0.5f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        // Judul promosi
        val fg = if (isLight(primary)) Color.parseColor("#0F172A") else Color.WHITE
        val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = 74f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        val headLayout = staticLayout(headline.ifBlank { p.appName }, headPaint, w - 160, 2, Layout.Alignment.ALIGN_CENTER)
        c.save(); c.translate(80f, 120f); headLayout.draw(c); c.restore()

        // Bingkai ponsel
        val phoneTop = 120f + headLayout.height + 80f
        val phoneW = 760f
        val phoneH = h - phoneTop + 160f // sengaja terpotong di bawah seperti mockup modern
        val phoneLeft = (w - phoneW) / 2f
        val body = RectF(phoneLeft, phoneTop, phoneLeft + phoneW, phoneTop + phoneH)
        paint.color = Color.argb(90, 0, 0, 0)
        c.drawRoundRect(RectF(body.left + 10, body.top + 18, body.right + 10, body.bottom + 18), 96f, 96f, paint)
        paint.color = Color.parseColor("#0B0F19")
        c.drawRoundRect(body, 96f, 96f, paint)
        val bezel = 22f
        val screen = RectF(body.left + bezel, body.top + bezel, body.right - bezel, body.bottom - bezel)
        val save = c.save()
        c.clipPath(Path().apply { addRoundRect(screen, 76f, 76f, Path.Direction.CW) })
        paint.color = Color.WHITE
        c.drawRect(screen, paint)
        // Skala tangkapan layar agar lebarnya pas layar ponsel
        val scale = screen.width() / capture.width
        val dst = RectF(screen.left, screen.top, screen.right, screen.top + capture.height * scale)
        c.drawBitmap(capture, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        c.restoreToCount(save)
        // Kamera punch-hole
        paint.color = Color.parseColor("#0B0F19")
        c.drawCircle(w / 2f, screen.top + 34f, 16f, paint)
        return out
    }

    private fun staticLayout(text: String, paint: TextPaint, width: Int, maxLines: Int, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.05f)
            .build()

    // ------------------------------------------------------------------ penyimpanan
    /** Simpan PNG ke Pictures/Webify_Assets/{appName}/ (MediaStore di Android 10+, folder aplikasi di bawahnya). */
    fun savePng(context: Context, bitmap: Bitmap, appName: String, fileName: String): Uri? {
        val folder = "Webify_Assets/" + appName.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifEmpty { "App" }
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + folder)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), folder).apply { mkdirs() }
            val f = File(dir, fileName)
            f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Uri.fromFile(f)
        }
    }

    /** Simpan juga salinan di cache untuk dibagikan via FileProvider. */
    fun cacheCopy(context: Context, bitmap: Bitmap, fileName: String): File {
        val dir = File(context.cacheDir, "play_assets").apply { mkdirs() }
        val f = File(dir, fileName)
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }
}
