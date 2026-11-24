package com.webify.app.domain

import com.webify.app.data.db.BuildRecordEntity
import com.webify.app.data.db.KeystoreEntity
import com.webify.app.data.db.WebProjectEntity
import java.net.URI
import java.util.Locale

/**
 * Pemeriksa kesiapan rilis Google Play — dijalankan sebelum build Release APK / AAB.
 * Aturan bersifat statis (tanpa jaringan) dan konservatif: ERROR = hampir pasti ditolak / gagal unggah,
 * WARNING = berisiko ditolak atau berdampak buruk, INFO = saran.
 */
object PlayStoreChecker {

    enum class Severity { ERROR, WARNING, INFO }

    data class Issue(val severity: Severity, val title: String, val detail: String, val tab: Int? = null)

    /** Target API minimum Google Play untuk aplikasi baru & update (berlaku sejak 31 Agustus 2026). */
    const val REQUIRED_TARGET_SDK = 36

    /** Merek pihak ketiga yang sering ditiru aplikasi pembungkus web (kata kunci → nama merek). */
    private val BRANDS = mapOf(
        "youtube" to "YouTube", "google" to "Google", "gmail" to "Gmail", "facebook" to "Facebook",
        "instagram" to "Instagram", "whatsapp" to "WhatsApp", "messenger" to "Messenger", "tiktok" to "TikTok",
        "twitter" to "Twitter/X", "netflix" to "Netflix", "spotify" to "Spotify", "telegram" to "Telegram",
        "shopee" to "Shopee", "tokopedia" to "Tokopedia", "lazada" to "Lazada", "bukalapak" to "Bukalapak",
        "gojek" to "Gojek", "grab" to "Grab", "traveloka" to "Traveloka", "amazon" to "Amazon",
        "chatgpt" to "ChatGPT", "openai" to "OpenAI", "discord" to "Discord", "snapchat" to "Snapchat",
        "pinterest" to "Pinterest", "linkedin" to "LinkedIn", "reddit" to "Reddit", "twitch" to "Twitch",
        "roblox" to "Roblox", "minecraft" to "Minecraft", "dana" to "DANA", "ovo" to "OVO", "paypal" to "PayPal"
    )

    /** ID iklan uji resmi Google (dilarang dipakai di rilis produksi). */
    private const val ADMOB_TEST_PUBLISHER = "ca-app-pub-3940256099942544"

    fun check(
        p: WebProjectEntity,
        builds: List<BuildRecordEntity>,
        keystore: KeystoreEntity?,
        forBundle: Boolean
    ): List<Issue> = buildList {
        // --- Target API
        if (p.targetSdk < REQUIRED_TARGET_SDK) add(
            Issue(Severity.ERROR, "targetSdk $REQUIRED_TARGET_SDK wajib",
                "Google Play menolak aplikasi baru & update dengan targetSdk < $REQUIRED_TARGET_SDK. Ubah di Identitas → targetSdk.", 0)
        )

        // --- Signing
        if (keystore == null || keystore.isDebugDefault) add(
            Issue(Severity.ERROR, "Masih memakai debug key",
                "Build rilis harus ditandatangani keystore rilis milik Anda. Buat di Vault lalu pilih di tab Build. " +
                    "Simpan keystore & password-nya baik-baik — tanpa itu aplikasi tidak bisa di-update.", 5)
        )

        // --- versionCode harus naik dibanding rilis sukses sebelumnya
        val lastRelease = builds.filter { it.status == BuildStatus.SUCCESS && it.buildType.isRelease && it.versionCode > 0 }
            .maxByOrNull { it.versionCode }
        if (lastRelease != null && p.versionCode <= lastRelease.versionCode) add(
            Issue(Severity.ERROR, "versionCode tidak naik",
                "Rilis sebelumnya memakai versionCode ${lastRelease.versionCode}; Play Console menolak versionCode yang sama atau lebih kecil. " +
                    "Naikkan ke ${lastRelease.versionCode + 1} atau aktifkan auto-increment.", 0)
        )

        // --- Kebijakan privasi
        val needsPrivacy = p.admobEnabled || p.fcmEnabled || p.permCamera || p.permMicrophone || p.permGeolocation || p.qrScannerEnabled
        if (p.privacyPolicyUrl.isBlank()) add(
            Issue(if (needsPrivacy) Severity.ERROR else Severity.WARNING, "URL kebijakan privasi kosong",
                (if (needsPrivacy) "Aplikasi memakai izin sensitif / iklan / FCM sehingga wajib punya kebijakan privasi. "
                else "Play Console meminta kebijakan privasi untuk hampir semua aplikasi. ") +
                    "Isi di Tampilan → Tentang & Rating (buat teksnya lewat AI Co-Pilot).", 1)
        )

        // --- Merek pihak ketiga
        val host = runCatching { URI(p.targetUrl.trim()).host?.lowercase(Locale.ROOT) }.getOrNull().orEmpty()
        val nameLc = p.appName.lowercase(Locale.ROOT)
        val pkgLc = p.packageName.lowercase(Locale.ROOT)
        val hits = BRANDS.filter { (k, _) ->
            Regex("(^|[^a-z])$k([^a-z]|$)").containsMatchIn(nameLc) ||
                pkgLc.split('.').any { it == k } ||
                host == "$k.com" || host.endsWith(".$k.com") || host.startsWith("$k.") || host.contains(".$k.")
        }.values.distinct()
        if (hits.isNotEmpty()) add(
            Issue(Severity.ERROR, "Memakai merek pihak ketiga: ${hits.joinToString()}",
                "Aplikasi yang membungkus website atau memakai nama/ikon merek milik pihak lain melanggar kebijakan " +
                    "Peniruan Identitas & Kekayaan Intelektual Google Play dan hampir pasti ditolak. " +
                    "Gunakan hanya untuk website milik Anda sendiri.", 0)
        )

        // --- Fungsi minimum (aplikasi web wrapper)
        val nativeExtras = listOf(
            p.navStyle != NavStyle.NONE, p.shortcutsJson.length > 2, p.fcmEnabled, p.enableBiometricLock,
            p.qrScannerEnabled, p.permNotifications, p.enablePullToRefresh, p.deepLinkHost.isNotBlank()
        ).count { it }
        if (nativeExtras < 2) add(
            Issue(Severity.WARNING, "Fitur native minim",
                "Tambahkan minimal beberapa fitur khas aplikasi (menu navigasi native, notifikasi, shortcut, deep link) " +
                    "agar tidak dianggap sekadar pembungkus website.", 1)
        )

        // --- Iklan uji
        if (p.admobEnabled && listOf(p.admobAppId, p.admobBannerId, p.admobInterstitialId).any { it.startsWith(ADMOB_TEST_PUBLISHER) }) add(
            Issue(Severity.ERROR, "ID iklan uji AdMob",
                "ID uji Google tidak menghasilkan pendapatan dan tidak boleh dirilis. Ganti dengan ID dari akun AdMob Anda.", 4)
        )

        // --- Izin sensitif tanpa fitur yang jelas memakainya
        if (p.permMicrophone) add(
            Issue(Severity.WARNING, "Izin mikrofon aktif",
                "Pastikan website benar-benar memakai mikrofon (getUserMedia). Izin yang tidak dipakai dapat memicu penolakan.", 3)
        )
        if (p.permGeolocation) add(
            Issue(Severity.WARNING, "Izin lokasi aktif",
                "Lokasi butuh deklarasi data di Play Console (Keamanan Data) dan penjelasan di kebijakan privasi.", 3)
        )
        if (p.permCamera && !p.qrScannerEnabled && !p.enableFileUploadChooser) add(
            Issue(Severity.WARNING, "Izin kamera tanpa fitur kamera",
                "Kamera aktif tetapi QR scanner & upload file nonaktif. Matikan bila tidak dipakai.", 3)
        )

        // --- Ikon
        if (p.iconUri.isNullOrBlank()) add(
            Issue(Severity.WARNING, "Ikon otomatis (huruf)",
                "Gunakan ikon asli beresolusi tinggi; ikon huruf terlihat kurang profesional di Play Store.", 0)
        )

        // --- Format
        if (!forBundle) add(
            Issue(Severity.INFO, "Gunakan AAB untuk Play Store",
                "Aplikasi baru di Google Play wajib diunggah sebagai Android App Bundle (.aab). APK rilis cocok untuk distribusi langsung.", 5)
        )
        if (p.minSdk < 23) add(
            Issue(Severity.INFO, "minSdk ${p.minSdk}", "Perangkat < Android 6 sangat jarang; minSdk 23+ memperkecil risiko crash.", 0)
        )
    }.sortedBy { it.severity.ordinal }

    fun score(issues: List<Issue>): Int =
        (100 - issues.fold(0) { acc, i -> acc + when (i.severity) { Severity.ERROR -> 25; Severity.WARNING -> 8; Severity.INFO -> 0 } }).coerceIn(0, 100)
}
