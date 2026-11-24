package com.webify.app.ui.dashboard

import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.rounded.MoreVert
import com.webify.app.ui.components.LocalDockInset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webify.app.ui.components.CardActionButton
import com.webify.app.ui.theme.Dimens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.webify.app.AppContainer
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.data.prefs.UiSettings
import com.webify.app.domain.SourceType
import com.webify.app.domain.Validators
import com.webify.app.ui.DashboardViewModel
import com.webify.app.ui.appViewModel
import com.webify.app.ui.components.LabeledField
import com.webify.app.ui.components.Pill
import com.webify.app.ui.components.StatTile
import com.webify.app.ui.components.EmptyState
import com.webify.app.ui.components.parseHexColor
import com.webify.app.ui.theme.WebifyTheme
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    container: AppContainer,
    onOpenProject: (String) -> Unit,
    onOpenConsole: () -> Unit,
    onOpenAssets: (String) -> Unit,
    onOpenCrashes: () -> Unit
) {
    val vm = appViewModel { DashboardViewModel(it.projectRepository, it.keystoreRepository, it.buildManager, it.archiveManager) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val importer = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.importBundle(uri) { id -> onOpenProject(id) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val session by container.buildManager.session.collectAsStateWithLifecycle()
    val ui by container.securePrefs.uiSettings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var showCache by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<WebProjectEntity?>(null) }
    val toolchain = remember { container.toolchain.status() }
    val dockInset = LocalDockInset.current

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.ScreenPadding, end = Dimens.ScreenPadding,
                top = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 16.dp + dockInset
            ),
            horizontalArrangement = Arrangement.spacedBy(Dimens.SectionGap),
            verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Header(
                    ui = ui, onUi = { container.securePrefs.saveUi(it) }, onCreate = { showCreate = true },
                    onImport = { importer.launch(arrayOf("*/*")) },
                    onCrashes = onOpenCrashes,
                    onCache = { showCache = true }
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Dimens.SectionGap)) {
                    StatTile("Proyek", state.totalProjects.toString(), Icons.Rounded.SpaceDashboard, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatTile("Build sukses", state.successfulBuilds.toString(), Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    StatTile("Keystore", state.keystoreCount.toString(), Icons.Rounded.VpnKey, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                }
            }
            if (!toolchain.ready) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(Dimens.RadiusCard)) {
                        Row(Modifier.fillMaxWidth().padding(Dimens.CardPadding), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, null, Modifier.size(Dimens.IconInline), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(Dimens.IconTextGap))
                            Text(toolchain.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            session?.let { s ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        onClick = onOpenConsole,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(Dimens.RadiusCard)
                    ) {
                        Column(Modifier.padding(Dimens.CardPadding)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Terminal, null, Modifier.size(Dimens.IconInline), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.width(Dimens.IconTextGap))
                                Text(
                                    if (s.running) "Membangun ${s.projectName} • ${s.activeStage?.label ?: "…"}" else "Build terakhir: ${s.projectName} — buka konsol",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                                )
                                Text("${s.percent}%", style = WebifyTheme.mono, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            if (s.running) {
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)))
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Cari proyek, package, atau URL") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(Dimens.IconInline)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                )
            }
            if (state.loaded && state.projects.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        Icons.Rounded.Language,
                        if (state.query.isBlank()) "Belum ada proyek" else "Tidak ditemukan",
                        if (state.query.isBlank()) "Ubah website favorit Anda menjadi aplikasi Android dalam hitungan detik — sepenuhnya di perangkat ini."
                        else "Coba kata kunci lain."
                    ) {
                        if (state.query.isBlank()) FilledTonalButton(onClick = { showCreate = true }) { Text("Buat proyek pertama") }
                    }
                }
            }
            items(state.projects, key = { it.id }) { p ->
                ProjectCard(
                    p,
                    onBuild = { if (vm.quickBuild(p)) onOpenConsole() },
                    onEdit = { onOpenProject(p.id) },
                    onClone = { vm.clone(p) },
                    onDelete = { toDelete = p },
                    onExport = { vm.export(p) { f -> com.webify.app.util.FileActions.share(ctx, f) } },
                    onAssets = { onOpenAssets(p.id) }
                )
            }
        }
    }

    if (showCreate) NewProjectWizard(
        onDismiss = { showCreate = false },
        onFinish = { r ->
            val meta = r.meta
            vm.create(
                r.name, r.url, r.packageName,
                template = { base ->
                    var n = r.template.apply(base)
                    meta?.themeColor?.let { c -> n = n.copy(primaryColorHex = c, statusBarColorHex = c, progressBarColorHex = c) }
                    (meta?.backgroundColor ?: meta?.themeColor)?.let { c -> n = n.copy(splashBgColorHex = c) }
                    meta?.backgroundColor?.let { c -> n = n.copy(adaptiveBgColor = c) }
                    n
                },
                iconSource = meta?.iconFile,
                buildNow = r.buildNow,
                onBuildStarted = { showCreate = false; onOpenConsole() }
            ) { id -> showCreate = false; onOpenProject(id) }
        }
    )
    if (showCache) CacheDialog(onDismiss = { showCache = false })

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Hapus ${p.appName}?") },
            text = { Text("Konfigurasi, workspace build, dan file output proyek ini akan dihapus permanen.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(p); toDelete = null }) { Text("Hapus", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun Header(
    ui: UiSettings, onUi: (UiSettings) -> Unit, onCreate: () -> Unit, onImport: () -> Unit,
    onCrashes: () -> Unit, onCache: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(Dimens.RadiusControl))
                .background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFF7C3AED)))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Code, null, Modifier.size(Dimens.IconBar), tint = Color.White) }
        Spacer(Modifier.width(Dimens.IconTextGap))
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text("Webify", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            Text(
                "Mobile IDE & Website-to-App Builder", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        FilledTonalButton(
            onClick = onCreate,
            shape = RoundedCornerShape(Dimens.RadiusControl),
            contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
            modifier = Modifier.height(Dimens.SecondaryButtonHeight)
        ) {
            Icon(Icons.Rounded.Add, null, Modifier.size(Dimens.IconInline))
            Spacer(Modifier.width(6.dp))
            Text("Baru", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(
                    when (ui.darkMode) { true -> Icons.Rounded.DarkMode; false -> Icons.Rounded.LightMode; null -> Icons.Rounded.SettingsBrightness },
                    "Tema", Modifier.size(Dimens.IconBar)
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Impor proyek (.webify)") },
                    leadingIcon = { Icon(Icons.Rounded.FileOpen, null, Modifier.size(Dimens.IconInline)) },
                    onClick = { menu = false; onImport() }
                )
                DropdownMenuItem(
                    text = { Text("Laporan crash") },
                    leadingIcon = { Icon(Icons.Rounded.BugReport, null, Modifier.size(Dimens.IconInline)) },
                    onClick = { menu = false; onCrashes() }
                )
                DropdownMenuItem(
                    text = { Text("Kelola cache") },
                    leadingIcon = { Icon(Icons.Rounded.Storage, null, Modifier.size(Dimens.IconInline)) },
                    onClick = { menu = false; onCache() }
                )
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Ikuti sistem") }, leadingIcon = { Icon(Icons.Rounded.SettingsBrightness, null, Modifier.size(Dimens.IconInline)) }, onClick = { onUi(ui.copy(darkMode = null)); menu = false })
                DropdownMenuItem(text = { Text("Terang") }, leadingIcon = { Icon(Icons.Rounded.LightMode, null, Modifier.size(Dimens.IconInline)) }, onClick = { onUi(ui.copy(darkMode = false)); menu = false })
                DropdownMenuItem(text = { Text("Gelap (Obsidian)") }, leadingIcon = { Icon(Icons.Rounded.DarkMode, null, Modifier.size(Dimens.IconInline)) }, onClick = { onUi(ui.copy(darkMode = true)); menu = false })
                DropdownMenuItem(
                    text = { Text("Material You") },
                    leadingIcon = { Icon(Icons.Rounded.Palette, null, Modifier.size(Dimens.IconInline)) },
                    trailingIcon = { Switch(checked = ui.dynamicColor, onCheckedChange = null) },
                    onClick = { onUi(ui.copy(dynamicColor = !ui.dynamicColor)) },
                    enabled = android.os.Build.VERSION.SDK_INT >= 31
                )
            }
        }
    }
}

@Composable
private fun ProjectCard(
    p: WebProjectEntity,
    onBuild: () -> Unit,
    onEdit: () -> Unit,
    onClone: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onAssets: () -> Unit
) {
    Surface(
        onClick = onEdit,
        shape = RoundedCornerShape(Dimens.RadiusCard),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
            // Baris atas: ikon 48dp • nama + package (weight) • badge versi di kanan atas
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProjectIcon(p, 48)
                Spacer(Modifier.width(Dimens.IconTextGap))
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(p.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        p.packageName,
                        style = WebifyTheme.mono.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Pill("v${p.versionName}", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, mono = true)
            }
            // Sumber web
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (p.sourceType == SourceType.URL) Icons.Rounded.Language else Icons.Rounded.FolderZip, null,
                    Modifier.size(Dimens.IconChip), tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(Dimens.IconTextGapSmall))
                Text(
                    if (p.sourceType == SourceType.URL) p.targetUrl else "Proyek HTML lokal (ZIP)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
            }
            // Baris aksi: Build (utama) + menu ⋮ (Edit, Clone, Hapus). Ketuk kartu = buka Studio.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CardActionButton("Build", Icons.Rounded.Build, onBuild, Modifier.width(112.dp), emphasized = true)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Ketuk kartu untuk edit", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                )
                Box {
                    var menu by remember { mutableStateOf(false) }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Opsi lain", Modifier.size(Dimens.IconInline)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Buka di Studio") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(Dimens.IconInline)) },
                            onClick = { menu = false; onEdit() }
                        )
                        DropdownMenuItem(
                            text = { Text("Aset Play Store") },
                            leadingIcon = { Icon(Icons.Rounded.Collections, null, Modifier.size(Dimens.IconInline)) },
                            onClick = { menu = false; onAssets() }
                        )
                        DropdownMenuItem(
                            text = { Text("Export .webify") },
                            leadingIcon = { Icon(Icons.Rounded.IosShare, null, Modifier.size(Dimens.IconInline)) },
                            onClick = { menu = false; onExport() }
                        )
                        DropdownMenuItem(
                            text = { Text("Duplikat proyek") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null, Modifier.size(Dimens.IconInline)) },
                            onClick = { menu = false; onClone() }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Hapus", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, Modifier.size(Dimens.IconInline), tint = MaterialTheme.colorScheme.error) },
                            onClick = { menu = false; onDelete() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProjectIcon(p: WebProjectEntity, sizeDp: Int) {
    val shape = RoundedCornerShape(if (sizeDp >= 40) Dimens.RadiusControl else (sizeDp * 0.28f).dp)
    val path = p.iconUri
    if (path != null && File(path).exists()) {
        AsyncImage(
            model = File(path),
            contentDescription = p.appName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(sizeDp.dp).clip(shape)
        )
    } else {
        val bg = parseHexColor(p.primaryColorHex) ?: MaterialTheme.colorScheme.primary
        Box(Modifier.size(sizeDp.dp).clip(shape).background(bg), contentAlignment = Alignment.Center) {
            Text(
                p.appName.trim().firstOrNull()?.uppercase() ?: "W",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
    }
}


/** Ukuran & pembersihan cache SDK (unduhan Maven) dan cache build inkremental. */
@Composable
private fun CacheDialog(onDismiss: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var sdk by remember { mutableStateOf(-1L) }
    var build by remember { mutableStateOf(-1L) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() = scope.launch {
        val (a, b) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.webify.app.build.AarDependencyManager.cacheSize(ctx.filesDir) to com.webify.app.build.BuildCache.totalSize(ctx.filesDir)
        }
        sdk = a; build = b
    }
    LaunchedEffect(Unit) { refresh() }
    fun clear(action: (java.io.File) -> Long, label: String) = scope.launch {
        busy = true
        val freed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { action(ctx.filesDir) }
        busy = false
        com.webify.app.util.FileActions.toast(ctx, "$label dibersihkan • ${com.webify.app.util.FileActions.humanSize(freed)}")
        refresh()
    }
    fun fmt(v: Long) = if (v < 0) "menghitung…" else com.webify.app.util.FileActions.humanSize(v)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Storage, null) },
        title = { Text("Kelola Cache") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
                Text("Cache SDK • ${fmt(sdk)}", style = MaterialTheme.typography.titleSmall)
                Text("Library Maven/AAR yang sudah diunduh (FCM, AdMob, ZXing…). Bersihkan bila build gagal karena file rusak; akan diunduh ulang.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { clear(com.webify.app.build.AarDependencyManager::clearCache, "Cache SDK") }, enabled = !busy && sdk > 0) {
                    Text("Bersihkan cache SDK")
                }
                HorizontalDivider()
                Text("Cache build • ${fmt(build)}", style = MaterialTheme.typography.titleSmall)
                Text("Hasil aapt2/ecj/d8 yang dipakai ulang agar build berikutnya lebih cepat.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(onClick = { clear(com.webify.app.build.BuildCache::clearAll, "Cache build") }, enabled = !busy && build > 0) {
                    Text("Bersihkan cache build")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}
