package com.webify.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class NavStyle(val label: String) { NONE("Tanpa menu"), BOTTOM_BAR("Bottom navigation"), DRAWER("Drawer samping") }

/** Ikon vektor bawaan yang ditulis ke res/drawable aplikasi output (wf_ic_<key>). */
enum class NavIcon(val key: String, val label: String, val path: String) {
    HOME("home", "Beranda", "M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z"),
    CATEGORY("category", "Kategori", "M3,13h8V3H3v10zM3,21h8v-6H3v6zM13,21h8V11h-8v10zM13,3v6h8V3h-8z"),
    CART("cart", "Keranjang", "M7,18c-1.1,0 -1.99,0.9 -1.99,2S5.9,22 7,22s2,-0.9 2,-2 -0.9,-2 -2,-2zM1,2v2h2l3.6,7.59 -1.35,2.45c-0.16,0.28 -0.25,0.61 -0.25,0.96 0,1.1 0.9,2 2,2h12v-2H7.42c-0.14,0 -0.25,-0.11 -0.25,-0.25l0.03,-0.12 0.9,-1.63h7.45c0.75,0 1.41,-0.41 1.75,-1.03l3.58,-6.49C21.25,4.82 20.76,4 20,4H5.21l-0.94,-2H1zM17,18c-1.1,0 -1.99,0.9 -1.99,2s0.89,2 1.99,2 2,-0.9 2,-2 -0.9,-2 -2,-2z"),
    ACCOUNT("account", "Akun", "M12,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4zM12,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z"),
    SEARCH("search", "Cari", "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79l5,4.99L20.49,19l-4.99,-5zM9.5,14C7.01,14 5,11.99 5,9.5S7.01,5 9.5,5 14,7.01 14,9.5 11.99,14 9.5,14z"),
    FAVORITE("favorite", "Favorit", "M12,21.35l-1.45,-1.32C5.4,15.36 2,12.28 2,8.5 2,5.42 4.42,3 7.5,3c1.74,0 3.41,0.81 4.5,2.09C13.09,3.81 14.76,3 16.5,3 19.58,3 22,5.42 22,8.5c0,3.78 -3.4,6.86 -8.55,11.54L12,21.35z"),
    NOTIFICATIONS("notifications", "Notifikasi", "M12,22c1.1,0 2,-0.9 2,-2h-4c0,1.1 0.89,2 2,2zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32L13.5,4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1l-2,-2z"),
    CHAT("chat", "Chat", "M20,2L4,2c-1.1,0 -1.99,0.9 -1.99,2L2,22l4,-4h14c1.1,0 2,-0.9 2,-2L22,4c0,-1.1 -0.9,-2 -2,-2z"),
    STAR("star", "Promo", "M12,17.27L18.18,21l-1.64,-7.03L22,9.24l-7.19,-0.61L12,2 9.19,8.63 2,9.24l5.46,4.73L5.82,21z"),
    INFO("info", "Info", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-6h2v6zM13,9h-2L11,7h2v2z"),
    LIST("list", "Daftar", "M3,13h2v-2L3,11v2zM3,17h2v-2L3,15v2zM3,9h2L5,7L3,7v2zM7,13h14v-2L7,11v2zM7,17h14v-2L7,15v2zM7,7v2h14L21,7L7,7z"),
    RECEIPT("receipt", "Pesanan", "M18,17L6,17v-2h12v2zM18,13L6,13v-2h12v2zM18,9L6,9L6,7h12v2zM3,22l1.5,-1.5L6,22l1.5,-1.5L9,22l1.5,-1.5L12,22l1.5,-1.5L15,22l1.5,-1.5L18,22l1.5,-1.5L21,22L21,2l-1.5,1.5L18,2l-1.5,1.5L15,2l-1.5,1.5L12,2l-1.5,1.5L9,2 7.5,3.5 6,2 4.5,3.5 3,2v20z");

    companion object {
        fun of(key: String?) = entries.firstOrNull { it.key == key || it.name == key } ?: HOME
    }
}

/** Ikon sistem tambahan yang selalu ditulis (menu drawer, kunci biometrik, tentang). */
object SystemIcons {
    const val MENU = "M3,18h18v-2L3,16v2zM3,13h18v-2L3,11v2zM3,6v2h18L21,6L3,6z"
    const val LOCK = "M18,8h-1L17,6c0,-2.76 -2.24,-5 -5,-5S7,3.24 7,6v2L6,8c-1.1,0 -2,0.9 -2,2v10c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2L20,10c0,-1.1 -0.9,-2 -2,-2zM12,17c-1.1,0 -2,-0.9 -2,-2s0.9,-2 2,-2 2,0.9 2,2 -0.9,2 -2,2zM15.1,8L8.9,8L8.9,6c0,-1.71 1.39,-3.1 3.1,-3.1 1.71,0 3.1,1.39 3.1,3.1v2z"
}

@Serializable
data class NavMenuItem(val label: String, val targetUrl: String, val icon: String = NavIcon.HOME.key)

@Serializable
data class AppShortcutItem(val shortLabel: String, val longLabel: String, val targetUrl: String, val icon: String = NavIcon.STAR.key)

object NativeConfigCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun navItems(raw: String): List<NavMenuItem> =
        if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString(ListSerializer(NavMenuItem.serializer()), raw) }.getOrDefault(emptyList())

    fun encodeNav(items: List<NavMenuItem>): String = json.encodeToString(ListSerializer(NavMenuItem.serializer()), items.take(5))

    fun shortcuts(raw: String): List<AppShortcutItem> =
        if (raw.isBlank()) emptyList() else runCatching { json.decodeFromString(ListSerializer(AppShortcutItem.serializer()), raw) }.getOrDefault(emptyList())

    fun encodeShortcuts(items: List<AppShortcutItem>): String = json.encodeToString(ListSerializer(AppShortcutItem.serializer()), items.take(4))

    /** Resolve URL relatif ("/akun") terhadap URL target proyek. */
    fun resolve(base: String, target: String): String {
        val t = target.trim()
        if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://")) return t
        return try { java.net.URL(java.net.URL(base), t).toString() } catch (_: Exception) { t }
    }
}
