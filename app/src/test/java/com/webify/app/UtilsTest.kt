package com.webify.app

import com.webify.app.build.AabPackager
import com.webify.app.build.BuildManager
import com.webify.app.data.web.WebsiteMetadataExtractor
import com.webify.app.domain.AppShortcutItem
import com.webify.app.domain.NativeConfigCodec
import com.webify.app.domain.NavMenuItem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UtilsTest {

    @Test
    fun normalizeColor() {
        assertEquals("#AABBCC", WebsiteMetadataExtractor.normalizeColor("#abc"))
        assertEquals("#1E88E5", WebsiteMetadataExtractor.normalizeColor("#1e88e5ff"))
        assertEquals("#FF8000", WebsiteMetadataExtractor.normalizeColor("rgb(255, 128, 0)"))
        assertEquals("#FFFFFF", WebsiteMetadataExtractor.normalizeColor(" White "))
        assertNull(WebsiteMetadataExtractor.normalizeColor("var(--brand)"))
    }

    @Test
    fun suggestPackage() {
        assertEquals("com.webify.tokosaya", WebsiteMetadataExtractor.suggestPackage("Toko Saya!"))
        assertEquals("com.webify.a99store", WebsiteMetadataExtractor.suggestPackage("99 Store"))
        assertEquals("com.webify.app", WebsiteMetadataExtractor.suggestPackage("★★★"))
    }

    @Test
    fun bumpPatch() {
        assertEquals("1.0.1", BuildManager.bumpPatch("1.0.0"))
        assertEquals("1.0.10", BuildManager.bumpPatch("1.0.9"))
        assertEquals("2.3.5-beta", BuildManager.bumpPatch("2.3.4-beta"))
        assertEquals("1.0", BuildManager.bumpPatch("1.0"))
    }

    @Test
    fun bundleConfigProto() {
        val bytes = AabPackager.bundleConfigBytes("1.15.6")
        val v = "1.15.6".toByteArray()
        val expected = byteArrayOf(0x0A, (v.size + 2).toByte(), 0x12, v.size.toByte()) + v
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun nativeConfigCodecLimitsAndResolve() {
        val items = (1..7).map { NavMenuItem("L$it", "/p$it", "home") }
        assertEquals(5, NativeConfigCodec.navItems(NativeConfigCodec.encodeNav(items)).size)
        val sc = (1..6).map { AppShortcutItem("S$it", "Long $it", "/s$it", "star") }
        assertEquals(4, NativeConfigCodec.shortcuts(NativeConfigCodec.encodeShortcuts(sc)).size)
        assertEquals("https://toko.com/cart", NativeConfigCodec.resolve("https://toko.com/shop/", "/cart"))
        assertEquals("https://other.com/x", NativeConfigCodec.resolve("https://toko.com", "https://other.com/x"))
    }
}
