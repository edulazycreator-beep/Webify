package com.webify.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.webify.app.domain.NavStyle
import kotlinx.serialization.Serializable
import com.webify.app.domain.BuildStatus
import com.webify.app.domain.BuildType
import com.webify.app.domain.ChatRole
import com.webify.app.domain.KeyAlgorithm
import com.webify.app.domain.OfflineModeType
import com.webify.app.domain.ScreenOrientation
import com.webify.app.domain.SourceType
import com.webify.app.domain.StatusBarStyle
import com.webify.app.domain.WebCacheMode
import java.util.UUID

@Serializable
@Entity(tableName = "web_projects", indices = [Index("updatedAt")])
data class WebProjectEntity(
    // Identitas & versioning
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val appName: String = "Aplikasi Saya",
    val packageName: String = "com.webify.myapp",
    val versionCode: Int = 1,
    val versionName: String = "1.0.0",
    val minSdk: Int = 24,
    val targetSdk: Int = 36,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Sumber web
    val sourceType: SourceType = SourceType.URL,
    val targetUrl: String = "https://",
    /** Daftar domain internal dipisah koma. Kosong = hanya host dari targetUrl. */
    val allowedDomains: String = "",
    /** Path file ZIP proyek web lokal (disalin ke filesDir) bila sourceType = LOCAL_HTML_ZIP. */
    val localZipPath: String? = null,

    // Tampilan & branding
    val iconUri: String? = null,
    val adaptiveBgColor: String = "#FFFFFF",
    val primaryColorHex: String = "#2563EB",
    val statusBarColorHex: String = "#2563EB",
    val statusBarStyle: StatusBarStyle = StatusBarStyle.LIGHT_ICONS,
    val navigationBarColorHex: String = "#FFFFFF",
    val screenOrientation: ScreenOrientation = ScreenOrientation.UNSPECIFIED,
    val fullscreenMode: Boolean = false,

    // Splash & offline
    val enableSplash: Boolean = true,
    val splashBgColorHex: String = "#FFFFFF",
    val splashDurationMs: Int = 1500,
    val splashShowLoader: Boolean = true,
    val offlineModeType: OfflineModeType = OfflineModeType.DEFAULT_MODERN,
    val customOfflineHtml: String = "",

    // WebView engine
    val enableJavaScript: Boolean = true,
    val enableDomStorage: Boolean = true,
    val enableZoomControls: Boolean = false,
    val enablePullToRefresh: Boolean = true,
    val enableSwipeBackForward: Boolean = false,
    val showTopProgressBar: Boolean = true,
    val progressBarColorHex: String = "#2563EB",
    val customUserAgent: String = "",
    val cacheMode: WebCacheMode = WebCacheMode.LOAD_DEFAULT,
    val allowMixedContent: Boolean = false,

    // WebView lanjutan (DB v2)
    val desktopMode: Boolean = false,
    val textZoomPercent: Int = 100,
    val forceDarkMode: Boolean = false,
    val hideScrollbars: Boolean = false,
    val adBlockEnabled: Boolean = false,
    val allowThirdPartyCookies: Boolean = true,
    val safeBrowsing: Boolean = true,
    val preventScreenshots: Boolean = false,
    val clearCacheOnStart: Boolean = false,
    val keepScreenOn: Boolean = false,
    val confirmExit: Boolean = false,
    val disableTextSelection: Boolean = false,
    val autoplayMedia: Boolean = true,

    // Perizinan & fitur native
    val permCamera: Boolean = false,
    val permMicrophone: Boolean = false,
    val permGeolocation: Boolean = false,
    val permNotifications: Boolean = false,
    val permStorageDownloads: Boolean = true,
    val enableFileUploadChooser: Boolean = true,
    val enableDownloadManager: Boolean = true,
    val enableExternalIntentRouting: Boolean = true,
    /** Host deep link (mis. "toko.com"), kosong = nonaktif. */
    val deepLinkHost: String = "",

    // Code injection
    val customCssCode: String = "",
    val customJsOnPageStart: String = "",
    val customJsOnPageFinished: String = "",

    // Signing
    val selectedKeystoreId: String? = null,

    // ---------------- DB v3 ----------------
    // Build
    val autoIncrementVersionCode: Boolean = true,

    // Navigasi native, shortcut, keamanan, tentang (aplikasi output)
    val navStyle: NavStyle = NavStyle.NONE,
    /** JSON List<NavMenuItem> (maks 5). */
    val navItemsJson: String = "",
    /** JSON List<AppShortcutItem> (maks 4). */
    val shortcutsJson: String = "",
    val enableBiometricLock: Boolean = false,
    val enableAboutDialog: Boolean = true,
    val privacyPolicyUrl: String = "",

    // Plugin SDK (jalur AAR)
    val fcmEnabled: Boolean = false,
    val googleServicesJsonPath: String? = null,
    val admobEnabled: Boolean = false,
    val admobAppId: String = "",
    val admobBannerId: String = "",
    val admobInterstitialId: String = "",
    val qrScannerEnabled: Boolean = false,
    /** Koordinat Maven tambahan, satu per baris (group:artifact:version). */
    val extraDependencies: String = "",
    /** Path file .aar/.jar lokal yang diimpor, satu per baris. */
    val localLibraries: String = "",
    /** Artefak transitif yang tidak ikut dibundel (group:artifact per baris). */
    val excludedDependencies: String = ""
)

@Entity(tableName = "keystores")
data class KeystoreEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val filePath: String,
    val isDebugDefault: Boolean = false,
    val keyAlias: String,
    val keyAlgorithm: KeyAlgorithm = KeyAlgorithm.RSA_2048,
    val commonName: String = "",
    val organizationalUnit: String = "",
    val organization: String = "",
    val locality: String = "",
    val state: String = "",
    val countryCode: String = "",
    val validityYears: Int = 25,
    val sha1Fingerprint: String = "",
    val sha256Fingerprint: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "build_records",
    indices = [Index("projectId"), Index("timestamp")],
    foreignKeys = [ForeignKey(
        entity = WebProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class BuildRecordEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val buildType: BuildType,
    val status: BuildStatus,
    val outputFilePath: String? = null,
    val fileSizeBytes: Long = 0,
    val durationMs: Long = 0,
    val fullConsoleLog: String = "",
    val errorSummary: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val versionCode: Int = 0,
    val versionName: String = "",
    /** {"Resources":110,...} durasi per tahap (ms). */
    val stageTimingsJson: String = "",
    /** Snapshot pengaturan proyek saat build (JSON WebProjectEntity) untuk perbandingan. */
    val configSnapshotJson: String = ""
)

@Entity(tableName = "chat_messages", indices = [Index("conversationId"), Index("createdAt")])
data class ChatMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String = "default",
    /** Proyek yang menjadi konteks saat pesan dibuat (boleh null). */
    val projectId: String? = null,
    val role: ChatRole,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
