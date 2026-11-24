package com.webify.app.ui.dashboard

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.webify.app.data.web.WebsiteMetadata
import com.webify.app.data.web.WebsiteMetadataExtractor
import com.webify.app.domain.Validators
import com.webify.app.ui.DashboardViewModel
import com.webify.app.ui.components.LabeledField
import com.webify.app.ui.components.PrimaryButton
import com.webify.app.ui.components.SecondaryButton
import com.webify.app.ui.components.parseHexColor
import com.webify.app.ui.theme.Dimens
import kotlinx.coroutines.launch
import java.io.File

/** Hasil wizard proyek baru. */
data class WizardResult(
    val name: String,
    val url: String,
    val packageName: String,
    val template: ProjectTemplate,
    val meta: WebsiteMetadata?,
    val buildNow: Boolean
)

/**
 * Wizard 3 langkah: (1) URL website → (2) ambil info otomatis & koreksi → (3) template → buat / langsung build.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewProjectWizard(onDismiss: () -> Unit, onFinish: (WizardResult) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var url by rememberSaveable { mutableStateOf("https://") }
    var name by rememberSaveable { mutableStateOf("") }
    var pkg by rememberSaveable { mutableStateOf("") }
    var pkgEdited by rememberSaveable { mutableStateOf(false) }
    var template by rememberSaveable { mutableStateOf(ProjectTemplate.BLANK) }
    var meta by remember { mutableStateOf<WebsiteMetadata?>(null) }
    var fetching by remember { mutableStateOf(false) }
    var fetchError by remember { mutableStateOf<String?>(null) }

    val urlError = Validators.urlError(url)
    val effectivePkg = if (pkgEdited) pkg else DashboardViewModel.suggestPackage(name)
    val pkgError = Validators.packageNameError(effectivePkg)

    fun fetchInfo() {
        fetching = true
        fetchError = null
        step = 1
        scope.launch {
            try {
                val target = File(ctx.cacheDir, "wizard_icon_${System.currentTimeMillis()}")
                val m = WebsiteMetadataExtractor().extract(url.trim(), target)
                meta = m
                if (name.isBlank()) m.displayName?.let { name = it }
            } catch (e: Exception) {
                fetchError = e.message ?: "Gagal mengambil info"
                if (name.isBlank()) name = runCatching { java.net.URI(url.trim()).host.removePrefix("www.").substringBefore('.') }
                    .getOrNull()?.replaceFirstChar { it.uppercase() }.orEmpty()
            } finally {
                fetching = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { if (step == 0) onDismiss() else step-- }) {
                            Icon(if (step == 0) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", Modifier.size(Dimens.IconBar))
                        }
                    },
                    title = {
                        Column {
                            Text("Proyek Baru", style = MaterialTheme.typography.titleLarge)
                            Text("Langkah ${step + 1} dari 3 • " + listOf("Website", "Identitas", "Template")[step],
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
                    Column(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = Dimens.ScreenPadding, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (step) {
                            0 -> PrimaryButton("Lanjut & Ambil Info Website", onClick = { fetchInfo() }, icon = Icons.Rounded.AutoAwesome,
                                enabled = urlError == null, modifier = Modifier.fillMaxWidth())
                            1 -> PrimaryButton("Lanjut", onClick = { step = 2 }, enabled = !fetching && name.isNotBlank() && pkgError == null,
                                modifier = Modifier.fillMaxWidth())
                            else -> {
                                PrimaryButton("Buat & Build Debug APK", onClick = {
                                    onFinish(WizardResult(name.trim(), url.trim(), effectivePkg, template, meta, buildNow = true))
                                }, icon = Icons.Rounded.Bolt, modifier = Modifier.fillMaxWidth())
                                SecondaryButton("Buat & Buka di Studio", onClick = {
                                    onFinish(WizardResult(name.trim(), url.trim(), effectivePkg, template, meta, buildNow = false))
                                }, icon = Icons.Rounded.Edit, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally { it / 6 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 6 * dir } + fadeOut())
                    },
                    label = "wizardStep"
                ) { s ->
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.ScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)
                    ) {
                        when (s) {
                            0 -> {
                                Icon(Icons.Rounded.Language, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("Website apa yang ingin dijadikan aplikasi?", style = MaterialTheme.typography.titleMedium)
                                Text("Webify akan membaca nama, warna tema, dan ikon dari website secara otomatis.",
                                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LabeledField("URL website", url, { url = it.trim() }, error = if (url.length > 8) urlError else null,
                                    mono = true, keyboardType = KeyboardType.Uri, placeholder = "https://toko-saya.com")
                                Text("Gunakan hanya website milik Anda sendiri — aplikasi yang membungkus situs/merek pihak lain ditolak Google Play.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            1 -> IdentityStep(
                                fetching = fetching, fetchError = fetchError, meta = meta,
                                name = name, onName = { name = it },
                                pkg = effectivePkg, pkgError = if (effectivePkg.isNotEmpty()) pkgError else null,
                                onPkg = { pkg = it.trim().lowercase(); pkgEdited = true }
                            )
                            else -> {
                                Text("Pilih titik awal", style = MaterialTheme.typography.titleMedium)
                                ProjectTemplate.entries.forEach { t ->
                                    val sel = t == template
                                    Surface(
                                        onClick = { template = t },
                                        shape = RoundedCornerShape(Dimens.RadiusControl),
                                        color = if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                        border = BorderStroke(1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(t.icon, null, Modifier.size(Dimens.IconBar), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(Dimens.IconTextGap))
                                            Column(Modifier.weight(1f)) {
                                                Text(t.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                                Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                            RadioButton(selected = sel, onClick = { template = t })
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityStep(
    fetching: Boolean,
    fetchError: String?,
    meta: WebsiteMetadata?,
    name: String,
    onName: (String) -> Unit,
    pkg: String,
    pkgError: String?,
    onPkg: (String) -> Unit
) {
    if (fetching) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 24.dp)) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(16.dp))
            Text("Membaca manifest, meta tag, dan ikon website…", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val icon = remember(meta?.iconFile) {
        meta?.iconFile?.takeIf { it.exists() }?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath)?.asImageBitmap() }.getOrNull() }
    }
    Surface(
        color = if (fetchError == null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(Dimens.RadiusControl), modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (fetchError == null) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, null, Modifier.size(Dimens.IconInline))
            Spacer(Modifier.width(Dimens.IconTextGap))
            Text(
                fetchError?.let { "Info website tidak bisa diambil ($it). Isi manual di bawah." }
                    ?: ("Info website terbaca" + if (meta?.manifestFound == true) " dari manifest PWA" else " dari meta tag"),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f)
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                .background(parseHexColor(meta?.backgroundColor ?: meta?.themeColor ?: "") ?: MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) Image(icon, null, Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)))
            else Text(name.take(1).uppercase().ifEmpty { "?" }, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(name.ifBlank { "Nama aplikasi" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            meta?.themeColor?.let { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(parseHexColor(c) ?: MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(6.dp))
                    Text("Warna tema $c", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    LabeledField("Nama aplikasi", name, onName, placeholder = "Toko Saya", supporting = "${name.length}/30")
    LabeledField("Package name", pkg, onPkg, error = pkgError, mono = true, supporting = "ID unik di Play Store, tidak bisa diubah setelah rilis")
}
