package com.webify.app.ui.console

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.VerticalAlignBottom
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.webify.app.ui.components.PrimaryButton
import com.webify.app.ui.components.SecondaryButton
import com.webify.app.ui.theme.Dimens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webify.app.AppContainer
import com.webify.app.ai.CopilotRequest
import com.webify.app.ai.PromptBuilder
import com.webify.app.build.BuildResult
import com.webify.app.build.BuildSession
import com.webify.app.build.BuildStage
import com.webify.app.build.LogLevel
import com.webify.app.build.LogLine
import com.webify.app.domain.BuildType
import com.webify.app.ui.components.EmptyState
import com.webify.app.ui.theme.TerminalColors
import com.webify.app.ui.theme.WebifyTheme
import com.webify.app.util.FileActions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildConsoleScreen(container: AppContainer, onBack: () -> Unit, onAnalyzeWithAi: () -> Unit) {
    val session by container.buildManager.session.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                title = {
                    Column {
                        Text("Live Build Console", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        session?.let {
                            Text(
                                "${it.projectName} • ${it.buildType.label}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    val s = session
                    if (s != null) {
                        IconButton(onClick = { FileActions.copy(ctx, "Log build", s.logs.joinToString("\n") { it.format() }) }) {
                            Icon(Icons.Rounded.ContentCopy, "Salin log", Modifier.size(20.dp))
                        }
                        if (s.running) IconButton(onClick = { container.buildManager.cancel() }) {
                            Icon(Icons.Rounded.Stop, "Batalkan", tint = MaterialTheme.colorScheme.error)
                        } else IconButton(onClick = { container.buildManager.dismiss(); onBack() }) {
                            Icon(Icons.Rounded.Close, "Tutup")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        val s = session
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Rounded.Terminal, "Tidak ada build aktif", "Mulai build dari Builder Studio atau kartu proyek.")
            }
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = Dimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap)) {
            ProgressHeader(s)
            StageStepper(s)
            TerminalPanel(s.logs, Modifier.weight(1f))
            AnimatedVisibility(visible = s.result != null) {
                s.result?.let { r ->
                    PostBuildCard(
                        r, s,
                        onAnalyze = {
                            val f = r as BuildResult.Failure
                            container.copilotBus.post(
                                CopilotRequest(
                                    s.projectId,
                                    PromptBuilder.errorDoctor(f.stage?.label ?: "-", f.summary, s.logs.takeLast(120).joinToString("\n") { it.format() }),
                                    "🩺 Analisis error build: ${f.stage?.label ?: "-"} — ${f.summary.lineSequence().first().take(120)}"
                                )
                            )
                            onAnalyzeWithAi()
                        },
                        onRetry = { container.buildManager.start(s.projectId, s.buildType) }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ProgressHeader(s: BuildSession) {
    val failed = s.result is BuildResult.Failure
    val color = when {
        failed -> MaterialTheme.colorScheme.error
        s.result is BuildResult.Success -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    s.running -> s.activeStage?.let { "Tahap ${it.ordinal + 1}/${BuildStage.entries.size} • ${it.label}" } ?: "Menyiapkan…"
                    failed -> "Build gagal"
                    s.result is BuildResult.Success -> "Build berhasil"
                    else -> "Dihentikan"
                },
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            )
            Text("${s.percent}%", style = WebifyTheme.mono.copy(fontWeight = FontWeight.Bold), color = color)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { s.percent / 100f },
            color = color,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
        )
    }
}

/** Stepper 7 tahap yang ringkas: titik 22dp + label 10sp, tinggi total ±44dp. */
@Composable
private fun StageStepper(s: BuildSession) {
    val pulse = rememberInfiniteTransition(label = "pulse").animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        BuildStage.entries.forEachIndexed { i, st ->
            val done = st in s.completedStages
            val active = st == s.activeStage && s.running
            val failed = st == s.failedStage
            val c = when {
                failed -> MaterialTheme.colorScheme.error
                done -> MaterialTheme.colorScheme.secondary
                active -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                    // garis penghubung kiri
                    Box(
                        Modifier.weight(1f).height(2.dp).background(
                            if (i == 0) Color.Transparent
                            else if (done || active || failed) c else MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(if (done || failed) c else Color.Transparent)
                            .border(2.dp, c, CircleShape)
                            .alpha(if (active) pulse.value else 1f),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            failed -> Icon(Icons.Rounded.Close, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onError)
                            done -> Icon(Icons.Rounded.Check, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSecondary)
                            else -> Text("${i + 1}", style = WebifyTheme.mono.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold), color = c)
                        }
                    }
                    // garis penghubung kanan
                    Box(
                        Modifier.weight(1f).height(2.dp).background(
                            if (i == BuildStage.entries.lastIndex) Color.Transparent
                            else if (done) c else MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    st.label.substringBefore(' ').let { if (st == BuildStage.PACKAGE) "Sign" else it },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)

@Composable
private fun TerminalPanel(logs: List<LogLine>, modifier: Modifier) {
    val listState = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 2
        }
    }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) follow = atBottom
    }
    LaunchedEffect(logs.size, follow) {
        if (follow && logs.isNotEmpty()) listState.scrollToItem(logs.lastIndex)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = TerminalColors.Background,
        shape = RoundedCornerShape(Dimens.RadiusSmall),
        border = BorderStroke(1.dp, TerminalColors.Border)
    ) {
        Box {
            Column {
                Row(Modifier.fillMaxWidth().background(TerminalColors.Surface).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(Color(0xFFF87171), Color(0xFFFBBF24), Color(0xFF34D399)).forEach {
                        Box(Modifier.padding(end = 6.dp).size(10.dp).clip(CircleShape).background(it))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("webify-build — zsh", style = WebifyTheme.mono.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize), color = TerminalColors.Timestamp)
                    Spacer(Modifier.weight(1f))
                    Text("${logs.size} baris", style = WebifyTheme.mono.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize), color = TerminalColors.Timestamp)
                }
                // JetBrains Mono 11.5sp / line-height 16sp: padat namun tidak dempet
                val mono = WebifyTheme.mono.copy(fontSize = 11.5.sp, lineHeight = 16.sp)
                LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp), modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(logs) { _, line ->
                        val c = when (line.level) {
                            LogLevel.INFO -> TerminalColors.Info
                            LogLevel.WARN -> TerminalColors.Warn
                            LogLevel.ERROR -> TerminalColors.Error
                            LogLevel.SUCCESS -> TerminalColors.Success
                            LogLevel.DEBUG -> TerminalColors.Debug
                        }
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = TerminalColors.Timestamp)) { append(timeFmt.format(Date(line.timestamp))); append("  ") }
                                withStyle(SpanStyle(color = c, fontWeight = FontWeight.Bold)) { append(line.level.name.padEnd(7)) }
                                withStyle(SpanStyle(color = if (line.level == LogLevel.DEBUG) TerminalColors.Debug else TerminalColors.Text)) { append(" "); append(line.message) }
                            },
                            style = mono
                        )
                    }
                }
            }
            if (!follow) {
                FilledTonalButton(
                    onClick = { follow = true },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Rounded.VerticalAlignBottom, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ikuti log")
                }
            }
        }
    }
}

@Composable
private fun PostBuildCard(r: BuildResult, s: BuildSession, onAnalyze: () -> Unit, onRetry: () -> Unit) {
    val ctx = LocalContext.current
    when (r) {
        is BuildResult.Success -> {
            val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                if (uri != null) FileActions.toast(ctx, if (FileActions.exportTo(ctx, r.outputFile, uri)) "Diekspor ke penyimpanan" else "Ekspor gagal")
            }
            Surface(
                shape = RoundedCornerShape(Dimens.RadiusCard),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(Dimens.IconBar), tint = MaterialTheme.colorScheme.secondary)
                        Spacer(Modifier.width(Dimens.IconTextGap))
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(r.outputFile.name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${FileActions.humanSize(r.sizeBytes)} • ${"%.1f".format(r.durationMs / 1000.0)} s",
                                style = WebifyTheme.mono.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize), color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                    // Instal & Buka: pantau status pemasangan selama kartu tampil
                    val apkMeta = remember(r.outputFile) { if (r.buildType.isBundle) null else FileActions.apkInfo(ctx, r.outputFile) }
                    var installed by remember { mutableStateOf<FileActions.InstalledInfo?>(null) }
                    LaunchedEffect(apkMeta) {
                        while (apkMeta != null) {
                            installed = FileActions.installedInfo(ctx, apkMeta.first)
                            kotlinx.coroutines.delay(1500)
                        }
                    }
                    val upToDate = apkMeta != null && installed != null && installed!!.versionCode == apkMeta.second &&
                        installed!!.lastUpdate >= r.outputFile.lastModified() - 2000
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (r.buildType != BuildType.AAB_PLAYSTORE) {
                            if (upToDate) {
                                PrimaryButton("Buka Aplikasi", onClick = { FileActions.launchApp(ctx, apkMeta!!.first) }, icon = Icons.Rounded.PlayArrow, modifier = Modifier.weight(1f))
                            } else {
                                PrimaryButton(if (installed != null) "Perbarui APK" else "Install APK", onClick = { FileActions.installApk(ctx, r.outputFile) }, icon = Icons.Rounded.InstallMobile, modifier = Modifier.weight(1f))
                            }
                        }
                        SecondaryButton(
                            if (r.buildType == BuildType.AAB_PLAYSTORE) "Share AAB" else "Share",
                            onClick = { FileActions.share(ctx, r.outputFile) }, icon = Icons.Rounded.Share,
                            modifier = Modifier.weight(1f).height(Dimens.PrimaryButtonHeight)
                        )
                    }
                    SecondaryButton("Export ke penyimpanan", onClick = { exporter.launch(r.outputFile.name) }, icon = Icons.Rounded.Download, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        is BuildResult.Failure -> {
            Surface(shape = RoundedCornerShape(Dimens.RadiusCard), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(if (r.summary == "Dibatalkan") Icons.Rounded.Cancel else Icons.Rounded.Error, null, Modifier.size(Dimens.IconBar), tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(Dimens.IconTextGap))
                        Column(Modifier.weight(1f)) {
                            Text("Gagal di tahap ${r.stage?.label ?: "-"}", style = MaterialTheme.typography.titleSmall)
                            Text(r.summary.take(400), style = WebifyTheme.mono.copy(fontSize = 11.sp, lineHeight = 16.sp), maxLines = 6, overflow = TextOverflow.Ellipsis)
                            r.compileErrors.filter { it.isError }.take(3).forEach {
                                Text("• ${it.file}:${it.line} ${it.message}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (r.summary != "Dibatalkan") {
                            PrimaryButton(
                                "Analisis dengan AI", onClick = onAnalyze, icon = Icons.Rounded.AutoAwesome, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)
                            )
                        }
                        SecondaryButton("Ulangi", onClick = onRetry, modifier = Modifier.height(Dimens.PrimaryButtonHeight))
                    }
                }
            }
        }
    }
}
