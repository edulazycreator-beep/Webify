package com.webify.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webify.app.domain.Validators
import com.webify.app.ui.theme.Dimens
import com.webify.app.ui.theme.TerminalColors
import com.webify.app.ui.theme.WebifyTheme
import com.webify.app.util.FileActions

// =============================================================================
// Kartu & baris
// =============================================================================

/** Kartu tematik: radius 16dp, border 1dp outlineVariant, padding 16dp, jarak isi 12dp. */
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Dimens.RadiusCard),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Dimens.FieldGap)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    IconBadge(icon, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(Dimens.IconTextGap))
                }
                Column(Modifier.weight(1f).padding(end = if (trailing != null) 12.dp else 0.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) {
                        Text(
                            subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

/** Kotak ikon 32dp (radius 8dp) berisi ikon 18dp. */
@Composable
fun IconBadge(icon: ImageVector, container: Color, content: Color, size: Dp = 32.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(Dimens.RadiusSmall)).background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = content)
    }
}

/**
 * Baris pengaturan dengan Switch di kanan. Tinggi minimum 52dp; teks kiri memakai
 * weight(1f) + padding end 12dp sehingga teks panjang tidak pernah mendorong Switch keluar layar.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Dimens.SettingRowMinHeight)
            .clip(RoundedCornerShape(Dimens.RadiusSmall))
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(Dimens.IconInline), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Dimens.IconTextGap))
        }
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

// =============================================================================
// Tombol
// =============================================================================

/** CTA utama: tinggi 52dp, radius 12dp, teks 14sp SemiBold. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors()
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = Dimens.PrimaryButtonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(Dimens.RadiusControl),
        colors = colors,
        contentPadding = PaddingValues(horizontal = 20.dp)
    ) {
        ButtonContent(text, icon, 14, FontWeight.SemiBold)
    }
}

/** Tombol sekunder: tinggi 40dp, radius 10dp, teks 13sp Medium. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    tonal: Boolean = false
) {
    val shape = RoundedCornerShape(Dimens.RadiusSecondary)
    val padding = PaddingValues(horizontal = 14.dp)
    if (tonal) {
        FilledTonalButton(onClick, modifier.height(Dimens.SecondaryButtonHeight), enabled, shape = shape, contentPadding = padding) {
            ButtonContent(text, icon, 13, FontWeight.Medium)
        }
    } else {
        OutlinedButton(onClick, modifier.height(Dimens.SecondaryButtonHeight), enabled, shape = shape, contentPadding = padding) {
            ButtonContent(text, icon, 13, FontWeight.Medium)
        }
    }
}

/** Tombol aksi kartu: tinggi 38dp, radius 10dp, ikon 16dp + teks 12sp — untuk baris aksi berjarak rata. */
@Composable
fun CardActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    destructive: Boolean = false
) {
    val shape = RoundedCornerShape(Dimens.RadiusSecondary)
    val padding = PaddingValues(horizontal = 6.dp)
    val content: @Composable RowScope.() -> Unit = {
        Icon(icon, null, Modifier.size(Dimens.IconChip))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (emphasized) {
        FilledTonalButton(onClick, modifier.height(Dimens.CardActionHeight), shape = shape, contentPadding = padding, content = content)
    } else {
        OutlinedButton(
            onClick, modifier.height(Dimens.CardActionHeight), shape = shape, contentPadding = padding,
            colors = if (destructive) ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            else ButtonDefaults.outlinedButtonColors(),
            border = BorderStroke(1.dp, if (destructive) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant),
            content = content
        )
    }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, sizeSp: Int, weight: FontWeight) {
    if (icon != null) {
        Icon(icon, null, Modifier.size(Dimens.IconInline))
        Spacer(Modifier.width(Dimens.IconTextGapSmall))
    }
    Text(text, fontSize = sizeSp.sp, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

// =============================================================================
// Input
// =============================================================================

/** OutlinedTextField standar: tinggi min 56dp, radius 12dp. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    supporting: String? = null,
    mono: Boolean = false,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    placeholder: String? = null
) {
    val supportingMsg = error ?: supporting
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        isError = error != null,
        supportingText = if (supportingMsg != null) { { Text(supportingMsg) } } else null,
        singleLine = singleLine,
        textStyle = if (mono) WebifyTheme.mono.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        else MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = if (mono) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            autoCorrectEnabled = !mono
        ),
        leadingIcon = if (leadingIcon != null) { { Icon(leadingIcon, null, Modifier.size(Dimens.IconInline)) } } else null,
        trailingIcon = trailing,
        placeholder = if (placeholder != null) { { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) } } else null,
        shape = RoundedCornerShape(Dimens.RadiusControl)
    )
}

val PresetColors = listOf(
    "#2563EB", "#0D9488", "#7C3AED", "#DC2626", "#EA580C", "#CA8A04",
    "#16A34A", "#0891B2", "#DB2777", "#0F172A", "#FFFFFF", "#F8FAFC"
)

fun parseHexColor(hex: String): Color? = try {
    if (!Validators.isHexColor(hex.trim())) null else Color(android.graphics.Color.parseColor(hex.trim()))
} catch (_: Exception) { null }

/** Input warna hex + swatch preview + palet preset (LazyRow, jarak 8dp). */
@Composable
fun ColorField(label: String, value: String, onChange: (String) -> Unit, error: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledField(
            label = label,
            value = value,
            onChange = { v -> onChange(if (v.startsWith("#")) v.uppercase() else "#" + v.uppercase()) },
            error = error,
            mono = true,
            trailing = {
                Box(
                    Modifier.padding(end = 12.dp).size(24.dp).clip(CircleShape)
                        .background(parseHexColor(value) ?: Color.Transparent)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PresetColors) { c ->
                val selected = c.equals(value.trim(), true)
                Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .background(parseHexColor(c) ?: Color.Transparent)
                        .border(
                            if (selected) 3.dp else 1.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        )
                        .clickable { onChange(c) }
                )
            }
        }
    }
}

/** Editor kode bergaya terminal: latar #090D16, JetBrains Mono, radius 8dp, padding 12dp, tinggi min 160dp. */
@Composable
fun CodeEditor(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String = "",
    minHeight: Int = 160,
    language: String = ""
) {
    val ctx = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(end = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (language.isNotEmpty()) {
                Pill(language.uppercase(), MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, mono = true)
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = { FileActions.copy(ctx, label, value) }, enabled = value.isNotEmpty(), modifier = Modifier.size(Dimens.IconButtonSmall)) {
                Icon(Icons.Rounded.ContentCopy, "Salin", Modifier.size(18.dp))
            }
        }
        Surface(
            color = TerminalColors.Background,
            shape = RoundedCornerShape(Dimens.RadiusSmall),
            border = BorderStroke(1.dp, TerminalColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            val codeStyle = WebifyTheme.mono.copy(fontSize = 12.sp, lineHeight = 18.sp)
            Row(Modifier.heightIn(min = minHeight.dp).padding(12.dp)) {
                val lines = value.count { it == '\n' } + 1
                Text(
                    (1..lines).joinToString("\n"),
                    style = codeStyle.copy(color = TerminalColors.Timestamp),
                    modifier = Modifier.padding(end = 10.dp)
                )
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = codeStyle.copy(color = TerminalColors.Timestamp.copy(alpha = 0.6f)))
                    BasicTextField(
                        value = value,
                        onValueChange = onChange,
                        textStyle = codeStyle.copy(color = TerminalColors.Text),
                        cursorBrush = SolidColor(TerminalColors.Info),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Dropdown pilihan sederhana tanpa API eksperimental. */
@Composable
fun <T> SelectField(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, Modifier.size(Dimens.IconBar)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(Dimens.RadiusControl),
            singleLine = true
        )
        // lapisan klik transparan di atas text field read-only
        Box(Modifier.matchParentSize().clip(RoundedCornerShape(Dimens.RadiusControl)).clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(optionLabel(o), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(o); open = false }
                )
            }
        }
    }
}

// =============================================================================
// Statistik, badge, state kosong
// =============================================================================

/** Kartu statistik — pakai di dalam Row(Modifier.height(IntrinsicSize.Max)) agar tinggi sama rata. */
@Composable
fun StatTile(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(Dimens.RadiusCard),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBadge(icon, tint.copy(alpha = 0.14f), tint, size = 28.dp)
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Badge / status chip: radius 8dp, teks 11sp Medium. */
@Composable
fun Pill(text: String, container: Color, content: Color, mono: Boolean = false) {
    Surface(shape = RoundedCornerShape(Dimens.RadiusSmall), color = container) {
        Text(
            text,
            style = if (mono) WebifyTheme.mono.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
            else MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, action: @Composable (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(4.dp))
            action()
        }
    }
}

@Composable
fun ThinDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
