package com.webify.app.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
fun DashboardScreen(container: AppContainer, onOpenProject: (String) -> Unit, onOpenConsole: () -> Unit) {
    val vm = appViewModel { DashboardViewModel(it.projectRepository, it.keystoreRepository, it.buildManager) }
    val state by vm.state.collectAsStateWithLifecycle()
    val session by container.buildManager.session.collectAsStateWithLifecycle()
    val ui by container.securePrefs.uiSettings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<WebProjectEntity?>(null) }
    val toolchain = remember { container.toolchain.status() }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp)) },
                text = { Text("Proyek Baru") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = pad.calculateTopPadding() + 8.dp,
                bottom = pad.calculateBottomPadding() + 96.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Header(ui = ui, onUi = { container.securePrefs.saveUi(it) })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Proyek", state.totalProjects.toString(), Icons.Rounded.SpaceDashboard, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                    StatTile("Build sukses", state.successfulBuilds.toString(), Icons.Rounded.CheckCircle, MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
                    StatTile("Keystore", state.keystoreCount.toString(), Icons.Rounded.VpnKey, MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
                }
            }
            if (!toolchain.ready) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(12.dp))
                            Text(toolchain.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
            session?.let { s ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        onClick = onOpenConsole,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Terminal, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    if (s.running) "Membangun ${s.projectName} • ${s.activeStage?.label ?: "…"}" else "Build terakhir: ${s.projectName} — buka konsol",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.weight(1f)
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
                    leadingIcon = { Icon(Icons.Rounded.Search, null, Modifier.size(20.dp)) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth()
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
                    onDelete = { toDelete = p }
                )
            }
        }
    }

    if (showCreate) CreateProjectDialog(
        onDismiss = { showCreate = false },
        onCreate = { name, url, pkg ->
            vm.create(name, url, pkg) { id -> showCreate = false; onOpenProject(id) }
        }
    )

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
private fun Header(ui: UiSettings, onUi: (UiSettings) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1E3A8A), Color(0xFF2563EB), Color(0xFF7C3AED)))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Code, null, tint = Color.White) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Webify", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
            Text("Mobile IDE & Website-to-App Builder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(
                    when (ui.darkMode) { true -> Icons.Rounded.DarkMode; false -> Icons.Rounded.LightMode; null -> Icons.Rounded.SettingsBrightness },
                    "Tema"
                )
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Ikuti sistem") }, leadingIcon = { Icon(Icons.Rounded.SettingsBrightness, null) }, onClick = { onUi(ui.copy(darkMode = null)); menu = false })
                DropdownMenuItem(text = { Text("Terang") }, leadingIcon = { Icon(Icons.Rounded.LightMode, null) }, onClick = { onUi(ui.copy(darkMode = false)); menu = false })
                DropdownMenuItem(text = { Text("Gelap (Obsidian)") }, leadingIcon = { Icon(Icons.Rounded.DarkMode, null) }, onClick = { onUi(ui.copy(darkMode = true)); menu = false })
                DropdownMenuItem(
                    text = { Text("Material You") },
                    leadingIcon = { Icon(Icons.Rounded.Palette, null) },
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
    onDelete: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProjectIcon(p, 52)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.appName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                        Spacer(Modifier.width(8.dp))
                        Pill("v${p.versionName}", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, mono = true)
                    }
                    Text(p.packageName, style = WebifyTheme.mono.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (p.sourceType == SourceType.URL) Icons.Rounded.Language else Icons.Rounded.FolderZip, null,
                    Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (p.sourceType == SourceType.URL) p.targetUrl else "Proyek HTML lokal (ZIP)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onBuild, contentPadding = PaddingValues(horizontal = 14.dp)) {
                    Icon(Icons.Rounded.Build, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Build")
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit", Modifier.size(20.dp)) }
                IconButton(onClick = onClone) { Icon(Icons.Rounded.ContentCopy, "Clone", Modifier.size(20.dp)) }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Hapus", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
fun ProjectIcon(p: WebProjectEntity, sizeDp: Int) {
    val shape = RoundedCornerShape((sizeDp * 0.28f).dp)
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

@Composable
private fun CreateProjectDialog(onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("https://") }
    var pkg by rememberSaveable { mutableStateOf("") }
    var pkgEdited by rememberSaveable { mutableStateOf(false) }
    val effectivePkg = if (pkgEdited) pkg else DashboardViewModel.suggestPackage(name)
    val pkgError = Validators.packageNameError(effectivePkg)
    val urlError = Validators.urlError(url)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Add, null) },
        title = { Text("Proyek Baru") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LabeledField("Nama aplikasi", name, { name = it }, placeholder = "Toko Saya")
                LabeledField("URL website", url, { url = it.trim() }, error = if (url.length > 8) urlError else null,
                    mono = true, keyboardType = KeyboardType.Uri)
                LabeledField("Package name", effectivePkg, { pkg = it.trim().lowercase(); pkgEdited = true },
                    error = if (effectivePkg.isNotEmpty()) pkgError else null, mono = true,
                    supporting = "ID unik di Play Store, tidak bisa diubah setelah rilis")
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && pkgError == null && urlError == null,
                onClick = { onCreate(name, url, effectivePkg) }
            ) { Text("Buat") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
