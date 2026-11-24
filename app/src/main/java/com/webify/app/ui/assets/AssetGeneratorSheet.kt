package com.webify.app.ui.assets

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Panorama
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webify.app.AppContainer
import com.webify.app.ai.AiMessage
import com.webify.app.ai.PromptBuilder
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.domain.ChatRole
import com.webify.app.domain.SourceType
import com.webify.app.tools.PlayStoreAssetGenerator
import com.webify.app.ui.components.EmptyState
import com.webify.app.ui.components.LabeledField
import com.webify.app.ui.components.PrimaryButton
import com.webify.app.ui.components.SecondaryButton
import com.webify.app.ui.components.SectionCard
import com.webify.app.ui.theme.Dimens
import com.webify.app.util.FileActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Play Store Graphic Studio: hi-res icon 512, feature graphic 1024×500, dan screenshot
 * berbingkai 1080×1920 dari WebView pratinjau. Semua disimpan ke Pictures/Webify_Assets/{appName}/.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetGeneratorScreen(projectId: String, container: AppContainer, onBack: () -> Unit) {
    val project by container.projectRepository.observe(projectId).collectAsStateWithLifecycle(initialValue = null)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var tagline by remember { mutableStateOf("") }
    var headline by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf<Bitmap?>(null) }
    var feature by remember { mutableStateOf<Bitmap?>(null) }
    val shots = remember { mutableStateListOf<Bitmap>() }
    var aiBusy by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val p = project
    LaunchedEffect(p?.updatedAt) {
        val proj = p ?: return@LaunchedEffect
        if (headline.isBlank()) headline = proj.appName
        icon = withContext(Dispatchers.Default) { PlayStoreAssetGenerator.hiResIcon(proj) }
    }
    LaunchedEffect(p?.updatedAt, tagline) {
        val proj = p ?: return@LaunchedEffect
        feature = withContext(Dispatchers.Default) { PlayStoreAssetGenerator.featureGraphic(proj, tagline) }
    }

    fun saveAll() {
        val proj = p ?: return
        scope.launch {
            saving = true
            try {
                val count = withContext(Dispatchers.IO) {
                    var n = 0
                    icon?.let { PlayStoreAssetGenerator.savePng(ctx, it, proj.appName, "icon-512.png"); n++ }
                    feature?.let { PlayStoreAssetGenerator.savePng(ctx, it, proj.appName, "feature-graphic-1024x500.png"); n++ }
                    shots.forEachIndexed { i, b -> PlayStoreAssetGenerator.savePng(ctx, b, proj.appName, "screenshot-${i + 1}.png"); n++ }
                    n
                }
                snackbar.showSnackbar("$count aset disimpan ke Pictures/Webify_Assets/${proj.appName}")
            } catch (e: Exception) {
                snackbar.showSnackbar("Gagal menyimpan: ${e.message}")
            } finally {
                saving = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", Modifier.size(Dimens.IconBar)) } },
                title = {
                    Column {
                        Text("Play Store Graphic Studio", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(p?.appName ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (p != null) {
                Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Dimens.ScreenPadding, vertical = 10.dp)) {
                        PrimaryButton(
                            if (saving) "Menyimpan…" else "Simpan Semua (${2 + shots.size} aset)",
                            onClick = ::saveAll, icon = Icons.Rounded.Download, enabled = !saving, modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        if (p == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap)
        ) {
            // 1. Ikon hi-res
            SectionCard("Hi-Res App Icon", Icons.Rounded.Image, "512 × 512 PNG • full-bleed, Play menerapkan mask") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon?.let {
                        Image(it.asImageBitmap(), "Ikon 512", Modifier.size(112.dp).clip(RoundedCornerShape(24.dp)))
                    } ?: Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    Spacer(Modifier.width(Dimens.IconTextGap))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Dirender dari ikon proyek di atas latar adaptif.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SecondaryButton("Bagikan", onClick = {
                            icon?.let { FileActions.share(ctx, PlayStoreAssetGenerator.cacheCopy(ctx, it, "icon-512.png")) }
                        }, icon = Icons.Rounded.Download)
                    }
                }
            }

            // 2. Feature graphic
            SectionCard("Feature Graphic", Icons.Rounded.Panorama, "1024 × 500 PNG • banner listing Play Store") {
                feature?.let {
                    Image(it.asImageBitmap(), "Feature graphic", Modifier.fillMaxWidth().aspectRatio(1024f / 500f).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit)
                }
                LabeledField("Tagline", tagline, { tagline = it.take(70) }, placeholder = "Belanja hemat langsung dari genggaman",
                    supporting = "${tagline.length}/70")
                SecondaryButton(
                    if (aiBusy) "AI menulis…" else "Isi tagline dengan AI",
                    onClick = {
                        scope.launch {
                            aiBusy = true
                            try {
                                val sb = StringBuilder()
                                container.aiService.stream(
                                    PromptBuilder.system(p),
                                    listOf(AiMessage(ChatRole.USER, "Tulis SATU tagline promosi Play Store berbahasa Indonesia untuk aplikasi ini, maksimal 60 karakter, tanpa tanda kutip, tanpa penjelasan."))
                                ).collect { sb.append(it) }
                                tagline = sb.toString().lines().firstOrNull { it.isNotBlank() }?.trim()?.trim('"', '“', '”', '*', '-')?.take(70) ?: tagline
                            } catch (e: Exception) {
                                snackbar.showSnackbar("AI: ${e.message}")
                            } finally {
                                aiBusy = false
                            }
                        }
                    },
                    icon = Icons.Rounded.AutoAwesome, enabled = !aiBusy, modifier = Modifier.fillMaxWidth()
                )
            }

            // 3. Screenshot berbingkai
            SectionCard("Screenshot Ponsel", Icons.Rounded.PhoneAndroid, "1080 × 1920 • navigasikan halaman lalu ambil gambar") {
                LabeledField("Judul promosi", headline, { headline = it.take(60) }, placeholder = "Belanja lebih cepat")
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(0.72f).align(Alignment.CenterHorizontally).aspectRatio(9f / 16f)
                ) {
                    CaptureWebView(p) { webView = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("Muat ulang", onClick = { webView?.reload() }, icon = Icons.Rounded.Refresh, modifier = Modifier.weight(1f))
                    PrimaryButton("Ambil Screenshot", onClick = {
                        val wv = webView ?: return@PrimaryButton
                        if (wv.width == 0 || wv.height == 0) return@PrimaryButton
                        val capture = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
                        wv.draw(Canvas(capture))
                        val proj = p
                        scope.launch {
                            val framed = withContext(Dispatchers.Default) { PlayStoreAssetGenerator.framedScreenshot(proj, capture, headline) }
                            if (shots.size >= 8) shots.removeAt(0)
                            shots.add(framed)
                        }
                    }, icon = Icons.Rounded.AddAPhoto, modifier = Modifier.weight(1f))
                }
                if (shots.isEmpty()) {
                    Text("Play Console membutuhkan minimal 2 screenshot ponsel.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(shots) { i, b ->
                            Box {
                                Image(b.asImageBitmap(), "Screenshot ${i + 1}", Modifier.height(220.dp).aspectRatio(1080f / 1920f).clip(RoundedCornerShape(10.dp)))
                                IconButton(
                                    onClick = { shots.removeAt(i) },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp)
                                        .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                ) { Icon(Icons.Rounded.Close, "Hapus", Modifier.size(16.dp)) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CaptureWebView(p: WebProjectEntity, onReady: (WebView) -> Unit) {
    val ctx = LocalContext.current
    val url = remember(p.id) {
        if (p.sourceType == SourceType.URL) p.targetUrl
        else File(ctx.filesDir, "preview/${p.id}").walkTopDown().firstOrNull { it.name.equals("index.html", true) }
            ?.let { "file://${it.absolutePath}" }
    }
    if (url == null) {
        EmptyState(Icons.Rounded.PhoneAndroid, "Buka Preview dulu", "Untuk proyek ZIP, jalankan Preview sekali agar situs diekstrak.")
        return
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = p.enableJavaScript
                settings.domStorageEnabled = p.enableDomStorage
                settings.allowFileAccess = p.sourceType == SourceType.LOCAL_HTML_ZIP
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webViewClient = WebViewClient()
                onReady(this)
                loadUrl(url)
            }
        }
    )
}
