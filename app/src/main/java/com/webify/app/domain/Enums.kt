package com.webify.app.domain

enum class SourceType { URL, LOCAL_HTML_ZIP }

enum class StatusBarStyle { LIGHT_ICONS, DARK_ICONS }

enum class ScreenOrientation(val manifestValue: String) {
    UNSPECIFIED("unspecified"), PORTRAIT("portrait"), LANDSCAPE("landscape")
}

enum class OfflineModeType { DEFAULT_MODERN, CUSTOM_HTML, CACHE_FIRST }

enum class WebCacheMode { LOAD_DEFAULT, LOAD_CACHE_ELSE_NETWORK, LOAD_NO_CACHE }

enum class KeyAlgorithm(val label: String, val bits: Int) {
    RSA_2048("RSA-2048", 2048), RSA_4096("RSA-4096", 4096)
}

enum class BuildType(val label: String, val extension: String) {
    APK_DEBUG("Debug APK", "apk"),
    APK_RELEASE("Release APK", "apk"),
    AAB_PLAYSTORE("Play Store AAB", "aab");

    val isRelease: Boolean get() = this != APK_DEBUG
    val isBundle: Boolean get() = this == AAB_PLAYSTORE
}

enum class BuildStatus { SUCCESS, FAILED }

enum class ChatRole { USER, ASSISTANT, SYSTEM }

object Validators {
    val PACKAGE_REGEX = Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$")
    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
        "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
        "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
        "volatile", "while", "true", "false", "null", "var", "record", "yield"
    )
    private val HEX_REGEX = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")

    /** Mengembalikan pesan error, atau null jika valid. */
    fun packageNameError(pkg: String): String? = when {
        pkg.isBlank() -> "Package name wajib diisi"
        !PACKAGE_REGEX.matches(pkg) -> "Format tidak valid (contoh: com.toko.app)"
        pkg.split('.').any { it in JAVA_KEYWORDS } -> "Segmen package tidak boleh kata kunci Java"
        pkg.length > 150 -> "Package name terlalu panjang"
        else -> null
    }

    fun isHexColor(v: String) = HEX_REGEX.matches(v)

    fun urlError(url: String): String? {
        val u = url.trim()
        if (u.isEmpty()) return "URL wajib diisi"
        if (!(u.startsWith("https://") || u.startsWith("http://"))) return "URL harus diawali http:// atau https://"
        return try {
            if (java.net.URL(u).host.isNullOrBlank()) "Host URL tidak valid" else null
        } catch (e: Exception) {
            "URL tidak valid: ${e.message}"
        }
    }
}
