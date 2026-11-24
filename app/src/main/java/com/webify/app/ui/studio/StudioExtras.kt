package com.webify.app.ui.studio

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webify.app.data.db.BuildRecordEntity
import com.webify.app.data.db.KeystoreEntity
import com.webify.app.data.db.WebProjectEntity
import com.webify.app.domain.BuildStatus
import com.webify.app.domain.BuildType
import com.webify.app.domain.PlayStoreChecker
import com.webify.app.ui.components.Pill
import com.webify.app.ui.components.SecondaryButton
import com.webify.app.ui.components.SectionCard
import com.webify.app.ui.theme.Dimens
import com.webify.app.ui.theme.WebifyTheme
import com.webify.app.util.FileActions
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

// =============================================================================
// Kesiapan Play Store
// =============================================================================

@Composable
private fun severityStyle(s: PlayStoreChecker.Severity): Pair<ImageVector, Color> = when (s) {
    PlayStoreChecker.Severity.ERROR -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
    PlayStoreChecker.Severity.WARNING -> Icons.Rounded.Warning to MaterialTheme.colorScheme.tertiary
    PlayStoreChecker.Severity.INFO -> Icons.Rounded.Info to MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun IssueRow(issue: PlayStoreChecker.Issue, onJump: ((Int) -> Unit)?) {
    val (icon, tint) = severityStyle(issue.severity)
    Surface(
        onClick = { issue.tab?.let { onJump?.invoke(it) } },
        enabled = issue.tab != null && onJump != null,
        shape = RoundedCornerShape(Dimens.RadiusSmall),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, Modifier.size(18.dp).padding(top = 2.dp), tint = tint)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(issue.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(issue.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Kartu ringkas skor kesiapan rilis + daftar temuan (ketuk untuk lompat ke tab terkait). */
@Composable
internal fun PlayStoreReadinessCard(issues: List<PlayStoreChecker.Issue>, onJump: (Int) -> Unit) {
    val score = PlayStoreChecker.score(issues)
    val errors = issues.count { it.severity == PlayStoreChecker.Severity.ERROR }
    val warnings = issues.count { it.severity == PlayStoreChecker.Severity.WARNING }
    var expanded by remember { mutableStateOf(errors > 0) }
    SectionCard(
        "Siap Play Store", Icons.Rounded.Shop,
        when {
            errors > 0 -> "$errors masalah wajib diperbaiki • $warnings peringatan"
            warnings > 0 -> "$warnings peringatan • siap diunggah dengan catatan"
            else -> "Semua pemeriksaan lolos"
        },
        trailing = {
            Pill(
                "$score/100",
                when {
                    errors > 0 -> MaterialTheme.colorScheme.errorContainer
                    warnings > 0 -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.secondaryContainer
                },
                MaterialTheme.colorScheme.onSurface, mono = true
            )
        }
    ) {
        LinearProgressIndicator(
            progress = { score / 100f },
            color = when {
                errors > 0 -> MaterialTheme.colorScheme.error
                warnings > 0 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.secondary
            },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        )
        val shown = if (expanded) issues else issues.filter { it.severity == PlayStoreChecker.Severity.ERROR }.take(3)
        Column(Modifier.animateContentSize()) { shown.forEach { IssueRow(it, onJump) } }
        if (issues.size > shown.size || expanded) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Ringkas" else "Lihat semua (${issues.size})")
            }
        }
    }
}

/** Dialog sebelum build rilis bila masih ada masalah. */
@Composable
internal fun PlayStoreGateDialog(
    issues: List<PlayStoreChecker.Issue>,
    onDismiss: () -> Unit,
    onBuildAnyway: () -> Unit,
    onJump: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Shop, null) },
        title = { Text("Belum siap Play Store") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Build tetap bisa dibuat (mis. untuk distribusi langsung), tetapi masalah berikut akan membuat unggahan ke Google Play ditolak:",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                issues.filter { it.severity == PlayStoreChecker.Severity.ERROR }.forEach { IssueRow(it) { t -> onDismiss(); onJump(t) } }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onBuildAnyway() }) { Text("Tetap Build") } },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                issues.firstOrNull { it.severity == PlayStoreChecker.Severity.ERROR }?.tab?.let(onJump)
            }) { Text("Perbaiki") }
        }
    )
}

// =============================================================================
// Riwayat build dengan perbandingan
// =============================================================================

private val diffJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Field snapshot yang tidak relevan untuk perbandingan. */
private val IGNORED_FIELDS = setOf("id", "createdAt", "updatedAt", "customOfflineHtml")

/** "statusBarColorHex" → "status bar color hex". */
private fun humanize(field: String): String =
    field.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").lowercase(Locale.ROOT)

internal data class ConfigChange(val field: String, val before: String, val after: String)

internal fun diffSnapshots(old: String, new: String): List<ConfigChange> {
    if (old.isBlank() || new.isBlank()) return emptyList()
    val a = runCatching { diffJson.parseToJsonElement(old).jsonObject }.getOrNull() ?: return emptyList()
    val b = runCatching { diffJson.parseToJsonElement(new).jsonObject }.getOrNull() ?: return emptyList()
    fun show(v: Any?): String {
        val s = (v as? JsonPrimitive)?.content ?: v?.toString() ?: "–"
        return when {
            s.isEmpty() -> "(kosong)"
            s.length > 48 -> s.take(45) + "…"
            else -> s
        }
    }
    return (a.keys + b.keys).filter { it !in IGNORED_FIELDS }.distinct().sorted().mapNotNull { k ->
        val x = a[k]; val y = b[k]
        if (x == y) null else ConfigChange(humanize(k), show(x), show(y))
    }
}

private fun parseTimings(raw: String): List<Pair<String, Long>> =
    runCatching { diffJson.parseToJsonElement(raw) as JsonObject }.getOrNull()
        ?.entries?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.longOrNull?.let { k to it } }
        .orEmpty()

@Composable
internal fun BuildHistorySection(
    p: WebProjectEntity,
    builds: List<BuildRecordEntity>,
    onExport: (File) -> Unit
) {
    val ctx = LocalContext.current
    var expanded by remember { mutableStateOf<String?>(null) }
    // Status pemasangan aplikasi (untuk tombol Buka)
    var installed by remember { mutableStateOf<FileActions.InstalledInfo?>(null) }
    LaunchedEffect(p.packageName) {
        while (true) {
            installed = FileActions.installedInfo(ctx, p.packageName)
            delay(2000)
        }
    }

    SectionCard("Riwayat Build", Icons.Rounded.History, if (builds.isEmpty()) null else "Ketuk build untuk melihat perbandingan") {
        if (builds.isEmpty()) {
            Text("Belum ada build untuk proyek ini.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val list = builds.sortedByDescending { it.timestamp }.take(20)
        list.forEachIndexed { i, b ->
            val older = list.drop(i + 1)
            val prevSameType = older.firstOrNull { it.status == BuildStatus.SUCCESS && it.buildType == b.buildType }
            val prevAny = older.firstOrNull()
            HistoryRow(
                b = b,
                sizeDelta = if (b.status == BuildStatus.SUCCESS && prevSameType != null) b.fileSizeBytes - prevSameType.fileSizeBytes else null,
                timeDelta = prevSameType?.let { b.durationMs - it.durationMs },
                expanded = expanded == b.id,
                onToggle = { expanded = if (expanded == b.id) null else b.id },
                changes = if (expanded == b.id && prevAny != null) diffSnapshots(prevAny.configSnapshotJson, b.configSnapshotJson) else emptyList(),
                hasPrevSnapshot = prevAny?.configSnapshotJson?.isNotBlank() == true && b.configSnapshotJson.isNotBlank(),
                installedMatches = installed != null && b.versionCode > 0 && installed!!.versionCode == b.versionCode.toLong(),
                onInstall = { b.outputFilePath?.let { FileActions.installApk(ctx, File(it)) } },
                onOpen = { FileActions.launchApp(ctx, p.packageName) },
                onShare = { b.outputFilePath?.let { FileActions.share(ctx, File(it)) } },
                onExport = { b.outputFilePath?.let { onExport(File(it)) } }
            )
            if (i < list.lastIndex) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun HistoryRow(
    b: BuildRecordEntity,
    sizeDelta: Long?,
    timeDelta: Long?,
    expanded: Boolean,
    onToggle: () -> Unit,
    changes: List<ConfigChange>,
    hasPrevSnapshot: Boolean,
    installedMatches: Boolean,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit
) {
    val ok = b.status == BuildStatus.SUCCESS
    val exists = b.outputFilePath != null && File(b.outputFilePath).exists()
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Surface(onClick = onToggle, color = Color.Transparent, shape = RoundedCornerShape(Dimens.RadiusSmall)) {
            Row(Modifier.fillMaxWidth().heightIn(min = Dimens.SettingRowMinHeight).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Error, null,
                    tint = if (ok) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error, modifier = Modifier.size(Dimens.IconInline)
                )
                Spacer(Modifier.width(Dimens.IconTextGap))
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.buildType.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
                        if (b.versionName.isNotBlank()) {
                            Spacer(Modifier.width(6.dp))
                            Text("v${b.versionName} (${b.versionCode})", style = WebifyTheme.mono.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ok) {
                            Text(FileActions.humanSize(b.fileSizeBytes), style = WebifyTheme.mono.copy(fontSize = 11.sp))
                            sizeDelta?.takeIf { it != 0L }?.let { d ->
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    (if (d > 0) "+" else "−") + FileActions.humanSize(kotlin.math.abs(d)),
                                    style = WebifyTheme.mono.copy(fontSize = 11.sp),
                                    color = if (d > 256 * 1024) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                )
                            }
                            Text(" • ", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("%.1f s".format(b.durationMs / 1000.0), style = WebifyTheme.mono.copy(fontSize = 11.sp))
                        timeDelta?.takeIf { kotlin.math.abs(it) >= 1000 }?.let { d ->
                            Text(
                                " (" + (if (d > 0) "+" else "−") + "%.1f s)".format(kotlin.math.abs(d) / 1000.0),
                                style = WebifyTheme.mono.copy(fontSize = 11.sp),
                                color = if (d > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(b.timestamp)) +
                            (b.errorSummary?.let { " • $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 6 else 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (expanded) {
            Column(Modifier.padding(start = 32.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val timings = parseTimings(b.stageTimingsJson)
                if (timings.isNotEmpty()) {
                    Text("Durasi per tahap", style = MaterialTheme.typography.labelMedium)
                    val max = timings.maxOf { it.second }.coerceAtLeast(1)
                    timings.forEach { (stage, ms) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stage, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(96.dp), maxLines = 1)
                            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                Box(Modifier.fillMaxWidth(ms.toFloat() / max).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
                            }
                            Text(if (ms >= 1000) "%.1f s".format(ms / 1000.0) else "$ms ms", style = WebifyTheme.mono.copy(fontSize = 11.sp),
                                modifier = Modifier.width(60.dp).padding(start = 6.dp), maxLines = 1)
                        }
                    }
                }
                when {
                    !hasPrevSnapshot -> Text("Perbandingan pengaturan tersedia mulai build berikutnya.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    changes.isEmpty() -> Text("Pengaturan sama dengan build sebelumnya.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> {
                        Text("Berubah dibanding build sebelumnya (${changes.size})", style = MaterialTheme.typography.labelMedium)
                        changes.take(15).forEach { c ->
                            Column {
                                Text(c.field, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text("${c.before} → ${c.after}", style = WebifyTheme.mono.copy(fontSize = 11.sp, lineHeight = 15.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (changes.size > 15) Text("+${changes.size - 15} lainnya", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (ok && exists) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (b.buildType != BuildType.AAB_PLAYSTORE) {
                            if (installedMatches) {
                                SecondaryButton("Buka", onClick = onOpen, icon = Icons.Rounded.PlayArrow, tonal = true)
                            }
                            IconButton(onClick = onInstall) { Icon(Icons.Rounded.InstallMobile, "Install", Modifier.size(Dimens.IconInline)) }
                        }
                        IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "Bagikan", Modifier.size(Dimens.IconInline)) }
                        IconButton(onClick = onExport) { Icon(Icons.Rounded.Download, "Ekspor", Modifier.size(Dimens.IconInline)) }
                    }
                }
            }
        }
    }
}

// =============================================================================
// App Links: assetlinks.json
// =============================================================================

/** Membuat isi /.well-known/assetlinks.json agar App Links terverifikasi (tanpa dialog pilih aplikasi). */
internal fun assetLinksJson(packageName: String, sha256Fingerprints: List<String>): String {
    val fps = sha256Fingerprints.filter { it.isNotBlank() }.distinct()
        .joinToString(",\n        ") { "\"${it.uppercase(Locale.ROOT)}\"" }
    return """
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "$packageName",
    "sha256_cert_fingerprints": [
        $fps
    ]
  }
}]
""".trim()
}

@Composable
internal fun AppLinksHelper(p: WebProjectEntity, keystores: List<KeystoreEntity>, onUseSiteDomain: () -> Unit) {
    val ctx = LocalContext.current
    val host = p.deepLinkHost.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
    val siteHost = runCatching { java.net.URI(p.targetUrl.trim()).host }.getOrNull()
    if (host.isEmpty()) {
        if (!siteHost.isNullOrBlank()) {
            SecondaryButton("Gunakan domain website ($siteHost)", onClick = onUseSiteDomain, icon = Icons.Rounded.Link, modifier = Modifier.fillMaxWidth())
        }
        return
    }
    val selected = keystores.firstOrNull { it.id == p.selectedKeystoreId }
    val debug = keystores.firstOrNull { it.isDebugDefault }
    val fps = listOfNotNull(selected?.sha256Fingerprint, debug?.sha256Fingerprint)
    val json = assetLinksJson(p.packageName, fps)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(Dimens.RadiusControl), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Verifikasi App Links", style = MaterialTheme.typography.labelLarge)
            Text(
                "Unggah file ini ke https://$host/.well-known/assetlinks.json agar link $host langsung terbuka di aplikasi tanpa dialog pilihan. " +
                    "Berisi sidik jari keystore rilis terpilih" + (if (debug != null) " + debug key (untuk uji)." else "."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (selected == null) {
                Text("Pilih keystore rilis di tab Build agar sidik jari rilis ikut dimasukkan.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            }
            Text(json, style = WebifyTheme.mono.copy(fontSize = 10.sp, lineHeight = 14.sp), maxLines = 14, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Salin", onClick = { FileActions.copy(ctx, "assetlinks.json", json) }, icon = Icons.Rounded.ContentCopy, modifier = Modifier.weight(1f))
                SecondaryButton("Bagikan", onClick = {
                    val f = File(ctx.cacheDir, "assetlinks.json").apply { writeText(json) }
                    FileActions.share(ctx, f)
                }, icon = Icons.Rounded.Share, modifier = Modifier.weight(1f))
            }
        }
    }
}

// =============================================================================
// Pencarian pengaturan Studio
// =============================================================================

internal data class SettingEntry(val label: String, val section: String, val tab: Int, val keywords: String)

/** Indeks statis seluruh pengaturan Studio (tab: 0 Identitas, 1 Tampilan, 2 WebView, 3 Izin & Kode, 4 SDK, 5 Build). */
internal val SETTINGS_INDEX = listOf(
    SettingEntry("Nama aplikasi", "Identitas", 0, "name app nama judul"),
    SettingEntry("Package name", "Identitas", 0, "package id applicationid paket"),
    SettingEntry("URL website / sumber", "Identitas", 0, "url link website zip offline lokal html sumber"),
    SettingEntry("Ambil info website otomatis", "Identitas", 0, "autofill otomatis manifest pwa meta"),
    SettingEntry("Versi (versionName / versionCode)", "Identitas", 0, "version versi code nomor"),
    SettingEntry("Ikon aplikasi", "Identitas", 0, "icon ikon logo adaptive"),
    SettingEntry("Domain yang diizinkan", "Identitas", 0, "domain allowed izin link eksternal"),
    SettingEntry("Warna tema & status bar", "Tampilan", 1, "warna color tema status bar navigasi primary"),
    SettingEntry("Menu navigasi native", "Tampilan", 1, "menu bottom nav drawer navigasi tab"),
    SettingEntry("App Shortcuts", "Tampilan", 1, "shortcut pintasan launcher tekan lama"),
    SettingEntry("Tentang & Rating / kebijakan privasi", "Tampilan", 1, "about tentang rating privacy privasi kebijakan"),
    SettingEntry("Splash screen", "Tampilan", 1, "splash pembuka loading"),
    SettingEntry("Halaman offline", "WebView", 2, "offline koneksi internet cache"),
    SettingEntry("Orientasi & layar penuh", "Tampilan", 1, "orientation orientasi fullscreen layar penuh potret lanskap"),
    SettingEntry("Pull-to-refresh", "WebView", 2, "pull refresh tarik muat ulang swipe"),
    SettingEntry("JavaScript & DOM storage", "WebView", 2, "javascript js dom storage"),
    SettingEntry("User agent / mode desktop", "WebView", 2, "user agent desktop ua"),
    SettingEntry("Zoom & ukuran teks", "WebView", 2, "zoom teks text font"),
    SettingEntry("Mode gelap paksa", "WebView", 2, "dark gelap malam"),
    SettingEntry("Cache WebView", "WebView", 2, "cache mode"),
    SettingEntry("Kunci biometrik", "WebView", 2, "biometrik sidik jari fingerprint kunci lock wajah pin"),
    SettingEntry("Blokir iklan & privasi", "WebView", 2, "adblock iklan pelacak privasi screenshot cookie"),
    SettingEntry("Deep link / App Links", "Identitas", 0, "deep link app links assetlinks domain"),
    SettingEntry("Izin kamera / mikrofon / lokasi", "Izin & Kode", 3, "izin permission kamera camera mikrofon microphone lokasi location gps"),
    SettingEntry("Notifikasi", "Izin & Kode", 3, "notifikasi notification push"),
    SettingEntry("Unduhan & upload file", "Izin & Kode", 3, "download unduh upload file storage"),
    SettingEntry("CSS / JS kustom", "Izin & Kode", 3, "css js javascript kode custom inject"),
    SettingEntry("Push Notification (FCM)", "SDK", 4, "fcm firebase push google services"),
    SettingEntry("Iklan AdMob", "SDK", 4, "admob iklan ads banner interstitial monetisasi"),
    SettingEntry("QR & Barcode Scanner", "SDK", 4, "qr barcode scan pindai zxing"),
    SettingEntry("Dependensi Maven / AAR / kecualikan", "SDK", 4, "maven aar jar dependency library kecualikan exclude"),
    SettingEntry("Format output (APK / AAB)", "Build", 5, "apk aab bundle release debug format"),
    SettingEntry("Keystore / sertifikat", "Build", 5, "keystore sertifikat signing tanda tangan key"),
    SettingEntry("Auto-increment versi", "Build", 5, "auto increment versi otomatis"),
    SettingEntry("minSdk / targetSdk", "Identitas", 0, "minsdk targetsdk api level"),
    SettingEntry("Siap Play Store", "Build", 5, "play store cek pemeriksa siap rilis"),
    SettingEntry("Riwayat build", "Build", 5, "riwayat history build log perbandingan")
)

internal fun searchSettings(query: String): List<SettingEntry> {
    val terms = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return emptyList()
    return SETTINGS_INDEX.filter { e ->
        val hay = (e.label + " " + e.section + " " + e.keywords).lowercase(Locale.ROOT)
        terms.all { hay.contains(it) }
    }
}
