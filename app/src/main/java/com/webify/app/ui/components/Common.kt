package com.webify.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.webify.app.domain.Validators
import com.webify.app.ui.theme.TerminalColors
import com.webify.app.ui.theme.WebifyTheme
import com.webify.app.util.FileActions

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

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
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

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
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        isError = error != null,
        supportingText = if (supportingMsg != null) { { Text(supportingMsg) } } else null,
        singleLine = singleLine,
        textStyle = if (mono) WebifyTheme.mono.copy(color = MaterialTheme.colorScheme.onSurface) else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = if (mono) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            autoCorrectEnabled = !mono
        ),
        leadingIcon = if (leadingIcon != null) { { Icon(leadingIcon, null, Modifier.size(20.dp)) } } else null,
        trailingIcon = trailing,
        placeholder = if (placeholder != null) { { Text(placeholder) } } else null,
        shape = MaterialTheme.shapes.medium
    )
}

val PresetColors = listOf(
    "#2563EB", "#0D9488", "#7C3AED", "#DC2626", "#EA580C", "#CA8A04",
    "#16A34A", "#0891B2", "#DB2777", "#0F172A", "#FFFFFF", "#F8FAFC"
)

fun parseHexColor(hex: String): Color? = try {
    if (!Validators.isHexColor(hex.trim())) null else Color(android.graphics.Color.parseColor(hex.trim()))
} catch (_: Exception) { null }

@Composable
fun ColorField(label: String, value: String, onChange: (String) -> Unit, error: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LabeledField(
            label = label,
            value = value,
            onChange = { v -> onChange(if (v.startsWith("#")) v.uppercase() else "#" + v.uppercase()) },
            error = error,
            mono = true,
            leadingIcon = null,
            trailing = {
                Box(
                    Modifier.padding(end = 8.dp).size(28.dp).clip(CircleShape)
                        .background(parseHexColor(value) ?: Color.Transparent)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetColors.forEach { c ->
                val selected = c.equals(value.trim(), true)
                Box(
                    Modifier.size(28.dp).clip(CircleShape)
                        .background(parseHexColor(c)!!)
                        .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable { onChange(c) }
                )
            }
        }
    }
}

/** Editor kode bergaya terminal (#090D16, JetBrains Mono). */
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
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (language.isNotEmpty()) {
                Text(language.uppercase(), style = WebifyTheme.mono.copy(color = MaterialTheme.colorScheme.primary), modifier = Modifier.padding(end = 4.dp))
            }
            IconButton(onClick = { FileActions.copy(ctx, label, value) }, enabled = value.isNotEmpty()) {
                Icon(Icons.Rounded.ContentCopy, "Salin", Modifier.size(20.dp))
            }
        }
        Surface(
            color = TerminalColors.Background,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, TerminalColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.heightIn(min = minHeight.dp).padding(12.dp)) {
                val lines = value.count { it == '\n' } + 1
                Text(
                    (1..lines).joinToString("\n"),
                    style = WebifyTheme.mono.copy(color = TerminalColors.Timestamp),
                    modifier = Modifier.padding(end = 10.dp)
                )
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = WebifyTheme.mono.copy(color = TerminalColors.Timestamp.copy(alpha = 0.6f)))
                    BasicTextField(
                        value = value,
                        onValueChange = onChange,
                        textStyle = WebifyTheme.mono.copy(color = TerminalColors.Text),
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
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            singleLine = true
        )
        // lapisan klik transparan di atas text field read-only
        Box(Modifier.matchParentSize().clip(MaterialTheme.shapes.medium).clickable { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(optionLabel(o)) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, Modifier.size(18.dp), tint = tint) }
            Spacer(Modifier.padding(top = 10.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color, mono: Boolean = false) {
    Surface(shape = RoundedCornerShape(50), color = container) {
        Text(
            text,
            style = if (mono) WebifyTheme.mono.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize) else MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1
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
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

@Composable
fun ThinDivider() = HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
