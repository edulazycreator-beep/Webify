package com.webify.app.ui.studio

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import com.webify.app.ui.components.PrimaryButton
import com.webify.app.ui.components.SecondaryButton
import com.webify.app.ui.theme.Dimens
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webify.app.AppContainer
import com.webify.app.data.db.BuildRecordEntity
import com.webify.app.data.db.KeystoreEntity
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.domain.BuildStatus
import com.webify.app.domain.BuildType
import com.webify.app.domain.OfflineModeType
import com.webify.app.domain.ScreenOrientation
import com.webify.app.domain.SourceType
import com.webify.app.domain.StatusBarStyle
import com.webify.app.domain.WebCacheMode
import com.webify.app.ui.StudioErrors
import com.webify.app.ui.StudioViewModel
import com.webify.app.ui.appViewModel
import com.webify.app.ui.components.CodeEditor
import com.webify.app.ui.components.ColorField
import com.webify.app.ui.components.EmptyState
import com.webify.app.ui.components.LabeledField
import com.webify.app.ui.components.Pill
import com.webify.app.ui.components.SectionCard
import com.webify.app.ui.components.SelectField
import com.webify.app.ui.components.SwitchRow
import com.webify.app.ui.dashboard.ProjectIcon
import com.webify.app.ai.CopilotRequest
import com.webify.app.ai.PromptBuilder
import com.webify.app.ui.theme.WebifyTheme
import com.webify.app.util.FileActions
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

private data class StudioTab(val title: String, val short: String, val icon: ImageVector)

private val TABS = listOf(
    StudioTab("Umum & Branding", "Umum", Icons.Rounded.Palette),
    StudioTab("WebView & Navigasi", "WebView", Icons.Rounded.Language),
    StudioTab("Splash & Offline", "Splash", Icons.Rounded.Smartphone),
    StudioTab("Izin & Code Injection", "Izin & Kode", Icons.Rounded.Code),
    StudioTab("Build & Export", "Build", Icons.Rounded.RocketLaunch)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    projectId: String,
    container: AppContainer,
    onBack: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenCopilot: () -> Unit
) {
    val vm = appViewModel(key = "studio-$projectId") {
        StudioViewModel(projectId, it.projectRepository, it.keystoreRepository, it.buildManager, it.appScope)
    }
    val draft by vm.draft.collectAsStateWithLifecycle()
    val errors by vm.errors.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val keystores by vm.keystores.collectAsStateWithLifecycle()
    val builds by vm.builds.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var savedPage by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = savedPage) { TABS.size }
    LaunchedEffect(pagerState.currentPage) { savedPage = pagerState.currentPage }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reload() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { scope.launch { vm.flush() } }

    val p = draft
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { scope.launch { vm.flush(); onBack() } }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (p != null) { ProjectIcon(p, 32); Spacer(Modifier.width(Dimens.IconTextGap)) }
                        Column {
                            Text(p?.appName ?: "Builder Studio", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (saving) "Menyimpan…" else "Tersimpan otomatis",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (p != null) container.copilotBus.post(
                            CopilotRequest(p.id, PromptBuilder.cssJsInjector("Sarankan perbaikan tampilan mobile untuk website ini (sembunyikan elemen yang tidak perlu di aplikasi seperti banner 'download app', perbesar target sentuh)."), "✨ Saran perbaikan tampilan untuk ${p.appName}")
                        )
                        onOpenCopilot()
                    }) { Icon(Icons.Rounded.AutoAwesome, "AI", tint = MaterialTheme.colorScheme.tertiary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        if (p == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.padding(pad).fillMaxSize()) {
            StudioTabStrip(
                selected = pagerState.currentPage,
                progress = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } }
            )
            val update: ((WebProjectEntity) -> WebProjectEntity) -> Unit = vm::update
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { it }
            ) { page ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding()
                        .padding(horizontal = Dimens.ScreenPadding, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap)
                ) {
                    when (page) {
                        0 -> GeneralTab(p, errors, update, vm)
                        1 -> WebViewTab(p, update)
                        2 -> SplashOfflineTab(p, errors, update)
                        3 -> PermissionsCodeTab(p, update, onAskAi = { instruction ->
                            container.copilotBus.post(CopilotRequest(p.id, PromptBuilder.cssJsInjector(instruction), "🎨 $instruction"))
                            onOpenCopilot()
                        })
                        4 -> BuildTab(
                            p, keystores, builds, errors, update,
                            onBuild = { type -> vm.startBuild(type, onOpenConsole) }
                        )
                    }
                    PageFooter(
                        page = page,
                        onPrev = { scope.launch { pagerState.animateScrollToPage(page - 1) } },
                        onNext = { scope.launch { pagerState.animateScrollToPage(page + 1) } }
                    )
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * Strip tab berbentuk pil (ikon + label) yang bisa di-scroll, dengan indikator progres
 * di bawahnya yang mengikuti geseran pager secara real-time.
 */
@Composable
private fun StudioTabStrip(selected: Int, progress: Float, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { listState.animateScrollToItem(maxOf(0, selected - 1)) }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(TABS) { i, t ->
                val isSel = i == selected
                val bg by animateColorAsState(
                    if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer, label = "tabBg"
                )
                val fg by animateColorAsState(
                    if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "tabFg"
                )
                Surface(
                    onClick = { onSelect(i) },
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    color = bg,
                    border = if (isSel) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.height(40.dp)
                ) {
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(t.icon, null, Modifier.size(18.dp), tint = fg)
                        Spacer(Modifier.width(Dimens.IconTextGapSmall))
                        Text(t.short, style = MaterialTheme.typography.labelLarge, fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium, color = fg, maxLines = 1)
                    }
                }
            }
        }
        // Indikator progres langkah (1/5 … 5/5) mengikuti geseran
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                TABS[selected].title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            Text("${selected + 1}/${TABS.size}", style = WebifyTheme.mono.copy(fontSize = 11.sp, lineHeight = 14.sp), color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding).height(3.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant)) {
            Box(
                Modifier.fillMaxWidth(((progress + 1f) / TABS.size).coerceIn(0f, 1f)).height(3.dp)
                    .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Navigasi Sebelumnya / Berikutnya di bawah setiap halaman. */
@Composable
private fun PageFooter(page: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (page > 0) {
            SecondaryButton("← ${TABS[page - 1].short}", onClick = onPrev, modifier = Modifier.weight(1f))
        } else Spacer(Modifier.weight(1f))
        if (page < TABS.lastIndex) {
            SecondaryButton("${TABS[page + 1].short} →", onClick = onNext, modifier = Modifier.weight(1f), tonal = true)
        } else Spacer(Modifier.weight(1f))
    }
}

// ------------------------------------------------------------------ Tab 1
@Composable
private fun GeneralTab(p: WebProjectEntity, e: StudioErrors, update: ((WebProjectEntity) -> WebProjectEntity) -> Unit, vm: StudioViewModel) {
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let(vm::setIcon) }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(vm::setSiteZip) }

    SectionCard("Sumber Website", Icons.Rounded.Language) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = p.sourceType == SourceType.URL, onClick = { update { it.copy(sourceType = SourceType.URL) } },
                label = { Text("URL Website") }, leadingIcon = { Icon(Icons.Rounded.Link, null, Modifier.size(18.dp)) })
            FilterChip(selected = p.sourceType == SourceType.LOCAL_HTML_ZIP, onClick = { update { it.copy(sourceType = SourceType.LOCAL_HTML_ZIP) } },
                label = { Text("HTML Lokal (ZIP)") }, leadingIcon = { Icon(Icons.Rounded.FolderZip, null, Modifier.size(18.dp)) })
        }
        if (p.sourceType == SourceType.URL) {
            LabeledField("URL target", p.targetUrl, { v -> update { it.copy(targetUrl = v.trim()) } }, error = e.url, mono = true, keyboardType = KeyboardType.Uri)
        } else {
            SecondaryButton(
                if (p.localZipPath == null) "Pilih ZIP (berisi index.html)" else "Ganti ZIP proyek web",
                onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                icon = Icons.Rounded.FolderZip
            )
            p.localZipPath?.let {
                Text(
                    "✓ ${File(it).name} (${FileActions.humanSize(File(it).length())})",
                    style = WebifyTheme.mono.copy(fontSize = 12.sp, lineHeight = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            e.url?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        LabeledField("Domain internal tambahan", p.allowedDomains, { v -> update { it.copy(allowedDomains = v) } }, mono = true,
            supporting = "Pisahkan dengan koma. Domain lain dibuka di browser eksternal.", placeholder = "auth.toko.com, cdn.toko.com")
        LabeledField("Host Deep Link / App Links (opsional)", p.deepLinkHost, { v -> update { it.copy(deepLinkHost = v.trim()) } }, mono = true, placeholder = "toko.com")
    }

    SectionCard("Identitas Aplikasi", Icons.Rounded.Android) {
        LabeledField("Nama aplikasi", p.appName, { v -> update { it.copy(appName = v) } })
        LabeledField("Package name", p.packageName, { v -> update { it.copy(packageName = v.trim().lowercase()) } }, error = e.packageName, mono = true,
            supporting = "Contoh: com.namatoko.app")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
            LabeledField("Version code", p.versionCode.toString(),
                { v -> v.filter(Char::isDigit).take(9).toIntOrNull()?.let { n -> update { it.copy(versionCode = maxOf(1, n)) } } },
                modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number, mono = true)
            LabeledField("Version name", p.versionName, { v -> update { it.copy(versionName = v.trim()) } },
                modifier = Modifier.weight(1f), error = e.versionName, mono = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
            SelectField("minSdk", (21..30).toList(), p.minSdk, { "API $it" }, { v -> update { it.copy(minSdk = v) } }, Modifier.weight(1f))
            SelectField("targetSdk", listOf(33, 34, 35), p.targetSdk, { "API $it" }, { v -> update { it.copy(targetSdk = v) } }, Modifier.weight(1f))
        }
    }

    SectionCard("Ikon & Warna", Icons.Rounded.Brush) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProjectIcon(p, 64)
            Spacer(Modifier.width(Dimens.IconTextGap))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Pilih ikon", onClick = { iconPicker.launch("image/*") }, icon = Icons.Rounded.Image)
                Text("PNG/JPG persegi ≥ 512 px. Otomatis di-resize 48–192 px + adaptive 108dp.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ColorField("Latar ikon adaptif", p.adaptiveBgColor, { v -> update { it.copy(adaptiveBgColor = v) } }, e.colors["adaptiveBgColor"])
        ColorField("Warna utama (primary)", p.primaryColorHex, { v -> update { it.copy(primaryColorHex = v) } }, e.colors["primaryColorHex"])
        ColorField("Warna status bar", p.statusBarColorHex, { v -> update { it.copy(statusBarColorHex = v) } }, e.colors["statusBarColorHex"])
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = p.statusBarStyle == StatusBarStyle.LIGHT_ICONS, onClick = { update { it.copy(statusBarStyle = StatusBarStyle.LIGHT_ICONS) } }, label = { Text("Ikon terang") })
            FilterChip(selected = p.statusBarStyle == StatusBarStyle.DARK_ICONS, onClick = { update { it.copy(statusBarStyle = StatusBarStyle.DARK_ICONS) } }, label = { Text("Ikon gelap") })
        }
        ColorField("Warna navigation bar", p.navigationBarColorHex, { v -> update { it.copy(navigationBarColorHex = v) } }, e.colors["navigationBarColorHex"])
        SelectField("Orientasi layar", ScreenOrientation.entries, p.screenOrientation,
            { when (it) { ScreenOrientation.UNSPECIFIED -> "Otomatis"; ScreenOrientation.PORTRAIT -> "Potret"; ScreenOrientation.LANDSCAPE -> "Lanskap" } },
            { v -> update { it.copy(screenOrientation = v) } })
        SwitchRow("Mode layar penuh (Immersive)", p.fullscreenMode, { v -> update { it.copy(fullscreenMode = v) } },
            "Sembunyikan status & navigation bar", Icons.Rounded.Wallpaper)
    }
}

// ------------------------------------------------------------------ Tab 2
@Composable
private fun WebViewTab(p: WebProjectEntity, update: ((WebProjectEntity) -> WebProjectEntity) -> Unit) {
    SectionCard("Engine WebView", Icons.Rounded.Code) {
        SwitchRow("JavaScript", p.enableJavaScript, { v -> update { it.copy(enableJavaScript = v) } }, "Wajib untuk sebagian besar website & JS Bridge")
        SwitchRow("DOM Storage", p.enableDomStorage, { v -> update { it.copy(enableDomStorage = v) } }, "localStorage / sessionStorage")
        SwitchRow("Kontrol zoom", p.enableZoomControls, { v -> update { it.copy(enableZoomControls = v) } }, icon = Icons.Rounded.ZoomIn)
        SwitchRow("Izinkan mixed content (HTTP)", p.allowMixedContent, { v -> update { it.copy(allowMixedContent = v) } }, "Tidak disarankan untuk produksi", Icons.Rounded.Warning)
        SelectField("Mode cache", WebCacheMode.entries, p.cacheMode,
            { when (it) { WebCacheMode.LOAD_DEFAULT -> "Default (ikuti header HTTP)"; WebCacheMode.LOAD_CACHE_ELSE_NETWORK -> "Cache dulu, lalu jaringan"; WebCacheMode.LOAD_NO_CACHE -> "Tanpa cache" } },
            { v -> update { it.copy(cacheMode = v) } })
        LabeledField("User-Agent kustom (opsional)", p.customUserAgent, { v -> update { it.copy(customUserAgent = v) } }, mono = true, singleLine = false,
            supporting = "Kosong = UA bawaan + \"WebifyApp/versi\"")
    }
    SectionCard("Navigasi & Interaksi", Icons.Rounded.SwipeLeft) {
        SwitchRow("Pull-to-refresh", p.enablePullToRefresh, { v -> update { it.copy(enablePullToRefresh = v) } }, icon = Icons.Rounded.Refresh)
        SwitchRow("Swipe kiri/kanan untuk kembali/maju", p.enableSwipeBackForward, { v -> update { it.copy(enableSwipeBackForward = v) } }, icon = Icons.Rounded.SwipeLeft)
        SwitchRow("Progress bar di atas", p.showTopProgressBar, { v -> update { it.copy(showTopProgressBar = v) } })
        if (p.showTopProgressBar) ColorField("Warna progress bar", p.progressBarColorHex, { v -> update { it.copy(progressBarColorHex = v) } })
        SwitchRow("Routing intent eksternal", p.enableExternalIntentRouting, { v -> update { it.copy(enableExternalIntentRouting = v) } },
            "tel:, mailto:, whatsapp:, tg:, intent:, domain luar → aplikasi/browser", Icons.Rounded.OpenInBrowser)
    }
    SectionCard("Tampilan Halaman", Icons.Rounded.ZoomIn, "Ukuran teks, mode desktop & tema gelap") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ukuran teks", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Pill("${p.textZoomPercent}%", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, mono = true)
        }
        Slider(
            value = p.textZoomPercent.toFloat(),
            onValueChange = { v -> update { it.copy(textZoomPercent = ((v / 10).toInt() * 10).coerceIn(50, 200)) } },
            valueRange = 50f..200f, steps = 14
        )
        SwitchRow("Mode desktop", p.desktopMode, { v -> update { it.copy(desktopMode = v) } },
            "Muat versi desktop website (User-Agent Chrome desktop)", Icons.Rounded.Computer)
        SwitchRow("Paksa mode gelap", p.forceDarkMode, { v -> update { it.copy(forceDarkMode = v) } },
            "Gelapkan website otomatis (Android 10+)", Icons.Rounded.DarkMode)
        SwitchRow("Sembunyikan scrollbar", p.hideScrollbars, { v -> update { it.copy(hideScrollbars = v) } },
            "Tampilan lebih bersih seperti aplikasi native", Icons.Rounded.UnfoldLess)
        SwitchRow("Putar media otomatis", p.autoplayMedia, { v -> update { it.copy(autoplayMedia = v) } },
            "Video/audio boleh diputar tanpa sentuhan", Icons.Rounded.PlayCircle)
    }
    SectionCard("Privasi & Keamanan", Icons.Rounded.Shield, "Iklan, cookie, dan perlindungan layar") {
        SwitchRow("Blokir iklan & pelacak", p.adBlockEnabled, { v -> update { it.copy(adBlockEnabled = v) } },
            "Blokir ±40 jaringan iklan/analitik populer", Icons.Rounded.Block)
        SwitchRow("Cookie pihak ketiga", p.allowThirdPartyCookies, { v -> update { it.copy(allowThirdPartyCookies = v) } },
            "Diperlukan untuk login Google/Facebook tertanam", Icons.Rounded.Cookie)
        SwitchRow("Safe Browsing", p.safeBrowsing, { v -> update { it.copy(safeBrowsing = v) } },
            "Peringatan situs berbahaya dari Google", Icons.Rounded.VerifiedUser)
        SwitchRow("Cegah screenshot & rekam layar", p.preventScreenshots, { v -> update { it.copy(preventScreenshots = v) } },
            "Lindungi konten sensitif (FLAG_SECURE)", Icons.Rounded.NoPhotography)
        SwitchRow("Bersihkan cache saat dibuka", p.clearCacheOnStart, { v -> update { it.copy(clearCacheOnStart = v) } },
            "Selalu muat konten terbaru", Icons.Rounded.CleaningServices)
    }
    SectionCard("Perilaku Aplikasi", Icons.Rounded.TouchApp, "Interaksi & kenyamanan pengguna") {
        SwitchRow("Layar tetap menyala", p.keepScreenOn, { v -> update { it.copy(keepScreenOn = v) } },
            "Cocok untuk dashboard, POS, atau video", Icons.Rounded.LightMode)
        SwitchRow("Konfirmasi sebelum keluar", p.confirmExit, { v -> update { it.copy(confirmExit = v) } },
            "Tekan kembali dua kali untuk keluar", Icons.AutoMirrored.Rounded.ExitToApp)
        SwitchRow("Nonaktifkan seleksi teks", p.disableTextSelection, { v -> update { it.copy(disableTextSelection = v) } },
            "Matikan tekan-lama & salin (kolom input tetap bisa)", Icons.Rounded.TextFields)
    }
    SectionCard("Upload & Unduhan", Icons.Rounded.Download) {
        SwitchRow("Upload file (galeri/kamera)", p.enableFileUploadChooser, { v -> update { it.copy(enableFileUploadChooser = v) } }, icon = Icons.Rounded.Upload)
        SwitchRow("DownloadManager sistem", p.enableDownloadManager, { v -> update { it.copy(enableDownloadManager = v) } },
            "Unduhan dengan notifikasi progres, termasuk blob: & data:", Icons.Rounded.Download)
    }
}

// ------------------------------------------------------------------ Tab 3
@Composable
private fun SplashOfflineTab(p: WebProjectEntity, e: StudioErrors, update: ((WebProjectEntity) -> WebProjectEntity) -> Unit) {
    SectionCard("Splash Screen", Icons.Rounded.Smartphone) {
        SwitchRow("Aktifkan splash", p.enableSplash, { v -> update { it.copy(enableSplash = v) } })
        if (p.enableSplash) {
            ColorField("Warna latar splash", p.splashBgColorHex, { v -> update { it.copy(splashBgColorHex = v) } }, e.colors["splashBgColorHex"])
            Text("Durasi minimum: ${p.splashDurationMs} ms", style = MaterialTheme.typography.labelLarge)
            Slider(
                value = p.splashDurationMs.toFloat(),
                onValueChange = { v -> update { it.copy(splashDurationMs = (v / 100).toInt() * 100) } },
                valueRange = 500f..5000f, steps = 44
            )
            SwitchRow("Tampilkan loader", p.splashShowLoader, { v -> update { it.copy(splashShowLoader = v) } })
        }
    }
    SectionCard("Mode Offline", Icons.Rounded.CloudOff) {
        OfflineModeType.entries.forEach { m ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = Dimens.SettingRowMinHeight)
                    .clip(RoundedCornerShape(Dimens.RadiusSmall))
                    .clickable { update { it.copy(offlineModeType = m) } }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = p.offlineModeType == m, onClick = { update { it.copy(offlineModeType = m) } })
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (m) { OfflineModeType.DEFAULT_MODERN -> "Halaman offline modern"; OfflineModeType.CUSTOM_HTML -> "HTML offline kustom"; OfflineModeType.CACHE_FIRST -> "Cache-first" },
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        when (m) {
                            OfflineModeType.DEFAULT_MODERN -> "Tampilan bawaan dengan tombol \"Coba Lagi\""
                            OfflineModeType.CUSTOM_HTML -> "Gunakan HTML Anda sendiri (link webify://retry untuk muat ulang)"
                            OfflineModeType.CACHE_FIRST -> "Tampilkan versi cache saat offline, fallback ke halaman offline"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (p.offlineModeType == OfflineModeType.CUSTOM_HTML) {
            CodeEditor(
                "HTML offline", p.customOfflineHtml, { v -> update { it.copy(customOfflineHtml = v) } },
                placeholder = "<!DOCTYPE html>\n<html>…<a href=\"webify://retry\">Coba lagi</a>…</html>", minHeight = 220, language = "html"
            )
        }
    }
}

// ------------------------------------------------------------------ Tab 4
@Composable
private fun PermissionsCodeTab(p: WebProjectEntity, update: ((WebProjectEntity) -> WebProjectEntity) -> Unit, onAskAi: (String) -> Unit) {
    var aiInstruction by rememberSaveable { mutableStateOf("") }
    SectionCard("Perizinan Native", Icons.Rounded.Security, "Hanya izin yang aktif yang ditulis ke AndroidManifest.xml") {
        SwitchRow("Kamera", p.permCamera, { v -> update { it.copy(permCamera = v) } }, "Upload foto langsung & WebRTC video", Icons.Rounded.CameraAlt)
        SwitchRow("Mikrofon", p.permMicrophone, { v -> update { it.copy(permMicrophone = v) } }, "WebRTC audio / rekam suara", Icons.Rounded.Mic)
        SwitchRow("Lokasi (GPS)", p.permGeolocation, { v -> update { it.copy(permGeolocation = v) } }, "navigator.geolocation", Icons.Rounded.LocationOn)
        SwitchRow("Notifikasi", p.permNotifications, { v -> update { it.copy(permNotifications = v) } }, "POST_NOTIFICATIONS (Android 13+)", Icons.Rounded.Notifications)
        SwitchRow("Penyimpanan unduhan", p.permStorageDownloads, { v -> update { it.copy(permStorageDownloads = v) } }, "WRITE_EXTERNAL_STORAGE (≤ Android 9)", Icons.Rounded.Download)
    }
    SectionCard("Code Injection", Icons.Rounded.Code, "Diinjeksi ke setiap halaman internal") {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(Dimens.RadiusControl)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
                Text("✨ Smart Injector — jelaskan perubahan yang diinginkan", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                OutlinedTextField(
                    value = aiInstruction, onValueChange = { aiInstruction = it },
                    placeholder = { Text("Sembunyikan banner iklan bawah & perbesar font") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(Dimens.RadiusControl)
                )
                PrimaryButton(
                    "Buat dengan AI", onClick = { onAskAi(aiInstruction) }, enabled = aiInstruction.isNotBlank(),
                    icon = Icons.Rounded.AutoAwesome, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)
                )
            }
        }
        CodeEditor("Custom CSS", p.customCssCode, { v -> update { it.copy(customCssCode = v) } },
            placeholder = ".ads-banner { display: none !important; }", language = "css")
        CodeEditor("JavaScript • onPageStarted", p.customJsOnPageStart, { v -> update { it.copy(customJsOnPageStart = v) } },
            placeholder = "// dijalankan saat halaman mulai dimuat", minHeight = 160, language = "js")
        CodeEditor("JavaScript • onPageFinished", p.customJsOnPageFinished, { v -> update { it.copy(customJsOnPageFinished = v) } },
            placeholder = "document.querySelector('header')?.remove();", language = "js")
    }
}

// ------------------------------------------------------------------ Tab 5
@Composable
private fun BuildTab(
    p: WebProjectEntity,
    keystores: List<KeystoreEntity>,
    builds: List<BuildRecordEntity>,
    errors: StudioErrors,
    update: ((WebProjectEntity) -> WebProjectEntity) -> Unit,
    onBuild: (BuildType) -> Unit
) {
    var target by rememberSaveable { mutableStateOf(BuildType.APK_DEBUG) }
    val selectedKs = keystores.firstOrNull { it.id == p.selectedKeystoreId }
    val ctx = LocalContext.current
    var exportFile by remember { mutableStateOf<File?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val f = exportFile
        if (uri != null && f != null) FileActions.toast(ctx, if (FileActions.exportTo(ctx, f, uri)) "Diekspor: ${f.name}" else "Ekspor gagal")
        exportFile = null
    }

    SectionCard("Target Build", Icons.Rounded.RocketLaunch) {
        BuildTargetOption(BuildType.APK_DEBUG, target, Icons.Rounded.Bolt, "Debug APK", "Cepat untuk uji coba, ditandatangani debug key") { target = it }
        BuildTargetOption(BuildType.APK_RELEASE, target, Icons.Rounded.InstallMobile, "Release APK", "Teroptimasi (D8 release), untuk distribusi langsung") { target = it }
        BuildTargetOption(BuildType.AAB_PLAYSTORE, target, Icons.Rounded.Shop, "Play Store AAB", "Android App Bundle siap unggah ke Google Play Console") { target = it }
    }

    if (target != BuildType.APK_DEBUG) {
        SectionCard("Keystore Penandatanganan", Icons.Rounded.Security) {
            val options: List<KeystoreEntity?> = listOf<KeystoreEntity?>(null) + keystores.filter { !it.isDebugDefault }
            SelectField("Keystore", options, selectedKs, { it?.let { k -> "${k.name} (${k.keyAlias})" } ?: "Debug key (bawaan)" },
                { k -> update { it.copy(selectedKeystoreId = k?.id) } })
            if (selectedKs == null) {
                Text(
                    if (target == BuildType.AAB_PLAYSTORE) "⚠ Google Play menolak AAB bertanda tangan debug. Buat keystore rilis di tab Keystore."
                    else "Tanpa keystore rilis, APK ditandatangani debug key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (target == BuildType.AAB_PLAYSTORE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("SHA-256", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selectedKs.sha256Fingerprint, style = WebifyTheme.mono.copy(fontSize = 11.sp, lineHeight = 16.sp))
            }
        }
    }

    if (errors.hasErrors) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(Dimens.RadiusControl), modifier = Modifier.fillMaxWidth()) {
            Text(
                listOfNotNull(errors.packageName?.let { "Package: $it" }, errors.url?.let { "Sumber: $it" }, errors.versionName?.let { "Versi: $it" },
                    errors.colors.keys.takeIf { it.isNotEmpty() }?.let { "Warna tidak valid: ${it.joinToString()}" }).joinToString("\n"),
                modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall
            )
        }
    }

    PrimaryButton(
        "Start Local Build", onClick = { onBuild(target) }, icon = Icons.Rounded.PlayArrow,
        modifier = Modifier.fillMaxWidth(), enabled = !errors.hasErrors
    )
    SecondaryButton(
        "Quick Build & Test (Debug Key)", onClick = { onBuild(BuildType.APK_DEBUG) }, icon = Icons.Rounded.Bolt,
        modifier = Modifier.fillMaxWidth(), enabled = !errors.hasErrors
    )

    SectionCard("Riwayat Build", Icons.Rounded.Refresh) {
        if (builds.isEmpty()) {
            Text("Belum ada build untuk proyek ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        builds.take(15).forEach { b ->
            BuildRecordRow(b,
                onInstall = { FileActions.installApk(ctx, File(b.outputFilePath!!)) },
                onShare = { FileActions.share(ctx, File(b.outputFilePath!!)) },
                onExport = { exportFile = File(b.outputFilePath!!); exporter.launch(File(b.outputFilePath!!).name) }
            )
        }
    }
}

@Composable
private fun BuildTargetOption(type: BuildType, selected: BuildType, icon: ImageVector, title: String, subtitle: String, onSelect: (BuildType) -> Unit) {
    val isSel = type == selected
    Surface(
        onClick = { onSelect(type) },
        shape = RoundedCornerShape(Dimens.RadiusControl),
        color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(Dimens.IconInline), tint = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Dimens.IconTextGap))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            RadioButton(selected = isSel, onClick = { onSelect(type) })
        }
    }
}

@Composable
private fun BuildRecordRow(b: BuildRecordEntity, onInstall: () -> Unit, onShare: () -> Unit, onExport: () -> Unit) {
    val ok = b.status == BuildStatus.SUCCESS
    val exists = b.outputFilePath != null && File(b.outputFilePath).exists()
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.SettingRowMinHeight), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null,
            tint = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error, modifier = Modifier.size(Dimens.IconInline))
        Spacer(Modifier.width(Dimens.IconTextGap))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.buildType.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.width(Dimens.IconTextGapSmall))
                if (ok) Pill(FileActions.humanSize(b.fileSizeBytes), MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurfaceVariant, mono = true)
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(b.timestamp)) + " • " + "%.1f s".format(b.durationMs / 1000.0) +
                    (b.errorSummary?.let { " • $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        }
        if (ok && exists) {
            if (b.buildType != BuildType.AAB_PLAYSTORE) {
                IconButton(onClick = onInstall, modifier = Modifier.size(Dimens.IconButtonSmall)) { Icon(Icons.Rounded.InstallMobile, "Install", Modifier.size(Dimens.IconInline)) }
            }
            IconButton(onClick = onShare, modifier = Modifier.size(Dimens.IconButtonSmall)) { Icon(Icons.Rounded.Share, "Bagikan", Modifier.size(Dimens.IconInline)) }
            IconButton(onClick = onExport, modifier = Modifier.size(Dimens.IconButtonSmall)) { Icon(Icons.Rounded.Download, "Ekspor", Modifier.size(Dimens.IconInline)) }
        }
    }
}

// ------------------------------------------------------------------ Picker (tab Studio tanpa proyek aktif)
@Composable
fun StudioPickerScreen(container: AppContainer, onOpenProject: (String) -> Unit) {
    val projects by container.projectRepository.observeAll().collectAsStateWithLifecycle(initialValue = null)
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        val list = projects
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Rounded.Code, "Builder Studio", "Buat proyek di tab Projects untuk mulai mengedit.")
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(pad),
                contentPadding = PaddingValues(Dimens.ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap)
            ) {
                item {
                    Text("Builder Studio", style = MaterialTheme.typography.titleLarge)
                    Text("Pilih proyek untuk dibuka", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                }
                items(list, key = { it.id }) { p ->
                    Surface(
                        onClick = { onOpenProject(p.id) },
                        shape = RoundedCornerShape(Dimens.RadiusCard),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp)
                    ) {
                        Row(Modifier.padding(Dimens.CardPadding), verticalAlignment = Alignment.CenterVertically) {
                            ProjectIcon(p, 44)
                            Spacer(Modifier.width(Dimens.IconTextGap))
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(p.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(p.packageName, style = WebifyTheme.mono.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Pill("v${p.versionName}", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, mono = true)
                        }
                    }
                }
            }
        }
    }
}
