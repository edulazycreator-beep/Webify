package com.webify.app.template

import android.content.Context
import android.util.Base64
import com.webify.app.build.BuildLogger
import com.webify.app.build.BuildStage
import com.webify.app.build.BuildStageException
import com.webify.app.build.Workspace
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.domain.NativeConfigCodec
import com.webify.app.domain.NavIcon
import com.webify.app.domain.NavStyle
import com.webify.app.domain.OfflineModeType
import com.webify.app.domain.SourceType
import com.webify.app.domain.StatusBarStyle
import com.webify.app.domain.Validators
import com.webify.app.domain.WebCacheMode
import java.io.File

/**
 * Tahap 4: Menghasilkan kode sumber Java aplikasi WebView (hanya API android.jar).
 * Template kelas statis disimpan di assets/java_templates; BuildConfig.java dibuat dinamis.
 */
class JavaTemplateGenerator(private val context: Context) {

    fun generate(project: WebProjectEntity, ws: Workspace, log: BuildLogger) {
        val pkg = project.packageName
        val pkgDir = File(ws.src, pkg.replace('.', '/')).apply { mkdirs() }

        val bc = File(pkgDir, "BuildConfig.java")
        bc.writeText(buildConfig(project))
        log.info("BuildConfig.java dibuat (${bc.length()} byte)")

        val pluginWrites = File(pkgDir, "WebifyPlugins.java")
        pluginWrites.writeText(pluginsClass(project))
        val templates = TEMPLATES + buildList {
            if (project.fcmEnabled) { add("WebifyFirebaseMessagingService.java"); add("WebifyFcm.java") }
            if (project.admobEnabled) add("WebifyAdManager.java")
            if (project.qrScannerEnabled) add("WebifyQrScannerActivity.java")
        }
        if (templates.size > TEMPLATES.size) log.info("Plugin SDK: ${templates.drop(TEMPLATES.size).joinToString()}")
        for (name in templates) {
            val source = try {
                context.assets.open("$TEMPLATE_DIR/$name").use { it.readBytes().toString(Charsets.UTF_8) }
            } catch (e: Exception) {
                throw BuildStageException(BuildStage.JAVA_GEN, "Template $name tidak ditemukan di aset Webify", e)
            }
            File(pkgDir, name).writeText(source.replace("{{PACKAGE}}", pkg))
            log.debug("→ $name")
        }
        val rJava = File(pkgDir, "R.java")
        if (!rJava.exists()) throw BuildStageException(BuildStage.JAVA_GEN, "R.java tidak ditemukan — aapt2 link --java gagal menulis ke ${pkgDir.path}")
        log.info("${templates.size + 2} kelas Java + R.java siap di ${pkgDir.relativeTo(ws.root).path}")
    }

    private fun buildConfig(p: WebProjectEntity): String {
        val isLocal = p.sourceType == SourceType.LOCAL_HTML_ZIP
        val target = if (isLocal) "file:///android_asset/www/index.html" else p.targetUrl.trim()
        val domains = p.allowedDomains.split(',', '\n', ' ', ';')
            .map { it.trim().removePrefix("https://").removePrefix("http://").substringBefore('/') }
            .filter { it.isNotEmpty() }
            .distinct()
        val cacheMode = when (p.cacheMode) {
            WebCacheMode.LOAD_DEFAULT -> -1
            WebCacheMode.LOAD_CACHE_ELSE_NETWORK -> 1
            WebCacheMode.LOAD_NO_CACHE -> 2
        }
        val offlineMode = when (p.offlineModeType) {
            OfflineModeType.DEFAULT_MODERN -> 0
            OfflineModeType.CUSTOM_HTML -> 1
            OfflineModeType.CACHE_FIRST -> 2
        }
        val offlineHtml = if (p.offlineModeType == OfflineModeType.CUSTOM_HTML && p.customOfflineHtml.isNotBlank())
            p.customOfflineHtml else defaultOfflineHtml(p)
        val offlineB64 = Base64.encodeToString(offlineHtml.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        return buildString {
            appendLine("package ${p.packageName};")
            appendLine()
            appendLine("/** Dihasilkan otomatis oleh Webify — jangan diedit manual. */")
            appendLine("public final class BuildConfig {")
            appendLine("    private BuildConfig() {}")
            appendLine()
            appendLine("    private static String j(String[] parts) {")
            appendLine("        StringBuilder sb = new StringBuilder();")
            appendLine("        for (String s : parts) sb.append(s);")
            appendLine("        return sb.toString();")
            appendLine("    }")
            appendLine()
            appendLine("    public static final String APPLICATION_ID = ${lit(p.packageName)};")
            appendLine("    public static final String VERSION_NAME = ${lit(p.versionName)};")
            appendLine("    public static final int VERSION_CODE = ${p.versionCode};")
            appendLine("    public static final String APP_NAME = ${lit(p.appName)};")
            appendLine()
            appendLine("    // Sumber web")
            appendLine("    public static final String TARGET_URL = ${lit(target)};")
            appendLine("    public static final boolean IS_LOCAL_SOURCE = $isLocal;")
            appendLine("    public static final String[] ALLOWED_DOMAINS = new String[]{${domains.joinToString(", ") { lit(it) }}};")
            appendLine()
            appendLine("    // Tampilan")
            appendLine("    public static final int PRIMARY_COLOR = ${color(p.primaryColorHex, "#2563EB")};")
            appendLine("    public static final int STATUS_BAR_COLOR = ${color(p.statusBarColorHex, "#2563EB")};")
            appendLine("    public static final boolean STATUS_BAR_LIGHT_ICONS = ${p.statusBarStyle == StatusBarStyle.LIGHT_ICONS};")
            appendLine("    public static final int NAV_BAR_COLOR = ${color(p.navigationBarColorHex, "#FFFFFF")};")
            appendLine("    public static final boolean FULLSCREEN = ${p.fullscreenMode};")
            appendLine()
            appendLine("    // Splash & offline")
            appendLine("    public static final boolean ENABLE_SPLASH = ${p.enableSplash};")
            appendLine("    public static final int SPLASH_BG_COLOR = ${color(p.splashBgColorHex, "#FFFFFF")};")
            appendLine("    public static final int SPLASH_DURATION_MS = ${p.splashDurationMs.coerceIn(0, 10000)};")
            appendLine("    public static final boolean SPLASH_SHOW_LOADER = ${p.splashShowLoader};")
            appendLine("    public static final int OFFLINE_DEFAULT = 0;")
            appendLine("    public static final int OFFLINE_CUSTOM = 1;")
            appendLine("    public static final int OFFLINE_CACHE_FIRST = 2;")
            appendLine("    public static final int OFFLINE_MODE = $offlineMode;")
            appendLine("    public static final String OFFLINE_HTML_BASE64 = ${bigLit(offlineB64)};")
            appendLine()
            appendLine("    // WebView engine")
            appendLine("    public static final boolean ENABLE_JAVASCRIPT = ${p.enableJavaScript};")
            appendLine("    public static final boolean ENABLE_DOM_STORAGE = ${p.enableDomStorage};")
            appendLine("    public static final boolean ENABLE_ZOOM = ${p.enableZoomControls};")
            appendLine("    public static final boolean ENABLE_PULL_TO_REFRESH = ${p.enablePullToRefresh};")
            appendLine("    public static final boolean ENABLE_SWIPE_NAVIGATION = ${p.enableSwipeBackForward};")
            appendLine("    public static final boolean SHOW_TOP_PROGRESS = ${p.showTopProgressBar};")
            appendLine("    public static final int PROGRESS_COLOR = ${color(p.progressBarColorHex, "#2563EB")};")
            appendLine("    public static final String CUSTOM_USER_AGENT = ${lit(p.customUserAgent)};")
            appendLine("    public static final int CACHE_MODE = $cacheMode;")
            appendLine("    public static final boolean ALLOW_MIXED_CONTENT = ${p.allowMixedContent};")
            appendLine("    public static final boolean DESKTOP_MODE = ${p.desktopMode};")
            appendLine("    public static final int TEXT_ZOOM = ${p.textZoomPercent.coerceIn(50, 200)};")
            appendLine("    public static final boolean FORCE_DARK = ${p.forceDarkMode};")
            appendLine("    public static final boolean HIDE_SCROLLBARS = ${p.hideScrollbars};")
            appendLine("    public static final boolean AUTOPLAY_MEDIA = ${p.autoplayMedia};")
            appendLine()
            appendLine("    // Privasi & keamanan")
            appendLine("    public static final boolean AD_BLOCK = ${p.adBlockEnabled};")
            appendLine("    public static final boolean THIRD_PARTY_COOKIES = ${p.allowThirdPartyCookies};")
            appendLine("    public static final boolean SAFE_BROWSING = ${p.safeBrowsing};")
            appendLine("    public static final boolean PREVENT_SCREENSHOTS = ${p.preventScreenshots};")
            appendLine("    public static final boolean CLEAR_CACHE_ON_START = ${p.clearCacheOnStart};")
            appendLine()
            appendLine("    // Perilaku")
            appendLine("    public static final boolean KEEP_SCREEN_ON = ${p.keepScreenOn};")
            appendLine("    public static final boolean CONFIRM_EXIT = ${p.confirmExit};")
            appendLine("    public static final boolean DISABLE_TEXT_SELECTION = ${p.disableTextSelection};")
            appendLine()
            appendLine("    // Izin & fitur native")
            appendLine("    public static final boolean PERM_CAMERA = ${p.permCamera};")
            appendLine("    public static final boolean PERM_MICROPHONE = ${p.permMicrophone};")
            appendLine("    public static final boolean PERM_GEOLOCATION = ${p.permGeolocation};")
            appendLine("    public static final boolean PERM_NOTIFICATIONS = ${p.permNotifications};")
            appendLine("    public static final boolean ENABLE_FILE_UPLOAD = ${p.enableFileUploadChooser};")
            appendLine("    public static final boolean ENABLE_DOWNLOAD_MANAGER = ${p.enableDownloadManager};")
            appendLine("    public static final boolean ENABLE_EXTERNAL_INTENTS = ${p.enableExternalIntentRouting};")
            appendLine()
            appendLine("    // Navigasi native, keamanan & tentang")
            val navItems = if (p.navStyle == NavStyle.NONE) emptyList()
                else NativeConfigCodec.navItems(p.navItemsJson).filter { it.label.isNotBlank() && it.targetUrl.isNotBlank() }.take(5)
            appendLine("    public static final int NAV_STYLE = ${if (navItems.isEmpty()) 0 else p.navStyle.ordinal};")
            appendLine("    public static final String[] NAV_LABELS = new String[]{${navItems.joinToString(", ") { lit(it.label) }}};")
            appendLine("    public static final String[] NAV_URLS = new String[]{${navItems.joinToString(", ") { lit(NativeConfigCodec.resolve(target, it.targetUrl)) }}};")
            appendLine("    public static final int[] NAV_ICONS = new int[]{${navItems.joinToString(", ") { "R.drawable.wf_ic_" + NavIcon.of(it.icon).key }}};")
            appendLine("    public static final boolean BIOMETRIC_LOCK = ${p.enableBiometricLock};")
            appendLine("    public static final boolean ABOUT_ENABLED = ${p.enableAboutDialog};")
            appendLine("    public static final String PRIVACY_URL = ${lit(p.privacyPolicyUrl.trim())};")
            appendLine()
            appendLine("    // Plugin SDK")
            appendLine("    public static final boolean FCM_ENABLED = ${p.fcmEnabled};")
            appendLine("    public static final boolean ADMOB_ENABLED = ${p.admobEnabled};")
            appendLine("    public static final String ADMOB_BANNER_ID = ${lit(p.admobBannerId.trim())};")
            appendLine("    public static final String ADMOB_INTERSTITIAL_ID = ${lit(p.admobInterstitialId.trim())};")
            appendLine("    public static final boolean QR_ENABLED = ${p.qrScannerEnabled};")
            appendLine()
            appendLine("    // Code injection")
            appendLine("    public static final String CUSTOM_CSS = ${bigLit(p.customCssCode)};")
            appendLine("    public static final String CUSTOM_JS_ON_PAGE_START = ${bigLit(p.customJsOnPageStart)};")
            appendLine("    public static final String CUSTOM_JS_ON_PAGE_FINISHED = ${bigLit(p.customJsOnPageFinished)};")
            appendLine("    // alias pendek")
            appendLine("    public static final String CUSTOM_JS_END = CUSTOM_JS_ON_PAGE_FINISHED;")
            appendLine("    public static final int SPLASH_DURATION = SPLASH_DURATION_MS;")
            appendLine("    public static final boolean ENABLE_JS = ENABLE_JAVASCRIPT;")
            appendLine("}")
        }
    }

    /**
     * Titik integrasi plugin: satu-satunya kelas yang memanggil kelas SDK (Firebase/AdMob/ZXing).
     * Jika plugin nonaktif, metode berisi no-op sehingga proyek tetap terkompilasi tanpa library eksternal.
     */
    private fun pluginsClass(p: WebProjectEntity): String = buildString {
        appendLine("package ${p.packageName};")
        appendLine()
        appendLine("/** Dihasilkan otomatis oleh Webify — jembatan ke plugin SDK yang aktif. */")
        appendLine("final class WebifyPlugins {")
        appendLine("    private WebifyPlugins() {}")
        appendLine()
        appendLine("    static void onCreate(MainActivity a) {")
        if (p.fcmEnabled) appendLine("        WebifyFcm.init(a);")
        if (p.admobEnabled) appendLine("        WebifyAdManager.init(a);")
        appendLine("    }")
        appendLine()
        appendLine("    static void onPageFinished(MainActivity a, android.webkit.WebView view) {")
        if (p.fcmEnabled) appendLine("        WebifyFcm.deliverToken(a, view);")
        appendLine("    }")
        appendLine()
        appendLine("    /** Banner iklan native di bawah WebView, atau null. */")
        appendLine("    static android.view.View createBanner(MainActivity a) {")
        appendLine(if (p.admobEnabled) "        return WebifyAdManager.createBanner(a);" else "        return null;")
        appendLine("    }")
        appendLine()
        appendLine("    static boolean showInterstitial(MainActivity a) {")
        appendLine(if (p.admobEnabled) "        return WebifyAdManager.showInterstitial(a);" else "        return false;")
        appendLine("    }")
        appendLine()
        appendLine("    static boolean scanQr(MainActivity a, String callback) {")
        if (p.qrScannerEnabled) {
            appendLine("        android.content.Intent i = new android.content.Intent(a, WebifyQrScannerActivity.class);")
            appendLine("        i.putExtra(\"callback\", callback);")
            appendLine("        a.startActivityForResult(i, MainActivity.REQ_QR_SCAN);")
            appendLine("        return true;")
        } else appendLine("        return false;")
        appendLine("    }")
        appendLine()
        appendLine("    static void requestFcmToken(MainActivity a, String callback) {")
        if (p.fcmEnabled) appendLine("        WebifyFcm.requestToken(a, callback);")
        else appendLine("        a.deliverJs(callback, \"null\");")
        appendLine("    }")
        appendLine()
        appendLine("    static void onDestroy(MainActivity a) {")
        if (p.admobEnabled) appendLine("        WebifyAdManager.destroy();")
        appendLine("    }")
        appendLine("}")
    }

    private fun defaultOfflineHtml(p: WebProjectEntity): String {
        val primary = if (Validators.isHexColor(p.primaryColorHex)) p.primaryColorHex else "#2563EB"
        val name = htmlEscape(p.appName)
        return """
<!DOCTYPE html>
<html lang="id"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>Offline</title>
<style>
:root{--p:$primary}
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:system-ui,-apple-system,Roboto,sans-serif;min-height:100vh;display:flex;align-items:center;justify-content:center;
background:#F8FAFC;color:#0F172A;padding:24px}
@media (prefers-color-scheme:dark){body{background:#0B0F19;color:#F1F5F9}.card{background:#111827!important;border-color:#1E293B!important}p{color:#94A3B8!important}}
.card{max-width:380px;width:100%;background:#fff;border:1px solid #E2E8F0;border-radius:24px;padding:36px 28px;text-align:center;
box-shadow:0 10px 30px rgba(15,23,42,.06)}
.icon{width:72px;height:72px;margin:0 auto 20px;border-radius:50%;background:color-mix(in srgb,var(--p) 14%,transparent);
display:flex;align-items:center;justify-content:center}
.icon svg{width:36px;height:36px;stroke:var(--p)}
h1{font-size:20px;font-weight:700;margin-bottom:8px}
p{font-size:14px;line-height:1.6;color:#475569;margin-bottom:24px}
button{appearance:none;border:0;background:var(--p);color:#fff;font-size:15px;font-weight:600;padding:14px 28px;border-radius:999px;width:100%;
cursor:pointer;transition:transform .1s}
button:active{transform:scale(.97)}
small{display:block;margin-top:16px;font-size:12px;opacity:.6}
</style></head>
<body><div class="card">
<div class="icon"><svg viewBox="0 0 24 24" fill="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
<path d="M2 8.82a15 15 0 0 1 4.17-2.65"/><path d="M10.66 5c4.01-.36 8.14.9 11.34 3.76"/><path d="M16.85 11.25a10 10 0 0 1 2.22 1.68"/>
<path d="M5 12.86a10 10 0 0 1 5.17-2.69"/><path d="M8.5 16.43a5 5 0 0 1 7 0"/><path d="M12 20h.01"/><path d="m2 2 20 20"/></svg></div>
<h1>Tidak ada koneksi internet</h1>
<p>Periksa Wi-Fi atau data seluler Anda, lalu coba lagi untuk membuka $name.</p>
<button onclick="location.href='webify://retry'">Coba Lagi</button>
<small>$name</small>
</div>
<script>window.addEventListener('online',function(){location.href='webify://retry';});</script>
</body></html>
""".trim()
    }

    private fun htmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun color(hex: String, fallback: String): String {
        val h = (if (Validators.isHexColor(hex.trim())) hex.trim() else fallback).removePrefix("#")
        val argb = if (h.length == 6) "FF$h" else h
        return "0x" + argb.uppercase()
    }

    companion object {
        const val TEMPLATE_DIR = "java_templates"
        val TEMPLATES = listOf(
            "MainActivity.java",
            "WebifyWebViewClient.java",
            "WebifyWebChromeClient.java",
            "WebifyDownloadListener.java",
            "WebifyJsBridge.java",
            "WebifyFileProvider.java",
            "WebifyRefreshLayout.java",
            "WebifySplashView.java",
            "WebifyNativeNav.java",
            "BiometricLockHelper.java",
            "WebifyAboutDialog.java",
            "WebifyNotificationReceiver.java"
        )
        private const val CHUNK = 8000

        /** Literal string Java yang aman (escape + unicode untuk non-ASCII). */
        fun lit(s: String): String {
            val sb = StringBuilder("\"")
            for (ch in s) {
                when {
                    ch == '\\' -> sb.append("\\\\")
                    ch == '"' -> sb.append("\\\"")
                    ch == '\n' -> sb.append("\\n")
                    ch == '\r' -> sb.append("\\r")
                    ch == '\t' -> sb.append("\\t")
                    ch.code < 0x20 || ch.code > 0x7E -> sb.append(String.format("\\u%04x", ch.code))
                    else -> sb.append(ch)
                }
            }
            return sb.append('"').toString()
        }

        /** String besar dipecah per-chunk agar tidak melampaui batas constant pool 65535 byte. */
        fun bigLit(s: String): String {
            if (s.length <= CHUNK) return lit(s)
            val parts = s.chunked(CHUNK).map { lit(it) }
            return "j(new String[]{\n        " + parts.joinToString(",\n        ") + "})"
        }
    }
}
