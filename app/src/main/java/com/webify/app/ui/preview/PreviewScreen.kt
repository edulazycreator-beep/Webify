package com.webify.app.ui.preview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webify.app.AppContainer
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.domain.SourceType
import com.webify.app.domain.WebCacheMode
import com.webify.app.ui.components.EmptyState
import com.webify.app.ui.components.parseHexColor
import com.webify.app.ui.theme.Dimens
import com.webify.app.ui.theme.WebifyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Pratinjau langsung proyek tanpa build: WebView dengan pengaturan, CSS/JS injeksi,
 * warna status bar & progress bar, mode desktop/gelap, dan ukuran teks yang sama
 * seperti aplikasi hasil build. Fitur native (upload, unduhan, izin) hanya aktif di APK.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(projectId: String, container: AppContainer, onBack: () -> Unit) {
    val project by container.projectRepository.observe(projectId).collectAsStateWithLifecycle(initialValue = null)
    // Snapshot konfigurasi saat layar dibuka agar WebView tidak dimuat ulang tiap rekomposisi
    var snapshot by remember { mutableStateOf<WebProjectEntity?>(null) }
    LaunchedEffect(project) { if (snapshot == null && project != null) snapshot = project }

    val p = snapshot
    var startUrl by remember { mutableStateOf<String?>(null) }
    var prepareError by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var showHint by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    LaunchedEffect(p?.id) {
        val proj = p ?: return@LaunchedEffect
        try {
            startUrl = if (proj.sourceType == SourceType.URL) proj.targetUrl.trim()
            else extractLocalSite(ctx.filesDir, proj)
        } catch (e: Exception) {
            prepareError = e.message ?: "Gagal menyiapkan pratinjau"
        }
    }

    BackHandler(enabled = canGoBack) { webView?.goBack() }
    DisposableEffect(Unit) {
        onDispose {
            webView?.let { wv ->
                wv.stopLoading()
                wv.destroy()
            }
        }
    }

    val statusColor = p?.let { parseHexColor(it.statusBarColorHex) } ?: MaterialTheme.colorScheme.primary
    val progressColor = p?.let { parseHexColor(it.progressBarColorHex) } ?: MaterialTheme.colorScheme.primary
    val statusContent = if (statusColor.luminance() > 0.5f) Color(0xFF0F172A) else Color.White

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", Modifier.size(Dimens.IconBar)) } },
                title = {
                    Column {
                        Text("Preview", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Text(
                            p?.let { if (it.sourceType == SourceType.URL) it.targetUrl else "Proyek HTML lokal" } ?: "",
                            style = WebifyTheme.mono.copy(fontSize = 12.sp, lineHeight = 16.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { pageError = null; webView?.reload() }) { Icon(Icons.Rounded.Refresh, "Muat ulang", Modifier.size(Dimens.IconBar)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).navigationBarsPadding()) {
            when {
                prepareError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(Icons.Rounded.Warning, "Pratinjau tidak tersedia", prepareError!!)
                }
                p == null || startUrl == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    // Simulasi status bar aplikasi hasil build
                    if (!p.fullscreenMode) {
                        Row(
                            Modifier.fillMaxWidth().background(statusColor).padding(horizontal = 14.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(p.appName, color = statusContent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("12:30  ▾ ◢ ▮", color = statusContent, fontSize = 11.sp)
                        }
                    }
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        PreviewWebView(
                            project = p,
                            url = startUrl!!,
                            onCreated = { webView = it },
                            onProgress = { progress = it },
                            onNavState = { canGoBack = it },
                            onError = { pageError = it }
                        )
                        if (p.showTopProgressBar && progress in 1..99) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                color = progressColor,
                                trackColor = Color.Transparent,
                                modifier = Modifier.fillMaxWidth().height(3.dp).align(Alignment.TopCenter)
                            )
                        }
                        pageError?.let { err ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(Dimens.RadiusControl),
                                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp)
                            ) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Warning, null, Modifier.size(Dimens.IconInline), tint = MaterialTheme.colorScheme.error)
                                    Spacer(Modifier.width(Dimens.IconTextGap))
                                    Text(err, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, false))
                                }
                            }
                        }
                        AnimatedVisibility(
                            visible = showHint,
                            enter = fadeIn(), exit = fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.inverseSurface,
                                shape = RoundedCornerShape(Dimens.RadiusControl),
                                shadowElevation = 4.dp
                            ) {
                                Row(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Info, null, Modifier.size(Dimens.IconChip), tint = MaterialTheme.colorScheme.inverseOnSurface)
                                    Spacer(Modifier.width(Dimens.IconTextGapSmall))
                                    Text(
                                        "Mode preview: upload, unduhan & izin native hanya aktif di APK.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.weight(1f, false)
                                    )
                                    IconButton(onClick = { showHint = false }) {
                                        Icon(Icons.Rounded.Close, "Tutup", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PreviewWebView(
    project: WebProjectEntity,
    url: String,
    onCreated: (WebView) -> Unit,
    onProgress: (Int) -> Unit,
    onNavState: (Boolean) -> Unit,
    onError: (String) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp)),
        factory = { context ->
            WebView(context).apply {
                applySettings(this, project)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, u: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, u, favicon)
                        injectCode(view, project, finished = false)
                    }

                    override fun onPageFinished(view: WebView, u: String?) {
                        super.onPageFinished(view, u)
                        injectCode(view, project, finished = true)
                        onNavState(view.canGoBack())
                    }

                    override fun doUpdateVisitedHistory(view: WebView, u: String?, isReload: Boolean) {
                        super.doUpdateVisitedHistory(view, u, isReload)
                        onNavState(view.canGoBack())
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) onError("Gagal memuat (${error.errorCode}): ${error.description}")
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) = onProgress(newProgress)
                }
                onCreated(this)
                loadUrl(url)
            }
        }
    )
}

@Suppress("DEPRECATION")
private fun applySettings(wv: WebView, p: WebProjectEntity) {
    val s = wv.settings
    s.javaScriptEnabled = p.enableJavaScript
    s.domStorageEnabled = p.enableDomStorage
    s.databaseEnabled = p.enableDomStorage
    s.setSupportZoom(p.enableZoomControls)
    s.builtInZoomControls = p.enableZoomControls
    s.displayZoomControls = false
    s.loadWithOverviewMode = true
    s.useWideViewPort = true
    s.textZoom = p.textZoomPercent.coerceIn(50, 200)
    s.mediaPlaybackRequiresUserGesture = !p.autoplayMedia
    s.allowFileAccess = p.sourceType == SourceType.LOCAL_HTML_ZIP
    s.mixedContentMode = if (p.allowMixedContent) WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE else WebSettings.MIXED_CONTENT_NEVER_ALLOW
    s.cacheMode = when (p.cacheMode) {
        WebCacheMode.LOAD_DEFAULT -> WebSettings.LOAD_DEFAULT
        WebCacheMode.LOAD_CACHE_ELSE_NETWORK -> WebSettings.LOAD_CACHE_ELSE_NETWORK
        WebCacheMode.LOAD_NO_CACHE -> WebSettings.LOAD_NO_CACHE
    }
    val ua = when {
        p.customUserAgent.isNotBlank() -> p.customUserAgent.trim()
        p.desktopMode -> DESKTOP_UA
        else -> s.userAgentString
    }
    s.userAgentString = "$ua WebifyApp/${p.versionName}"
    if (Build.VERSION.SDK_INT >= 26) s.safeBrowsingEnabled = p.safeBrowsing
    if (p.forceDarkMode) {
        try {
            if (Build.VERSION.SDK_INT >= 33) s.isAlgorithmicDarkeningAllowed = true
            else if (Build.VERSION.SDK_INT >= 29) s.forceDark = WebSettings.FORCE_DARK_ON
        } catch (_: Throwable) { }
    }
    CookieManager.getInstance().setAcceptThirdPartyCookies(wv, p.allowThirdPartyCookies)
    wv.isVerticalScrollBarEnabled = !p.hideScrollbars
    wv.isHorizontalScrollBarEnabled = !p.hideScrollbars
}

private fun injectCode(view: WebView, p: WebProjectEntity, finished: Boolean) {
    if (!p.enableJavaScript) return
    var css = p.customCssCode
    if (p.disableTextSelection) {
        css = "*:not(input):not(textarea):not([contenteditable]){-webkit-user-select:none!important;user-select:none!important}\n$css"
    }
    if (p.hideScrollbars) css = "::-webkit-scrollbar{display:none!important}\n$css"
    if (css.isNotBlank()) {
        view.evaluateJavascript(
            "(function(){try{var id='webify-custom-css';var s=document.getElementById(id);" +
                "if(!s){s=document.createElement('style');s.id=id;(document.head||document.documentElement).appendChild(s);}" +
                "s.textContent=decodeURIComponent(escape(atob('${b64(css)}')));}catch(e){console.error(e);}})();", null
        )
    }
    val js = if (finished) p.customJsOnPageFinished else p.customJsOnPageStart
    if (js.isNotBlank()) {
        view.evaluateJavascript(
            "(function(){try{var c=decodeURIComponent(escape(atob('${b64(js)}')));(new Function(c))();}catch(e){console.error('Webify JS',e);}})();", null
        )
    }
}

private fun b64(s: String) = Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

/** Ekstrak ZIP proyek lokal ke filesDir/preview/<id> dan kembalikan URL file:// ke index.html. */
private suspend fun extractLocalSite(filesDir: File, p: WebProjectEntity): String = withContext(Dispatchers.IO) {
    val zipPath = p.localZipPath ?: throw IllegalStateException("ZIP proyek web belum dipilih")
    val out = File(filesDir, "preview/${p.id}")
    out.deleteRecursively()
    out.mkdirs()
    val root = out.canonicalPath + File.separator
    ZipFile(zipPath).use { zip ->
        val entries = zip.entries().toList().filter { !it.isDirectory && !it.name.startsWith("__MACOSX/") }
        val index = entries.map { it.name }.filter { it.substringAfterLast('/').equals("index.html", true) }
            .minByOrNull { it.count { c -> c == '/' } } ?: throw IllegalStateException("index.html tidak ditemukan di ZIP")
        val prefix = index.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        for (e in entries) {
            if (!e.name.startsWith(prefix)) continue
            val f = File(out, e.name.removePrefix(prefix))
            if (!f.canonicalPath.startsWith(root)) throw SecurityException("Entri ZIP tidak aman: ${e.name}")
            f.parentFile?.mkdirs()
            zip.getInputStream(e).use { ins -> f.outputStream().use { ins.copyTo(it) } }
        }
        "file://" + File(out, index.removePrefix(prefix)).absolutePath
    }
}

private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
