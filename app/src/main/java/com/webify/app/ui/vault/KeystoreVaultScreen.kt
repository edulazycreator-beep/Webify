package com.webify.app.ui.vault

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import com.webify.app.ui.components.PrimaryButton
import com.webify.app.ui.components.SecondaryButton
import com.webify.app.ui.theme.Dimens
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.webify.app.AppContainer
import com.webify.app.crypto.DistinguishedName
import com.webify.app.crypto.ReleaseKeystoreRequest
import com.webify.app.data.db.KeystoreEntity
import com.webify.app.domain.KeyAlgorithm
import com.webify.app.ui.VaultViewModel
import com.webify.app.ui.appViewModel
import com.webify.app.ui.components.LabeledField
import com.webify.app.ui.components.Pill
import com.webify.app.ui.components.SelectField
import com.webify.app.ui.theme.WebifyTheme
import com.webify.app.util.FileActions
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeystoreVaultScreen(container: AppContainer) {
    val vm = appViewModel { VaultViewModel(it.keystoreManager, it.keystoreRepository) }
    val keystores by vm.keystores.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var showCreate by rememberSaveable { mutableStateOf(false) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var exportTarget by remember { mutableStateOf<KeystoreEntity?>(null) }
    var toDelete by remember { mutableStateOf<KeystoreEntity?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> importUri = uri }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-pkcs12")) { uri ->
        val ks = exportTarget
        if (uri != null && ks != null) vm.export(ks, uri)
        exportTarget = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Rounded.Add, null, Modifier.size(20.dp)) },
                text = { Text("Keystore Baru") }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.ScreenPadding, end = Dimens.ScreenPadding,
                top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.SectionGap)
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Keystore Vault", style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Text(
                            "Kunci penandatanganan digital untuk APK & AAB", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    SecondaryButton("Impor", onClick = { importPicker.launch(arrayOf("*/*")) }, icon = Icons.Rounded.FileUpload)
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(Dimens.RadiusControl), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Simpan cadangan keystore rilis & password-nya di tempat aman. Kehilangan upload key berarti harus mengajukan reset ke Google Play.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(12.dp)
                    )
                }
            }
            items(keystores, key = { it.id }) { ks ->
                KeystoreCard(
                    ks,
                    onCopy = { label, v -> FileActions.copy(ctx, label, v) },
                    onExport = { exportTarget = ks; exporter.launch(ks.name.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".p12") },
                    onDelete = { toDelete = ks }
                )
            }
        }
    }

    if (showCreate) CreateKeystoreSheet(busy = busy, onDismiss = { showCreate = false }) { req -> vm.generate(req) { showCreate = false } }

    importUri?.let { uri -> ImportDialog(uri, vm, onDismiss = { importUri = null }) }

    toDelete?.let { ks ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Hapus ${ks.name}?") },
            text = { Text("File keystore dan password tersimpan akan dihapus dari perangkat. Pastikan Anda memiliki cadangan.") },
            confirmButton = { TextButton(onClick = { vm.delete(ks); toDelete = null }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun KeystoreCard(ks: KeystoreEntity, onCopy: (String, String) -> Unit, onExport: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(Dimens.RadiusCard),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(Dimens.RadiusControl))
                        .background(if (ks.isDebugDefault) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (ks.isDebugDefault) Icons.Rounded.Key else Icons.Rounded.Shield, null, Modifier.size(Dimens.IconBar),
                        tint = if (ks.isDebugDefault) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Spacer(Modifier.width(Dimens.IconTextGap))
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(ks.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "alias: ${ks.keyAlias} • ${ks.keyAlgorithm.label} • ${ks.validityYears} thn",
                        style = WebifyTheme.mono.copy(fontSize = 12.sp, lineHeight = 16.sp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (ks.isDebugDefault) Pill("DEBUG", MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.onSurfaceVariant)
                else Pill("RELEASE", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
            }
            val dn = listOf("CN" to ks.commonName, "OU" to ks.organizationalUnit, "O" to ks.organization, "L" to ks.locality, "ST" to ks.state, "C" to ks.countryCode)
                .filter { it.second.isNotBlank() }.joinToString(", ") { "${it.first}=${it.second}" }
            if (dn.isNotEmpty()) Text(dn, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Fingerprint("SHA-1", ks.sha1Fingerprint, onCopy)
            Fingerprint("SHA-256", ks.sha256Fingerprint, onCopy)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Dibuat " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ks.createdAt)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = 12.dp), maxLines = 1
                )
                SecondaryButton("Backup", onClick = onExport, icon = Icons.Rounded.Download)
                if (!ks.isDebugDefault) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Delete, "Hapus", Modifier.size(Dimens.IconInline), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun Fingerprint(label: String, value: String, onCopy: (String, String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(Dimens.RadiusSmall), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Fingerprint, null, Modifier.size(Dimens.IconInline), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(Dimens.IconTextGap))
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
                // Mono 11sp; hash dibiarkan wrap ke baris berikut agar tidak terpotong
                Text(value, style = WebifyTheme.mono.copy(fontSize = 11.sp, lineHeight = 15.sp))
            }
            IconButton(onClick = { onCopy(label, value) }, modifier = Modifier.size(Dimens.IconButtonSmall)) {
                Icon(Icons.Rounded.ContentCopy, "Salin $label", Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateKeystoreSheet(busy: Boolean, onDismiss: () -> Unit, onCreate: (ReleaseKeystoreRequest) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf("") }
    var storePw by rememberSaveable { mutableStateOf("") }
    var alias by rememberSaveable { mutableStateOf("upload") }
    var keyPw by rememberSaveable { mutableStateOf("") }
    var samePw by rememberSaveable { mutableStateOf(true) }
    var algorithm by rememberSaveable { mutableStateOf(KeyAlgorithm.RSA_2048) }
    var years by rememberSaveable { mutableIntStateOf(30) }
    var cn by rememberSaveable { mutableStateOf("") }
    var ou by rememberSaveable { mutableStateOf("") }
    var o by rememberSaveable { mutableStateOf("") }
    var l by rememberSaveable { mutableStateOf("") }
    var st by rememberSaveable { mutableStateOf("") }
    var c by rememberSaveable { mutableStateOf("ID") }
    var showPw by remember { mutableStateOf(false) }
    val effectiveKeyPw = if (samePw) storePw else keyPw
    val valid = name.isNotBlank() && storePw.length >= 6 && effectiveKeyPw.length >= 6 && alias.isNotBlank() && cn.isNotBlank() &&
        (c.isBlank() || c.length == 2)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)
        ) {
            Text("Keystore Rilis Baru", style = MaterialTheme.typography.titleLarge)
            Text("Sertifikat X.509 self-signed (SHA256withRSA) untuk Google Play upload key.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LabeledField("Nama keystore", name, { name = it }, placeholder = "Toko Saya Release")
            val visual = if (showPw) VisualTransformation.None else PasswordVisualTransformation()
            PasswordField("Keystore password", storePw, { storePw = it }, visual, showPw, { showPw = !showPw }, if (storePw.isNotEmpty() && storePw.length < 6) "Min. 6 karakter" else null)
            LabeledField("Key alias", alias, { alias = it.trim() }, mono = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = samePw, onClick = { samePw = !samePw }, label = { Text("Key password = keystore password") })
            }
            if (!samePw) PasswordField("Key password", keyPw, { keyPw = it }, visual, showPw, { showPw = !showPw }, if (keyPw.isNotEmpty() && keyPw.length < 6) "Min. 6 karakter" else null)
            SelectField("Algoritma", KeyAlgorithm.entries, algorithm, { "${it.label} • SHA256withRSA" }, { algorithm = it })
            Text("Masa berlaku: $years tahun", style = MaterialTheme.typography.labelLarge)
            Slider(value = years.toFloat(), onValueChange = { years = it.toInt() }, valueRange = 25f..50f, steps = 24)
            Text("Distinguished Name", style = MaterialTheme.typography.titleSmall)
            LabeledField("Common Name (CN)", cn, { cn = it }, placeholder = "Nama Anda / Perusahaan")
            LabeledField("Organizational Unit (OU)", ou, { ou = it })
            LabeledField("Organization (O)", o, { o = it })
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
                LabeledField("Kota (L)", l, { l = it }, modifier = Modifier.weight(1f))
                LabeledField("Provinsi (ST)", st, { st = it }, modifier = Modifier.weight(1f))
            }
            LabeledField("Kode negara (C)", c, { c = it.filter(Char::isLetter).take(2).uppercase() }, mono = true,
                error = if (c.isNotEmpty() && c.length != 2) "2 huruf, mis. ID" else null)
            Spacer(Modifier.height(4.dp))
            PrimaryButton(
                text = if (busy) "Membuat kunci${if (algorithm == KeyAlgorithm.RSA_4096) " (RSA-4096 butuh beberapa detik)" else ""}…" else "Generate Keystore",
                onClick = {
                    onCreate(
                        ReleaseKeystoreRequest(
                            name = name, storePassword = storePw, keyAlias = alias, keyPassword = effectiveKeyPw,
                            algorithm = algorithm, validityYears = years,
                            dn = DistinguishedName(cn, ou, o, l, st, c)
                        )
                    )
                },
                enabled = valid && !busy,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PasswordField(
    label: String, value: String, onChange: (String) -> Unit, visual: VisualTransformation,
    shown: Boolean, onToggle: () -> Unit, error: String?
) {
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        visualTransformation = visual, singleLine = true, isError = error != null,
        supportingText = if (error != null) { { Text(error) } } else null,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(if (shown) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (shown) "Sembunyikan" else "Tampilkan", Modifier.size(Dimens.IconInline))
            }
        },
        textStyle = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(Dimens.RadiusControl)
    )
}

@Composable
private fun ImportDialog(uri: Uri, vm: VaultViewModel, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var storePw by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf<List<String>>(emptyList()) }
    var alias by remember { mutableStateOf("") }
    var keyPw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var show by remember { mutableStateOf(false) }
    val visual = if (show) VisualTransformation.None else PasswordVisualTransformation()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.FileUpload, null) },
        title = { Text("Impor Keystore") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
                Text(
                    uri.lastPathSegment ?: uri.toString(), style = WebifyTheme.mono.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                LabeledField("Nama", name, { name = it })
                PasswordField("Keystore password", storePw, { storePw = it; aliases = emptyList() }, visual, show, { show = !show }, null)
                if (aliases.isEmpty()) {
                    SecondaryButton("Validasi & baca alias", modifier = Modifier.fillMaxWidth(), enabled = storePw.isNotEmpty(), onClick = {
                        scope.launch {
                            vm.aliases(uri, storePw)
                                .onSuccess { aliases = it; alias = it.firstOrNull() ?: ""; error = if (it.isEmpty()) "Tidak ada private key" else null }
                                .onFailure { error = it.message }
                        }
                    })
                } else {
                    SelectField("Alias", aliases, alias, { it }, { alias = it })
                    PasswordField("Key password (kosong = sama)", keyPw, { keyPw = it }, visual, show, { show = !show }, null)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && aliases.isNotEmpty(),
                onClick = { vm.importKeystore(uri, name, storePw, alias, keyPw, onDismiss) }
            ) { Text("Impor") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
