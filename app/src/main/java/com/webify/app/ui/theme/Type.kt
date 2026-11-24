package com.webify.app.ui.theme

import android.content.res.AssetManager
import android.util.Log
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tiga keluarga font Webify. File variable font dibundel di assets/fonts/ oleh
 * scripts/prepare_toolchain.sh (dari repositori google/fonts, lisensi OFL) sehingga
 * berfungsi 100% offline. Bila file tidak ada, otomatis fallback ke font sistem.
 */
class WebifyFonts(val display: FontFamily, val body: FontFamily, val mono: FontFamily) {
    companion object {
        const val JAKARTA = "fonts/PlusJakartaSans.ttf"
        const val INTER = "fonts/Inter.ttf"
        const val JETBRAINS = "fonts/JetBrainsMono.ttf"

        @Volatile private var cached: WebifyFonts? = null

        fun get(assets: AssetManager): WebifyFonts = cached ?: synchronized(this) {
            cached ?: load(assets).also { cached = it }
        }

        @OptIn(ExperimentalTextApi::class)
        private fun load(assets: AssetManager): WebifyFonts {
            val available = try { assets.list("fonts")?.toSet().orEmpty() } catch (_: Exception) { emptySet() }
            fun family(path: String, fallback: FontFamily, weights: List<FontWeight>): FontFamily {
                if (path.substringAfter('/') !in available) return fallback
                return try {
                    FontFamily(weights.map { w -> Font(path, assets, weight = w) })
                } catch (e: Exception) {
                    Log.w("WebifyFonts", "Gagal memuat $path", e)
                    fallback
                }
            }
            return WebifyFonts(
                display = family(JAKARTA, FontFamily.SansSerif, listOf(FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold)),
                body = family(INTER, FontFamily.Default, listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)),
                mono = family(JETBRAINS, FontFamily.Monospace, listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.Bold))
            )
        }
    }
}

fun webifyTypography(f: WebifyFonts): Typography {
    val d = f.display
    val b = f.body
    return Typography(
        displayLarge = TextStyle(fontFamily = d, fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 60.sp, letterSpacing = (-1).sp),
        displayMedium = TextStyle(fontFamily = d, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 48.sp),
        displaySmall = TextStyle(fontFamily = d, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
        headlineLarge = TextStyle(fontFamily = d, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
        headlineMedium = TextStyle(fontFamily = d, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
        headlineSmall = TextStyle(fontFamily = d, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = d, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = d, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
        titleSmall = TextStyle(fontFamily = b, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = TextStyle(fontFamily = b, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = b, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = b, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontFamily = b, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = b, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
        labelSmall = TextStyle(fontFamily = b, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
    )
}

/** Gaya monospace untuk terminal, editor kode, package name, fingerprint. */
fun monoStyle(f: WebifyFonts, size: Int = 13, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = f.mono, fontWeight = weight, fontSize = size.sp, lineHeight = (size * 1.5f).sp)
