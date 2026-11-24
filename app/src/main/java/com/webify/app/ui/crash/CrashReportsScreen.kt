package com.webify.app.ui.crash

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webify.app.AppContainer
import com.webify.app.ai.CopilotRequest
import com.webify.app.data.crash.CrashReport
import com.webify.app.data.crash.CrashReportStore
import com.webify.app.ui.components.EmptyState
import com.webify.app.ui.components.SecondaryButton
import com.webify.app.ui.theme.Dimens
import com.webify.app.ui.theme.WebifyTheme
import com.webify.app.util.FileActions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Daftar laporan crash yang dikirim aplikasi hasil build debug. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrashReportsScreen(container: AppContainer, onBack: () -> Unit, onOpenCopilot: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { CrashReportStore(ctx) }
    var reports by remember { mutableStateOf(store.list()) }
    var expanded by remember { mutableStateOf(reports.firstOrNull()?.file?.name) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", Modifier.size(Dimens.IconBar)) }
                },
                title = { Text("Laporan Crash", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (reports.isNotEmpty()) {
                        IconButton(onClick = { store.clear(); reports = emptyList() }) {
                            Icon(Icons.Rounded.DeleteSweep, "Hapus semua", Modifier.size(Dimens.IconBar))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        if (reports.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    Icons.Rounded.BugReport, "Belum ada laporan crash",
                    "Aplikasi hasil Debug APK yang crash akan menawarkan \"Kirim ke Webify\" saat dibuka lagi. Laporannya muncul di sini."
                )
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(reports, key = { it.file.name }) { r ->
                CrashCard(
                    r, expanded = expanded == r.file.name,
                    onToggle = { expanded = if (expanded == r.file.name) null else r.file.name },
                    onAnalyze = { scope.launch {
                        val projectId = runCatching { container.projectRepository.findByPackage(r.packageName)?.id }.getOrNull()
                        container.copilotBus.post(
                            CopilotRequest(
                                projectId,
                                "Aplikasi Android hasil Webify (WebView) dengan package ${r.packageName} mengalami crash. " +
                                    "Jelaskan penyebab paling mungkin dalam bahasa Indonesia sederhana, lalu beri langkah perbaikan konkret " +
                                    "yang bisa dilakukan lewat pengaturan Webify (izin, plugin, JS/CSS kustom, minSdk) atau di website.\n\n" +
                                    "```\n${r.text.take(8000)}\n```",
                                "🐞 Analisis crash ${r.packageName}"
                            )
                        )
                        onOpenCopilot()
                    } },
                    onCopy = { FileActions.copy(ctx, "Crash", r.text) },
                    onShare = {
                        ctx.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_SUBJECT, "Crash ${r.packageName}")
                                    .putExtra(Intent.EXTRA_TEXT, r.text),
                                "Bagikan laporan"
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onDelete = { store.delete(r); reports = store.list() }
                )
            }
        }
    }
}

@Composable
private fun CrashCard(
    r: CrashReport,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAnalyze: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val time = remember(r.timestamp) { SimpleDateFormat("dd MMM yyyy HH:mm", Locale("id")).format(Date(r.timestamp)) }
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(Dimens.RadiusCard),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.BugReport, null, Modifier.size(Dimens.IconInline), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(Dimens.IconTextGap))
                Column(Modifier.weight(1f)) {
                    Text(r.packageName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(Dimens.IconInline))
            }
            Text(r.headline, style = WebifyTheme.mono.copy(fontSize = 12.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.error,
                maxLines = if (expanded) 4 else 2, overflow = TextOverflow.Ellipsis)
            if (expanded) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(Dimens.RadiusSmall)) {
                    Text(
                        r.text.take(6000),
                        style = WebifyTheme.mono.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(10.dp)
                    )
                }
                SecondaryButton("Analisis dengan AI", onClick = onAnalyze, icon = Icons.Rounded.AutoAwesome, tonal = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Salin", onClick = onCopy, icon = Icons.Rounded.ContentCopy, modifier = Modifier.weight(1f))
                    SecondaryButton("Bagikan", onClick = onShare, icon = Icons.Rounded.Share, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Hapus", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
