package com.webify.app.ui.studio

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NoPhotography
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.webify.app.AppContainer
import com.webify.app.ai.CopilotRequest
import com.webify.app.ai.PromptBuilder
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
import com.webify.app.ui.components.ColorSelectorTile
import com.webify.app.ui.components.EmptyState
import com.webify.app.ui.components.IconBadge
import com.webify.app.ui.components.LabeledField
import com.webify.app.ui.components.LocalDockInset
import com.webify.app.ui.components.Pill
import com.webify.app.ui.components.SecondaryButton
import com.webify.app.ui.components.SectionCard
import com.webify.app.ui.components.SelectField
import com.webify.app.ui.components.SwitchRow
import com.webify.app.ui.components.parseHexColor
import com.webify.app.ui.dashboard.ProjectIcon
import com.webify.app.ui.theme.Dimens
import com.webify.app.ui.theme.WebifyTheme
import com.webify.app.util.FileActions
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date

private typealias Updater = ((WebProjectEntity) -> WebProjectEntity) -> Unit

private data class StudioTab(val title: String, val short: String, val icon: ImageVector)

private val TABS = listOf(
    StudioTab("Identitas aplikasi", "Identitas", Icons.Rounded.Badge),
    StudioTab("Tampilan & tema", "Tampilan", Icons.Rounded.Palette),
    StudioTab("WebView & offline", "WebView", Icons.Rounded.Language),
    StudioTab("Izin & injeksi kode", "Izin & Kode", Icons.Rounded.Code),
    StudioTab("Build & signing", "Build", Icons.Rounded.RocketLaunch)
)

// =============================================================================
// Layar utama Builder Studio
// =============================================================================

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
    var target by rememberSaveable { mutableStateOf(BuildType.APK_DEBUG) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.reload() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { scope.launch { vm.flush() } }

    val p = draft
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { scope.launch { vm.flush(); onBack() } }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", Modifier.size(Dimens.IconBar))
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (p != null) { ProjectIcon(p, 32); Spacer(Modifier.width(Dimens.IconTextGap)) }
                        Column {
                            Text(p?.appName ?: "Builder Studio", style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (saving) "Menyimpan…" else "Tersimpan otomatis",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (p != null) container.copilotBus.post(
                            CopilotRequest(
                                p.id,
                                PromptBuilder.cssJsInjector("Sarankan perbaikan tampilan mobile untuk website ini (sembunyikan elemen yang tidak perlu di aplikasi seperti banner 'download app', perbesar target sentuh)."),
                                "✨ Saran perbaikan tampilan untuk ${p.appName}"
                            )
                        )
                        onOpenCopilot()
                    }) { Icon(Icons.Rounded.AutoAwesome, "AI Co-Pilot", Modifier.size(Dimens.IconBar), tint = MaterialTheme.colorScheme.tertiary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (p != null) {
                StickyActionBar(
                    saving = saving,
                    target = target,
                    enabled = !errors.hasErrors,
                    onSave = {
                        scope.launch {
                            vm.flush()
                            snackbar.showSnackbar("Perubahan disimpan")
                        }
                    },
                    onBuild = { vm.startBuild(target, onOpenConsole) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        if (p == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val update: Updater = vm::update
        Column(Modifier.fillMaxSize().padding(pad)) {
            PillTabRow(
                pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                selected = pagerState.targetPage,
                onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i, animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f)) } }
            )
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
                        0 -> IdentityPage(p, errors, update, vm)
                        1 -> AppearancePage(p, errors, update)
                        2 -> WebViewPage(p, update)
                        3 -> PermissionsCodePage(p, update, onAskAi = { instruction ->
                            container.copilotBus.post(CopilotRequest(p.id, PromptBuilder.cssJsInjector(instruction), "🎨 $instruction"))
                            onOpenCopilot()
                        })
                        4 -> BuildPage(p, keystores, builds, errors, target, onTarget = { target = it }, update = update,
                            onQuickBuild = { vm.startBuild(BuildType.APK_DEBUG, onOpenConsole) })
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// =============================================================================
// Tab pil dengan indikator kapsul yang meluncur (sinkron dua arah dengan pager)
// =============================================================================

@Composable
private fun PillTabRow(pagePosition: Float, selected: Int, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    // posisi (x) & lebar tiap tab dalam dp, diisi saat layout
    val xs = remember { mutableStateListOf(*Array(TABS.size) { 0.dp }) }
    val ws = remember { mutableStateListOf(*Array(TABS.size) { 0.dp }) }

    // Interpolasi posisi indikator mengikuti jari saat swipe; animateDpAsState memberi efek pegas saat mengetuk.
    val base = pagePosition.toInt().coerceIn(0, TABS.lastIndex)
    val next = (base + 1).coerceAtMost(TABS.lastIndex)
    val frac = (pagePosition - base).coerceIn(0f, 1f)
    val targetX = lerp(xs[base], xs[next], frac)
    val targetW = lerp(ws[base], ws[next], frac)
    val indicatorX by animateDpAsState(targetX, spring(dampingRatio = 0.8f, stiffness = 700f), label = "indX")
    val indicatorW by animateDpAsState(targetW, spring(dampingRatio = 0.8f, stiffness = 700f), label = "indW")

    LaunchedEffect(selected, xs[selected]) {
        val px = with(density) { (xs[selected] - 48.dp).toPx() }.toInt().coerceAtLeast(0)
        scroll.animateScrollTo(px)
    }

    Box(
        Modifier.fillMaxWidth().horizontalScroll(scroll)
            .padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Box(Modifier.padding(4.dp)) {
                // Kapsul indikator
                if (indicatorW > 0.dp) {
                    Box(
                        Modifier.offset(x = indicatorX).width(indicatorW).height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
                Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    TABS.forEachIndexed { i, t ->
                        val emphasis = (1f - kotlin.math.abs(pagePosition - i)).coerceIn(0f, 1f)
                        val fg by animateColorAsState(
                            if (emphasis > 0.5f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            label = "tabFg$i"
                        )
                        Row(
                            Modifier
                                .onGloballyPositioned { c ->
                                    with(density) {
                                        xs[i] = c.positionInParent().x.toDp()
                                        ws[i] = c.size.width.toDp()
                                    }
                                }
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onSelect(i) }
                                .height(40.dp)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(t.icon, null, Modifier.size(18.dp), tint = fg)
                            Spacer(Modifier.width(Dimens.IconTextGapSmall))
                            Text(
                                t.short, style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (emphasis > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
                                color = fg, maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// Bar aksi bawah (selalu terjangkau ibu jari)
// =============================================================================

@Composable
private fun StickyActionBar(saving: Boolean, target: BuildType, enabled: Boolean, onSave: () -> Unit, onBuild: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Dimens.ScreenPadding, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier.weight(0.38f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.Save, null, Modifier.size(Dimens.IconInline))
                    Spacer(Modifier.width(Dimens.IconTextGapSmall))
                    Text("Simpan", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Button(
                    onClick = onBuild,
                    enabled = enabled,
                    modifier = Modifier.weight(0.62f).height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, Modifier.size(Dimens.IconInline))
                    Spacer(Modifier.width(Dimens.IconTextGapSmall))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text("Build Sekarang", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(target.label, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// =============================================================================
// Komponen bantu: accordion, tile izin, editor layar penuh
// =============================================================================

/** Kartu yang bisa dibuka-tutup dengan animasi expand/shrink + chevron berputar. */
@Composable
private fun AccordionCard(
    title: String,
    icon: ImageVector,
    subtitle: String,
    initiallyExpanded: Boolean = false,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, spring(stiffness = 400f), label = "chev")
    Surface(
        shape = RoundedCornerShape(Dimens.RadiusCard),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(Dimens.CardPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(icon, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(Dimens.IconTextGap))
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (badge != null) {
                    Pill(badge, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(Icons.Rounded.ExpandMore, if (expanded) "Tutup" else "Buka", Modifier.size(Dimens.IconBar).rotate(rotation))
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(spring(dampingRatio = 0.9f, stiffness = 380f)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = 500f)) + fadeOut()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(start = Dimens.CardPadding, end = Dimens.CardPadding, bottom = Dimens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    content()
                }
            }
        }
    }
}

/** Tile izin native yang bisa diketuk (dipakai dalam grid 2 kolom). */
@Composable
private fun PermissionTile(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, label = "permBg"
    )
    val fg by animateColorAsState(
        if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, label = "permFg"
    )
    Surface(
        onClick = { onChange(!checked) },
        shape = RoundedCornerShape(Dimens.RadiusControl),
        color = bg,
        border = BorderStroke(1.dp, if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxHeight()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(Dimens.IconInline), tint = fg)
                Spacer(Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.height(24.dp))
            }
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, style = MaterialTheme.typography.bodySmall, color = fg.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Editor kode dengan tombol expand ke mode layar penuh. */
@Composable
private fun ExpandableCodeCard(
    title: String,
    language: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit
) {
    var fullscreen by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(Dimens.RadiusCard),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (value.isBlank()) "Kosong" else "${value.lines().size} baris • ${value.length} karakter",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { fullscreen = true }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.Fullscreen, "Layar penuh", Modifier.size(Dimens.IconBar))
                }
            }
            CodeEditor("Editor", value, onChange, placeholder = placeholder, minHeight = 160, language = language)
        }
    }
    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding().padding(top = 24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { fullscreen = false }) { Icon(Icons.Rounded.Close, "Tutup", Modifier.size(Dimens.IconBar)) }
                        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Pill(language.uppercase(), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, mono = true)
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Dimens.ScreenPadding)) {
                        CodeEditor("Editor", value, onChange, placeholder = placeholder, minHeight = 480, language = language)
                    }
                }
            }
        }
    }
}

// =============================================================================
// 1. Identitas
// =============================================================================

@Composable
private fun IdentityPage(p: WebProjectEntity, e: StudioErrors, update: Updater, vm: StudioViewModel) {
    val iconPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let(vm::setIcon) }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> uri?.let(vm::setSiteZip) }

    SectionCard("Aplikasi", Icons.Rounded.Android, "Nama, package, dan versi") {
        LabeledField("Nama aplikasi", p.appName, { v -> update { it.copy(appName = v) } })
        LabeledField(
            "Package name", p.packageName, { v -> update { it.copy(packageName = v.trim().lowercase()) } },
            error = e.packageName, mono = true, supporting = "Contoh: com.namatoko.app — tidak bisa diubah setelah rilis"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
            LabeledField(
                "Version code", p.versionCode.toString(),
                { v -> v.filter(Char::isDigit).take(9).toIntOrNull()?.let { n -> update { it.copy(versionCode = maxOf(1, n)) } } },
                modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number, mono = true
            )
            LabeledField(
                "Version name", p.versionName, { v -> update { it.copy(versionName = v.trim()) } },
                modifier = Modifier.weight(1f), error = e.versionName, mono = true
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
            SelectField("minSdk", (21..30).toList(), p.minSdk, { "API $it" }, { v -> update { it.copy(minSdk = v) } }, Modifier.weight(1f))
            SelectField("targetSdk", listOf(33, 34, 35), p.targetSdk, { "API $it" }, { v -> update { it.copy(targetSdk = v) } }, Modifier.weight(1f))
        }
    }

    SectionCard("Sumber Website", Icons.Rounded.Language, "URL online atau proyek HTML lokal") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = p.sourceType == SourceType.URL, onClick = { update { it.copy(sourceType = SourceType.URL) } },
                label = { Text("URL Website") }, leadingIcon = { Icon(Icons.Rounded.Link, null, Modifier.size(Dimens.IconChip)) }
            )
            FilterChip(
                selected = p.sourceType == SourceType.LOCAL_HTML_ZIP, onClick = { update { it.copy(sourceType = SourceType.LOCAL_HTML_ZIP) } },
                label = { Text("HTML Lokal (ZIP)") }, leadingIcon = { Icon(Icons.Rounded.FolderZip, null, Modifier.size(Dimens.IconChip)) }
            )
        }
        if (p.sourceType == SourceType.URL) {
            LabeledField("URL target", p.targetUrl, { v -> update { it.copy(targetUrl = v.trim()) } }, error = e.url, mono = true, keyboardType = KeyboardType.Uri)
        } else {
            SecondaryButton(
                if (p.localZipPath == null) "Pilih ZIP (berisi index.html)" else "Ganti ZIP proyek web",
                onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                icon = Icons.Rounded.FolderZip, modifier = Modifier.fillMaxWidth()
            )
            p.localZipPath?.let {
                Text(
                    "✓ ${File(it).name} (${FileActions.humanSize(File(it).length())})",
                    style = WebifyTheme.mono.copy(fontSize = 12.sp, lineHeight = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            e.url?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
        LabeledField(
            "Domain internal tambahan", p.allowedDomains, { v -> update { it.copy(allowedDomains = v) } }, mono = true,
            supporting = "Pisahkan dengan koma. Domain lain dibuka di browser.", placeholder = "auth.toko.com, cdn.toko.com"
        )
        LabeledField("Deep Link / App Links (opsional)", p.deepLinkHost, { v -> update { it.copy(deepLinkHost = v.trim()) } }, mono = true, placeholder = "toko.com")
    }

    SectionCard("Ikon Aplikasi", Icons.Rounded.Image, "Pratinjau adaptive icon (bulat & kotak)") {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AdaptiveIconPreview(p, CircleShape, "Bulat")
            AdaptiveIconPreview(p, RoundedCornerShape(18.dp), "Kotak")
            AdaptiveIconPreview(p, RoundedCornerShape(8.dp), "Squircle", size = 56.dp)
        }
        SecondaryButton("Pilih gambar ikon", onClick = { iconPicker.launch("image/*") }, icon = Icons.Rounded.Image, modifier = Modifier.fillMaxWidth())
        Text(
            "PNG/JPG persegi ≥ 512 px. Otomatis di-resize 48–192 px + lapisan adaptif 108dp.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ColorSelectorTile("Latar ikon adaptif", p.adaptiveBgColor, { v -> update { it.copy(adaptiveBgColor = v) } }, error = e.colors["adaptiveBgColor"])
    }
}

/** Ikon pada latar adaptif dengan safe-zone 72/108 seperti launcher Android. */
@Composable
private fun AdaptiveIconPreview(p: WebProjectEntity, shape: androidx.compose.ui.graphics.Shape, label: String, size: Dp = 64.dp) {
    val bg = parseHexColor(p.adaptiveBgColor) ?: Color.White
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(size).clip(shape).background(bg)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
            contentAlignment = Alignment.Center
        ) {
            val path = p.iconUri
            val inner = size * (72f / 108f)
            if (path != null && File(path).exists()) {
                AsyncImage(model = File(path), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(inner).clip(RoundedCornerShape(6.dp)))
            } else {
                val fg = parseHexColor(p.primaryColorHex) ?: MaterialTheme.colorScheme.primary
                Box(Modifier.size(inner).clip(RoundedCornerShape(8.dp)).background(fg), contentAlignment = Alignment.Center) {
                    Text(p.appName.trim().firstOrNull()?.uppercase() ?: "W", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// =============================================================================
// 2. Tampilan & Tema
// =============================================================================

@Composable
private fun AppearancePage(p: WebProjectEntity, e: StudioErrors, update: Updater) {
    SectionCard("Warna Tema", Icons.Rounded.Palette, "Ketuk untuk membuka color picker") {
        ColorSelectorTile("Warna utama", p.primaryColorHex, { v -> update { it.copy(primaryColorHex = v) } }, "aksen & loader", e.colors["primaryColorHex"])
        ColorSelectorTile("Status bar", p.statusBarColorHex, { v -> update { it.copy(statusBarColorHex = v) } }, null, e.colors["statusBarColorHex"])
        ColorSelectorTile("Navigation bar", p.navigationBarColorHex, { v -> update { it.copy(navigationBarColorHex = v) } }, null, e.colors["navigationBarColorHex"])
        ColorSelectorTile("Progress bar", p.progressBarColorHex, { v -> update { it.copy(progressBarColorHex = v) } }, null, e.colors["progressBarColorHex"])
    }

    SectionCard("Status Bar & Layar", Icons.Rounded.Smartphone) {
        Text("Gaya ikon status bar", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = p.statusBarStyle == StatusBarStyle.LIGHT_ICONS, onClick = { update { it.copy(statusBarStyle = StatusBarStyle.LIGHT_ICONS) } },
                label = { Text("Ikon terang") }, leadingIcon = { Icon(Icons.Rounded.LightMode, null, Modifier.size(Dimens.IconChip)) }
            )
            FilterChip(
                selected = p.statusBarStyle == StatusBarStyle.DARK_ICONS, onClick = { update { it.copy(statusBarStyle = StatusBarStyle.DARK_ICONS) } },
                label = { Text("Ikon gelap") }, leadingIcon = { Icon(Icons.Rounded.DarkMode, null, Modifier.size(Dimens.IconChip)) }
            )
        }
        SelectField(
            "Orientasi layar", ScreenOrientation.entries, p.screenOrientation,
            { when (it) { ScreenOrientation.UNSPECIFIED -> "Otomatis"; ScreenOrientation.PORTRAIT -> "Potret"; ScreenOrientation.LANDSCAPE -> "Lanskap" } },
            { v -> update { it.copy(screenOrientation = v) } }
        )
        SwitchRow("Layar penuh (Immersive)", p.fullscreenMode, { v -> update { it.copy(fullscreenMode = v) } }, "Sembunyikan status & navigation bar", Icons.Rounded.Wallpaper)
    }

    SectionCard("Splash Screen", Icons.Rounded.RocketLaunch, "Layar pembuka saat aplikasi dimulai") {
        SwitchRow("Aktifkan splash", p.enableSplash, { v -> update { it.copy(enableSplash = v) } })
        AnimatedVisibility(visible = p.enableSplash, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
                ColorSelectorTile("Latar splash", p.splashBgColorHex, { v -> update { it.copy(splashBgColorHex = v) } }, null, e.colors["splashBgColorHex"])
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Durasi minimum", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(end = 12.dp))
                    Pill("${p.splashDurationMs} ms", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, mono = true)
                }
                Slider(
                    value = p.splashDurationMs.toFloat(),
                    onValueChange = { v -> update { it.copy(splashDurationMs = (v / 100).toInt() * 100) } },
                    valueRange = 500f..5000f, steps = 44
                )
                SwitchRow("Tampilkan loader", p.splashShowLoader, { v -> update { it.copy(splashShowLoader = v) } })
            }
        }
    }
}

// =============================================================================
// 3. WebView & Offline
// =============================================================================

@Composable
private fun WebViewPage(p: WebProjectEntity, update: Updater) {
    SectionCard("Fitur Utama", Icons.Rounded.Settings, "Sakelar yang paling sering dipakai") {
        SwitchRow("JavaScript", p.enableJavaScript, { v -> update { it.copy(enableJavaScript = v) } }, "Wajib untuk sebagian besar website & JS Bridge", Icons.Rounded.Code)
        SwitchRow("DOM Storage", p.enableDomStorage, { v -> update { it.copy(enableDomStorage = v) } }, "localStorage / sessionStorage")
        SwitchRow("Pull-to-refresh", p.enablePullToRefresh, { v -> update { it.copy(enablePullToRefresh = v) } }, icon = Icons.Rounded.Refresh)
        SwitchRow("Swipe kembali/maju", p.enableSwipeBackForward, { v -> update { it.copy(enableSwipeBackForward = v) } }, icon = Icons.Rounded.SwipeLeft)
        SwitchRow("Progress bar atas", p.showTopProgressBar, { v -> update { it.copy(showTopProgressBar = v) } })
        SwitchRow("Kontrol zoom", p.enableZoomControls, { v -> update { it.copy(enableZoomControls = v) } }, icon = Icons.Rounded.ZoomIn)
        SwitchRow("Upload file (galeri/kamera)", p.enableFileUploadChooser, { v -> update { it.copy(enableFileUploadChooser = v) } }, icon = Icons.Rounded.Upload)
        SwitchRow("DownloadManager sistem", p.enableDownloadManager, { v -> update { it.copy(enableDownloadManager = v) } }, "Termasuk unduhan blob: & data:", Icons.Rounded.Download)
        SwitchRow("Routing intent eksternal", p.enableExternalIntentRouting, { v -> update { it.copy(enableExternalIntentRouting = v) } },
            "tel:, mailto:, whatsapp:, tg:, intent:", Icons.Rounded.OpenInBrowser)
    }

    val displayOn = listOf(p.desktopMode, p.forceDarkMode, p.hideScrollbars, p.textZoomPercent != 100).count { it }
    AccordionCard("Tampilan Halaman", Icons.Rounded.ZoomIn, "Ukuran teks, mode desktop, tema gelap", badge = displayOn.takeIf { it > 0 }?.let { "$it aktif" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ukuran teks", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(end = 12.dp))
            Pill("${p.textZoomPercent}%", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, mono = true)
        }
        Slider(
            value = p.textZoomPercent.toFloat(),
            onValueChange = { v -> update { it.copy(textZoomPercent = ((v / 10).toInt() * 10).coerceIn(50, 200)) } },
            valueRange = 50f..200f, steps = 14
        )
        SwitchRow("Mode desktop", p.desktopMode, { v -> update { it.copy(desktopMode = v) } }, "User-Agent Chrome desktop", Icons.Rounded.Computer)
        SwitchRow("Paksa mode gelap", p.forceDarkMode, { v -> update { it.copy(forceDarkMode = v) } }, "Android 10+", Icons.Rounded.DarkMode)
        SwitchRow("Sembunyikan scrollbar", p.hideScrollbars, { v -> update { it.copy(hideScrollbars = v) } }, icon = Icons.Rounded.UnfoldLess)
        SwitchRow("Putar media otomatis", p.autoplayMedia, { v -> update { it.copy(autoplayMedia = v) } }, icon = Icons.Rounded.PlayCircle)
    }

    val privacyOn = listOf(p.adBlockEnabled, p.preventScreenshots, p.clearCacheOnStart, !p.allowThirdPartyCookies).count { it }
    AccordionCard("Privasi & Keamanan", Icons.Rounded.Shield, "Iklan, cookie, screenshot", badge = privacyOn.takeIf { it > 0 }?.let { "$it aktif" }) {
        SwitchRow("Blokir iklan & pelacak", p.adBlockEnabled, { v -> update { it.copy(adBlockEnabled = v) } }, "±40 jaringan iklan/analitik", Icons.Rounded.Block)
        SwitchRow("Cookie pihak ketiga", p.allowThirdPartyCookies, { v -> update { it.copy(allowThirdPartyCookies = v) } }, "Untuk login Google/Facebook tertanam", Icons.Rounded.Cookie)
        SwitchRow("Safe Browsing", p.safeBrowsing, { v -> update { it.copy(safeBrowsing = v) } }, icon = Icons.Rounded.VerifiedUser)
        SwitchRow("Cegah screenshot", p.preventScreenshots, { v -> update { it.copy(preventScreenshots = v) } }, "FLAG_SECURE", Icons.Rounded.NoPhotography)
        SwitchRow("Bersihkan cache saat dibuka", p.clearCacheOnStart, { v -> update { it.copy(clearCacheOnStart = v) } }, icon = Icons.Rounded.CleaningServices)
        SwitchRow("Izinkan mixed content (HTTP)", p.allowMixedContent, { v -> update { it.copy(allowMixedContent = v) } }, "Tidak disarankan untuk produksi", Icons.Rounded.Warning)
    }

    val behaviorOn = listOf(p.keepScreenOn, p.confirmExit, p.disableTextSelection).count { it }
    AccordionCard("Perilaku Aplikasi", Icons.Rounded.TouchApp, "Layar menyala, konfirmasi keluar, seleksi teks", badge = behaviorOn.takeIf { it > 0 }?.let { "$it aktif" }) {
        SwitchRow("Layar tetap menyala", p.keepScreenOn, { v -> update { it.copy(keepScreenOn = v) } }, "Dashboard, POS, video", Icons.Rounded.LightMode)
        SwitchRow("Konfirmasi sebelum keluar", p.confirmExit, { v -> update { it.copy(confirmExit = v) } }, "Tekan kembali dua kali", Icons.AutoMirrored.Rounded.ExitToApp)
        SwitchRow("Nonaktifkan seleksi teks", p.disableTextSelection, { v -> update { it.copy(disableTextSelection = v) } }, "Kolom input tetap bisa", Icons.Rounded.TextFields)
    }

    AccordionCard("Lanjutan", Icons.Rounded.Code, "User-Agent kustom & mode cache") {
        LabeledField(
            "User-Agent kustom (opsional)", p.customUserAgent, { v -> update { it.copy(customUserAgent = v) } }, mono = true, singleLine = false,
            supporting = "Kosong = UA bawaan + \"WebifyApp/versi\""
        )
        SelectField(
            "Mode cache", WebCacheMode.entries, p.cacheMode,
            { when (it) { WebCacheMode.LOAD_DEFAULT -> "Default (ikuti header HTTP)"; WebCacheMode.LOAD_CACHE_ELSE_NETWORK -> "Cache dulu, lalu jaringan"; WebCacheMode.LOAD_NO_CACHE -> "Tanpa cache" } },
            { v -> update { it.copy(cacheMode = v) } }
        )
    }

    AccordionCard("Mode Offline", Icons.Rounded.CloudOff, when (p.offlineModeType) {
        OfflineModeType.DEFAULT_MODERN -> "Halaman offline modern"
        OfflineModeType.CUSTOM_HTML -> "HTML offline kustom"
        OfflineModeType.CACHE_FIRST -> "Cache-first"
    }) {
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
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium
                    )
                    Text(
                        when (m) {
                            OfflineModeType.DEFAULT_MODERN -> "Tampilan bawaan dengan tombol \"Coba Lagi\""
                            OfflineModeType.CUSTOM_HTML -> "HTML Anda sendiri (tautan webify://retry untuk muat ulang)"
                            OfflineModeType.CACHE_FIRST -> "Versi cache saat offline, lalu halaman offline"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        AnimatedVisibility(visible = p.offlineModeType == OfflineModeType.CUSTOM_HTML, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            CodeEditor(
                "HTML offline", p.customOfflineHtml, { v -> update { it.copy(customOfflineHtml = v) } },
                placeholder = "<!DOCTYPE html>\n<html>…<a href=\"webify://retry\">Coba lagi</a>…</html>", minHeight = 200, language = "html"
            )
        }
    }
}

// =============================================================================
// 4. Izin & Injeksi Kode
// =============================================================================

private data class PermSpec(val title: String, val desc: String, val icon: ImageVector, val get: (WebProjectEntity) -> Boolean, val set: (WebProjectEntity, Boolean) -> WebProjectEntity)

private val PERMISSIONS = listOf(
    PermSpec("Kamera", "Foto upload & WebRTC video", Icons.Rounded.CameraAlt, { it.permCamera }, { p, v -> p.copy(permCamera = v) }),
    PermSpec("Mikrofon", "WebRTC audio & rekam suara", Icons.Rounded.Mic, { it.permMicrophone }, { p, v -> p.copy(permMicrophone = v) }),
    PermSpec("Lokasi", "navigator.geolocation", Icons.Rounded.LocationOn, { it.permGeolocation }, { p, v -> p.copy(permGeolocation = v) }),
    PermSpec("Notifikasi", "Android 13+", Icons.Rounded.Notifications, { it.permNotifications }, { p, v -> p.copy(permNotifications = v) }),
    PermSpec("Unduhan", "Penyimpanan ≤ Android 9", Icons.Rounded.Download, { it.permStorageDownloads }, { p, v -> p.copy(permStorageDownloads = v) })
)

@Composable
private fun PermissionsCodePage(p: WebProjectEntity, update: Updater, onAskAi: (String) -> Unit) {
    var aiInstruction by rememberSaveable { mutableStateOf("") }
    val enabledCount = PERMISSIONS.count { it.get(p) }
    SectionCard("Izin Native", Icons.Rounded.Security, "Hanya izin aktif yang ditulis ke manifest", trailing = {
        Pill("$enabledCount/${PERMISSIONS.size}", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, mono = true)
    }) {
        PERMISSIONS.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
                row.forEach { spec ->
                    PermissionTile(spec.title, spec.desc, spec.icon, spec.get(p), { v -> update { spec.set(it, v) } }, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(Dimens.RadiusCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(Dimens.IconInline), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(Dimens.IconTextGapSmall))
                Text("Smart Injector AI", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            OutlinedTextField(
                value = aiInstruction, onValueChange = { aiInstruction = it },
                placeholder = { Text("Sembunyikan banner iklan bawah & perbesar font", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(Dimens.RadiusControl)
            )
            Button(
                onClick = { onAskAi(aiInstruction) }, enabled = aiInstruction.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary),
                shape = RoundedCornerShape(Dimens.RadiusControl),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(Dimens.IconInline))
                Spacer(Modifier.width(Dimens.IconTextGapSmall))
                Text("Buat dengan AI", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    ExpandableCodeCard("Custom CSS", "css", p.customCssCode, ".ads-banner { display: none !important; }") { v -> update { it.copy(customCssCode = v) } }
    ExpandableCodeCard("JavaScript • onPageFinished", "js", p.customJsOnPageFinished, "document.querySelector('header')?.remove();") { v ->
        update { it.copy(customJsOnPageFinished = v) }
    }
    ExpandableCodeCard("JavaScript • onPageStarted", "js", p.customJsOnPageStart, "// dijalankan saat halaman mulai dimuat") { v ->
        update { it.copy(customJsOnPageStart = v) }
    }
}

// =============================================================================
// 5. Build & Signing
// =============================================================================

@Composable
private fun BuildPage(
    p: WebProjectEntity,
    keystores: List<KeystoreEntity>,
    builds: List<BuildRecordEntity>,
    errors: StudioErrors,
    target: BuildType,
    onTarget: (BuildType) -> Unit,
    update: Updater,
    onQuickBuild: () -> Unit
) {
    val ctx = LocalContext.current
    var exportFile by remember { mutableStateOf<File?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val f = exportFile
        if (uri != null && f != null) FileActions.toast(ctx, if (FileActions.exportTo(ctx, f, uri)) "Diekspor: ${f.name}" else "Ekspor gagal")
        exportFile = null
    }
    val debugKs = keystores.firstOrNull { it.isDebugDefault }
    val releaseKs = keystores.filter { !it.isDebugDefault }
    val selectedKs = releaseKs.firstOrNull { it.id == p.selectedKeystoreId }

    SectionCard("Format Output", Icons.Rounded.RocketLaunch, "Dipakai tombol \"Build Sekarang\" di bawah") {
        OptionCard(Icons.Rounded.Bolt, "Debug APK", "Cepat untuk uji coba • selalu debug key", target == BuildType.APK_DEBUG) { onTarget(BuildType.APK_DEBUG) }
        OptionCard(Icons.Rounded.InstallMobile, "Release APK", "D8 release, untuk distribusi langsung", target == BuildType.APK_RELEASE) { onTarget(BuildType.APK_RELEASE) }
        OptionCard(Icons.Rounded.Shop, "Play Store AAB", "Android App Bundle siap unggah ke Play Console", target == BuildType.AAB_PLAYSTORE) { onTarget(BuildType.AAB_PLAYSTORE) }
    }

    SectionCard("Sertifikat Penandatanganan", Icons.Rounded.Key, "Untuk Release APK & Play Store AAB") {
        CertificateCard(
            title = "Debug Key (bawaan)", subtitle = "alias webifydebugkey • hanya untuk uji coba",
            sha1 = debugKs?.sha1Fingerprint, selected = selectedKs == null, isDebug = true
        ) { update { it.copy(selectedKeystoreId = null) } }
        releaseKs.forEach { ks ->
            CertificateCard(
                title = ks.name, subtitle = "alias ${ks.keyAlias} • ${ks.keyAlgorithm.label} • ${ks.validityYears} thn",
                sha1 = ks.sha1Fingerprint, selected = ks.id == selectedKs?.id, isDebug = false
            ) { update { it.copy(selectedKeystoreId = ks.id) } }
        }
        if (releaseKs.isEmpty()) {
            Text("Belum ada keystore rilis. Buat di tab Keystore pada navigasi bawah.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (target == BuildType.AAB_PLAYSTORE && selectedKs == null) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(Dimens.RadiusControl), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, Modifier.size(Dimens.IconInline), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(Dimens.IconTextGap))
                    Text("Google Play menolak AAB bertanda tangan debug key.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    if (errors.hasErrors) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(Dimens.RadiusControl), modifier = Modifier.fillMaxWidth()) {
            Text(
                listOfNotNull(
                    errors.packageName?.let { "Package: $it" }, errors.url?.let { "Sumber: $it" }, errors.versionName?.let { "Versi: $it" },
                    errors.colors.keys.takeIf { it.isNotEmpty() }?.let { "Warna tidak valid: ${it.joinToString()}" }
                ).joinToString("\n"),
                modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall
            )
        }
    }

    SecondaryButton("Quick Build & Test (Debug Key)", onClick = onQuickBuild, icon = Icons.Rounded.Bolt, modifier = Modifier.fillMaxWidth(), enabled = !errors.hasErrors)

    SectionCard("Riwayat Build", Icons.Rounded.History) {
        if (builds.isEmpty()) {
            Text("Belum ada build untuk proyek ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        builds.take(15).forEach { b ->
            BuildRecordRow(
                b,
                onInstall = { FileActions.installApk(ctx, File(b.outputFilePath!!)) },
                onShare = { FileActions.share(ctx, File(b.outputFilePath!!)) },
                onExport = { exportFile = File(b.outputFilePath!!); exporter.launch(File(b.outputFilePath!!).name) }
            )
        }
    }
}

@Composable
private fun OptionCard(icon: ImageVector, title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, label = "optBg")
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(Dimens.RadiusControl),
        color = bg,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(Dimens.IconInline), tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Dimens.IconTextGap))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            RadioButton(selected = selected, onClick = onSelect)
        }
    }
}

@Composable
private fun CertificateCard(title: String, subtitle: String, sha1: String?, selected: Boolean, isDebug: Boolean, onSelect: () -> Unit) {
    val bg by animateColorAsState(
        when {
            selected && !isDebug -> MaterialTheme.colorScheme.tertiaryContainer
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        }, label = "certBg"
    )
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(Dimens.RadiusControl),
        color = bg,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (isDebug) Icons.Rounded.Key else Icons.Rounded.Shield, null, Modifier.size(Dimens.IconInline))
                Spacer(Modifier.width(Dimens.IconTextGap))
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Pill(
                    if (isDebug) "DEBUG" else "RELEASE",
                    if (isDebug) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.tertiary,
                    if (isDebug) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiary
                )
                RadioButton(selected = selected, onClick = onSelect)
            }
            if (!sha1.isNullOrBlank()) {
                Text("SHA-1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sha1, style = WebifyTheme.mono.copy(fontSize = 11.sp, lineHeight = 15.sp))
            }
        }
    }
}

@Composable
private fun BuildRecordRow(b: BuildRecordEntity, onInstall: () -> Unit, onShare: () -> Unit, onExport: () -> Unit) {
    val ok = b.status == BuildStatus.SUCCESS
    val exists = b.outputFilePath != null && File(b.outputFilePath).exists()
    Row(Modifier.fillMaxWidth().heightIn(min = Dimens.SettingRowMinHeight), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null,
            tint = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error, modifier = Modifier.size(Dimens.IconInline)
        )
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

// =============================================================================
// Picker (tab Studio tanpa proyek aktif)
// =============================================================================

@Composable
fun StudioPickerScreen(container: AppContainer, onOpenProject: (String) -> Unit) {
    val projects by container.projectRepository.observeAll().collectAsStateWithLifecycle(initialValue = null)
    val dockInset = LocalDockInset.current
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { pad ->
        val list = projects
        when {
            list == null -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Rounded.Code, "Builder Studio", "Buat proyek di tab Projects untuk mulai mengedit.")
            }
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Dimens.ScreenPadding, end = Dimens.ScreenPadding,
                    top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 16.dp + dockInset
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap)
            ) {
                item {
                    Text("Builder Studio", style = MaterialTheme.typography.titleLarge)
                    Text("Pilih proyek untuk dibuka", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                Text(
                                    p.packageName, style = WebifyTheme.mono.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Pill("v${p.versionName}", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, mono = true)
                        }
                    }
                }
            }
        }
    }
}
