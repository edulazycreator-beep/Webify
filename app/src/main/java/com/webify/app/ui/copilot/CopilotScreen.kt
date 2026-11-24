package com.webify.app.ui.copilot

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webify.app.AppContainer
import com.webify.app.ai.AiAssistantViewModel
import com.webify.app.ai.AiUiState
import com.webify.app.ai.CodeBlock
import com.webify.app.ai.MarkdownParser
import com.webify.app.ai.MessagePart
import com.webify.app.ai.ModelListState
import com.webify.app.data.prefs.AiProvider
import com.webify.app.data.prefs.AiSettings
import com.webify.app.domain.ChatRole
import com.webify.app.ui.appViewModel
import com.webify.app.ui.components.LabeledField
import com.webify.app.ui.components.PrimaryButton
import com.webify.app.ui.components.SecondaryButton
import com.webify.app.ui.dashboard.ProjectIcon
import com.webify.app.ui.theme.Dimens
import com.webify.app.ui.theme.TerminalColors
import com.webify.app.ui.theme.WebifyTheme
import com.webify.app.util.FileActions
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private enum class InputMode(val placeholder: String) {
    CHAT("Tanyakan apa saja tentang proyek Anda…"),
    HIDE_ELEMENT("Elemen apa yang ingin disembunyikan?"),
    CSS_JS("Jelaskan perubahan tampilan/perilaku website…"),
    BRIDGE("Fitur native apa yang ingin dipanggil dari website?")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CopilotScreen(container: AppContainer) {
    val vm = appViewModel { AiAssistantViewModel(it.aiService, it.chatRepository, it.projectRepository, it.securePrefs, it.copilotBus) }
    val state by vm.state.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val pending by container.copilotBus.pending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var input by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(InputMode.CHAT) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Gelembung pesan maksimal 85% lebar layar
    val maxBubble = (LocalConfiguration.current.screenWidthDp * 0.85f).dp

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(pending) { if (pending != null) vm.consumePending() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar("AI: $it"); vm.dismissError() } }
    val itemCount = state.messages.size + (if (state.isStreaming) 1 else 0) + (if (state.messages.isEmpty() && !state.isStreaming) 1 else 0)
    LaunchedEffect(itemCount, state.streamingText.length / 200) {
        if (itemCount > 0) listState.animateScrollToItem(itemCount)
    }

    fun submit() {
        val t = input.trim()
        if (t.isEmpty()) return
        when (mode) {
            InputMode.CHAT -> vm.send(t)
            InputMode.HIDE_ELEMENT -> vm.quickCssJs("Sembunyikan elemen berikut dari website menggunakan CSS (dan JS bila perlu untuk elemen dinamis): $t")
            InputMode.CSS_JS -> vm.quickCssJs(t)
            InputMode.BRIDGE -> vm.quickBridge(t)
        }
        input = ""
        mode = InputMode.CHAT
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).clip(RoundedCornerShape(Dimens.RadiusSmall)).background(
                                Brush.linearGradient(listOf(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.primary))
                            ), contentAlignment = Alignment.Center
                        ) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiary) }
                        Spacer(Modifier.width(Dimens.IconTextGap))
                        Column {
                            Text("AI Co-Pilot", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                            Text(
                                "${state.settings.provider.label} • ${state.settings.model}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.clearConversation() }) { Icon(Icons.Rounded.DeleteSweep, "Hapus percakapan", Modifier.size(Dimens.IconBar)) }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Rounded.Settings, "Pengaturan AI", Modifier.size(Dimens.IconBar)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ProjectSelector(state, onSelect = vm::selectProject)
            if (!state.settings.isConfigured) {
                Surface(
                    onClick = { showSettings = true },
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(Dimens.RadiusControl),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding, vertical = 4.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Key, null, Modifier.size(Dimens.IconInline), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(Dimens.IconTextGap))
                        Text(
                            "Masukkan API key Gemini/OpenAI atau endpoint kustom untuk mengaktifkan Co-Pilot.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap)
            ) {
                if (state.messages.isEmpty() && !state.isStreaming) {
                    item(key = "welcome") { Welcome() }
                }
                items(state.messages, key = { it.id }) { m ->
                    if (m.role == ChatRole.USER) UserBubble(m.content, maxBubble)
                    else AssistantMessage(m.content, maxBubble, canApply = state.activeProject != null, onApply = { b, append -> vm.applyCode(b, append) })
                }
                if (state.isStreaming) {
                    item(key = "streaming") {
                        AssistantMessage(state.streamingText.ifEmpty { "▍" }, maxBubble, canApply = false, onApply = { _, _ -> }, streaming = true)
                    }
                }
                item(key = "bottom") { Spacer(Modifier.height(4.dp)) }
            }
            // Area input bawah: chips + field — terlindung dari keyboard & navigation bar
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding()) {
                PromptChips(
                    onHide = { mode = InputMode.HIDE_ELEMENT },
                    onCss = { mode = InputMode.CSS_JS },
                    onAso = { vm.quickAso() },
                    onPrivacy = { vm.quickPrivacy() },
                    onBridge = { mode = InputMode.BRIDGE },
                    enabled = !state.isStreaming
                )
                InputBar(
                    value = input, onChange = { input = it }, mode = mode,
                    onClearMode = { mode = InputMode.CHAT },
                    streaming = state.isStreaming,
                    onSend = ::submit, onStop = vm::stop
                )
            }
        }
    }

    if (showSettings) AiSettingsSheet(
        initial = state.settings,
        models = models,
        settingsFor = vm::settingsFor,
        onLoadCached = vm::loadCachedModels,
        onRefreshModels = { s, silent -> vm.refreshModels(s, silent) },
        onDismiss = { showSettings = false },
        onSave = { vm.saveSettings(it); showSettings = false },
        onTest = { vm.testSettings(it) }
    )
}

@Composable
private fun ProjectSelector(state: AiUiState, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(horizontal = Dimens.ScreenPadding, vertical = 4.dp)) {
        FilterChip(
            selected = state.activeProject != null,
            onClick = { open = true },
            label = {
                Text(
                    state.activeProject?.let { "Konteks: ${it.appName}" } ?: "Tanpa konteks proyek",
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            },
            leadingIcon = { state.activeProject?.let { ProjectIcon(it, 18) } },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(18.dp)) }
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Tanpa konteks proyek") }, onClick = { onSelect(null); open = false })
            state.projects.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.appName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { ProjectIcon(p, 24) },
                    trailingIcon = { if (p.id == state.activeProject?.id) Icon(Icons.Rounded.Check, null, Modifier.size(Dimens.IconInline)) },
                    onClick = { onSelect(p.id); open = false }
                )
            }
        }
    }
}

@Composable
private fun Welcome() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.tertiary) }
        Text("Co-Pilot siap membantu", style = MaterialTheme.typography.titleMedium)
        Text(
            "Buat CSS/JS injeksi, diagnosis error build, materi listing Play Store, kebijakan privasi, dan integrasi WebifyBridge.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun UserBubble(text: String, maxWidth: Dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            modifier = Modifier.widthIn(max = maxWidth)
        ) {
            Text(
                text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessage(text: String, maxWidth: Dp, canApply: Boolean, onApply: (CodeBlock, Boolean) -> Unit, streaming: Boolean = false) {
    val parts = remember(text) { MarkdownParser.split(text) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiary) }
        Spacer(Modifier.width(Dimens.IconTextGapSmall))
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.widthIn(max = maxWidth - 36.dp)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                parts.forEach { part ->
                    when (part) {
                        is MessagePart.Text -> SelectionContainer { MarkdownText(part.markdown) }
                        is MessagePart.Code -> CodeCard(part.block, canApply && !streaming, onApply)
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownText(md: String) {
    val codeBg = MaterialTheme.colorScheme.surfaceContainer
    val primary = MaterialTheme.colorScheme.primary
    val mono = WebifyTheme.fonts.mono
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        md.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.isBlank() -> Spacer(Modifier.height(2.dp))
                line.startsWith("### ") -> Text(inline(line.removePrefix("### "), codeBg, primary, mono), style = MaterialTheme.typography.titleSmall)
                line.startsWith("## ") -> Text(inline(line.removePrefix("## "), codeBg, primary, mono), style = MaterialTheme.typography.titleMedium)
                line.startsWith("# ") -> Text(inline(line.removePrefix("# "), codeBg, primary, mono), style = MaterialTheme.typography.titleMedium)
                line.trimStart().matches(Regex("^[-*•]\\s+.*")) -> {
                    val indent = (line.length - line.trimStart().length) / 2
                    Row(Modifier.padding(start = (indent * 12).dp)) {
                        Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.width(14.dp))
                        Text(inline(line.trimStart().drop(1).trimStart(), codeBg, primary, mono), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                line.trimStart().matches(Regex("^\\d+[.)]\\s+.*")) -> {
                    val num = line.trimStart().takeWhile { it.isDigit() }
                    Row {
                        Text("$num.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.width(22.dp))
                        Text(inline(line.trimStart().dropWhile { it.isDigit() }.drop(1).trimStart(), codeBg, primary, mono), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                line.startsWith("> ") -> Text(
                    inline(line.removePrefix("> "), codeBg, primary, mono),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                line.matches(Regex("^-{3,}$")) -> Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                else -> Text(inline(line, codeBg, primary, mono), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Markdown inline: **tebal**, *miring* / _miring_, `kode`, [tautan](url). */
private fun inline(text: String, codeBg: Color, link: Color, mono: FontFamily): AnnotatedString =
    buildAnnotatedString {
        val pattern = Regex("""\*\*(.+?)\*\*|`([^`]+)`|\[([^\]]+)]\(([^)]+)\)|(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])|(?<!\w)_(?!\s)(.+?)(?<!\s)_(?!\w)""")
        var idx = 0
        for (m in pattern.findAll(text)) {
            append(text.substring(idx, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(g[1]) }
                g[2].isNotEmpty() -> withStyle(SpanStyle(fontFamily = mono, background = codeBg, fontSize = 13.sp)) { append(" ${g[2]} ") }
                g[3].isNotEmpty() -> withStyle(SpanStyle(color = link, textDecoration = TextDecoration.Underline)) { append(g[3]) }
                g[5].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[5]) }
                g[6].isNotEmpty() -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(g[6]) }
                else -> append(m.value)
            }
            idx = m.range.last + 1
        }
        if (idx < text.length) append(text.substring(idx))
    }

/** Blok kode: header (label bahasa kiri • salin + "Terapkan ke Proyek" kanan), isi mono, radius 8dp. */
@Composable
private fun CodeCard(block: CodeBlock, canApply: Boolean, onApply: (CodeBlock, Boolean) -> Unit) {
    val ctx = LocalContext.current
    val target = block.target
    var menu by remember { mutableStateOf(false) }
    Surface(
        color = TerminalColors.Background,
        shape = RoundedCornerShape(Dimens.RadiusSmall),
        border = BorderStroke(1.dp, TerminalColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(TerminalColors.Surface).heightIn(min = 40.dp).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (block.language.lowercase()) { "js", "javascript" -> "JavaScript"; "css" -> "CSS"; else -> block.language.uppercase() },
                    style = WebifyTheme.mono.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold),
                    color = TerminalColors.Info, maxLines = 1, modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                IconButton(onClick = { FileActions.copy(ctx, "Kode", block.code) }, modifier = Modifier.size(Dimens.IconButtonSmall)) {
                    Icon(Icons.Rounded.ContentCopy, "Salin", Modifier.size(16.dp), tint = TerminalColors.Timestamp)
                }
                if (target != null && canApply) {
                    Box {
                        Button(
                            onClick = { onApply(block, false) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary),
                            shape = RoundedCornerShape(Dimens.RadiusSmall),
                            contentPadding = PaddingValues(horizontal = 10.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Rounded.Check, null, Modifier.size(Dimens.IconChip))
                            Spacer(Modifier.width(4.dp))
                            Text("Terapkan ke Proyek", fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Tambahkan di akhir kode lama") }, onClick = { onApply(block, true); menu = false })
                        }
                    }
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(Dimens.IconButtonSmall)) {
                        Icon(Icons.Rounded.ArrowDropDown, "Opsi terapkan", Modifier.size(18.dp), tint = TerminalColors.Timestamp)
                    }
                }
            }
            SelectionContainer {
                Text(
                    block.code,
                    style = WebifyTheme.mono.copy(fontSize = 12.sp, lineHeight = 17.sp),
                    color = TerminalColors.Text,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)
                )
            }
            if (target != null && canApply) {
                Text(
                    "→ ${target.label}", style = MaterialTheme.typography.labelSmall, color = TerminalColors.Timestamp,
                    modifier = Modifier.padding(start = 12.dp, bottom = 10.dp)
                )
            }
        }
    }
}

private data class PromptChip(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val onClick: () -> Unit)

@Composable
private fun PromptChips(onHide: () -> Unit, onCss: () -> Unit, onAso: () -> Unit, onPrivacy: () -> Unit, onBridge: () -> Unit, enabled: Boolean) {
    val colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.tertiary)
    val chips = listOf(
        PromptChip("Buat JS Sembunyikan Elemen", Icons.Rounded.VisibilityOff, onHide),
        PromptChip("Smart CSS & JS", Icons.Rounded.AutoAwesome, onCss),
        PromptChip("Generate Deskripsi Play Store", Icons.Rounded.Storefront, onAso),
        PromptChip("Buat Privacy Policy", Icons.Rounded.Policy, onPrivacy),
        PromptChip("Bridge Advisor", Icons.Rounded.Hub, onBridge)
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Dimens.ScreenPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chips) { c ->
            AssistChip(
                onClick = c.onClick, enabled = enabled,
                label = { Text(c.label, maxLines = 1) },
                leadingIcon = { Icon(c.icon, null, Modifier.size(Dimens.IconChip)) },
                colors = colors
            )
        }
    }
}

@Composable
private fun InputBar(
    value: String, onChange: (String) -> Unit, mode: InputMode, onClearMode: () -> Unit,
    streaming: Boolean, onSend: () -> Unit, onStop: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (mode != InputMode.CHAT) {
            InputChip(
                selected = true, onClick = onClearMode,
                label = {
                    Text(
                        when (mode) {
                            InputMode.HIDE_ELEMENT -> "Mode: Sembunyikan elemen"
                            InputMode.CSS_JS -> "Mode: Smart CSS & JS"
                            InputMode.BRIDGE -> "Mode: Bridge Advisor"
                            InputMode.CHAT -> ""
                        }, maxLines = 1
                    )
                },
                trailingIcon = { Text("✕", fontSize = 12.sp) }
            )
        }
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = value, onValueChange = onChange,
                placeholder = { Text(mode.placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
                maxLines = 5,
                shape = RoundedCornerShape(Dimens.RadiusControl)
            )
            Spacer(Modifier.width(8.dp))
            if (streaming) {
                FilledIconButton(
                    onClick = onStop, modifier = Modifier.size(52.dp), shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Icon(Icons.Rounded.Stop, "Hentikan", Modifier.size(Dimens.IconBar)) }
            } else {
                FilledIconButton(
                    onClick = onSend, enabled = value.isNotBlank(), modifier = Modifier.size(52.dp), shape = RoundedCornerShape(Dimens.RadiusControl),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary)
                ) { Icon(Icons.AutoMirrored.Rounded.Send, "Kirim", Modifier.size(Dimens.IconInline)) }
            }
        }
    }
}

/**
 * Pengaturan provider AI + pemilih model dengan daftar model yang diambil otomatis
 * dari API (saat sheet dibuka, provider/kunci/base URL berubah) atau manual via tombol refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiSettingsSheet(
    initial: AiSettings,
    models: ModelListState,
    settingsFor: (AiProvider) -> AiSettings,
    onLoadCached: (AiSettings) -> Unit,
    onRefreshModels: (AiSettings, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: (AiSettings) -> Unit,
    onTest: (AiSettings) -> Unit
) {
    var s by remember { mutableStateOf(initial) }
    var modelMenu by remember { mutableStateOf(false) }

    // Muat cache untuk provider/base URL aktif, lalu ambil otomatis (debounce 700 ms) bila kunci tersedia.
    LaunchedEffect(s.provider, s.baseUrl) { onLoadCached(s) }
    LaunchedEffect(s.provider, s.apiKey, s.baseUrl) {
        if (s.provider == AiProvider.CUSTOM || s.apiKey.length >= 10) {
            delay(700)
            onRefreshModels(s, true)
        }
    }

    val relevant = models.provider == s.provider && models.baseUrl.trimEnd('/') == s.baseUrl.trimEnd('/')
    val list = if (relevant) models.models else emptyList()
    val filtered = remember(list, s.model) {
        val q = s.model.trim()
        if (q.isEmpty() || list.contains(q)) list else list.filter { it.contains(q, ignoreCase = true) }.ifEmpty { list }
    }
    val modelUnknown = relevant && list.isNotEmpty() && s.model.isNotBlank() && s.model.trim() !in list

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Dimens.RadiusCard, topEnd = Dimens.RadiusCard)
    ) {
        Column(
            Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)
        ) {
            Text("Pengaturan AI", style = MaterialTheme.typography.titleLarge)
            Text(
                "API key disimpan terenkripsi (EncryptedSharedPreferences + Android Keystore).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AiProvider.entries) { p ->
                    FilterChip(selected = s.provider == p, onClick = { s = settingsFor(p) }, label = { Text(p.label, maxLines = 1) })
                }
            }
            OutlinedTextField(
                value = s.apiKey, onValueChange = { s = s.copy(apiKey = it.trim()) },
                label = { Text(if (s.provider == AiProvider.CUSTOM) "API key (opsional)" else "API key") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(Dimens.RadiusControl)
            )
            LabeledField("Base URL", s.baseUrl, { s = s.copy(baseUrl = it.trim()) }, mono = true)

            // ---------------- Pemilih model ----------------
            Box(Modifier.fillMaxWidth()) {
                LabeledField(
                    label = "Model",
                    value = s.model,
                    onChange = { s = s.copy(model = it.trim()); modelMenu = list.isNotEmpty() },
                    mono = true,
                    error = if (modelUnknown) "Model tidak ada di daftar akun ini" else null,
                    supporting = when {
                        models.loading && relevant -> "Mengambil daftar model…"
                        relevant && models.error != null -> models.error
                        list.isNotEmpty() -> "${list.size} model tersedia" + (models.fetchedAt.takeIf { it > 0 }?.let {
                            " • diperbarui " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))
                        } ?: "")
                        else -> "Ketuk ⟳ untuk mengambil daftar model"
                    },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (list.isNotEmpty()) {
                                IconButton(onClick = { modelMenu = !modelMenu }, modifier = Modifier.size(Dimens.IconButtonSmall)) {
                                    Icon(Icons.Rounded.ArrowDropDown, "Pilih model", Modifier.size(Dimens.IconBar))
                                }
                            }
                            if (models.loading && relevant) {
                                Box(Modifier.size(Dimens.IconButtonSmall), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                            } else {
                                IconButton(onClick = { onRefreshModels(s, false) }, modifier = Modifier.size(Dimens.IconButtonSmall)) {
                                    Icon(Icons.Rounded.Refresh, "Refresh daftar model", Modifier.size(Dimens.IconInline))
                                }
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                )
                DropdownMenu(
                    expanded = modelMenu && filtered.isNotEmpty(),
                    onDismissRequest = { modelMenu = false },
                    modifier = Modifier.heightIn(max = 320.dp)
                ) {
                    filtered.take(200).forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m, style = WebifyTheme.mono.copy(fontSize = 13.sp), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            trailingIcon = { if (m == s.model) Icon(Icons.Rounded.Check, null, Modifier.size(Dimens.IconInline)) },
                            onClick = { s = s.copy(model = m); modelMenu = false }
                        )
                    }
                }
            }
            if (modelUnknown && filtered.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Warning, null, Modifier.size(Dimens.IconChip), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(Dimens.IconTextGapSmall))
                    Text("Pakai ", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { s = s.copy(model = list.first()) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(list.first(), style = WebifyTheme.mono.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Uji koneksi", onClick = { onTest(s) }, modifier = Modifier.weight(1f).height(Dimens.PrimaryButtonHeight))
                PrimaryButton("Simpan", onClick = { onSave(s) }, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
