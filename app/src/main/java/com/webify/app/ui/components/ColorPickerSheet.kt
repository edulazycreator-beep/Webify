package com.webify.app.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.webify.app.domain.Validators
import com.webify.app.util.FileActions
import com.webify.app.ui.theme.Dimens
import com.webify.app.ui.theme.WebifyTheme
import java.util.Locale

/** 24 warna preset harmonis (Material + brand modern). */
val HarmonicPresets = listOf(
    "#2563EB", "#1D4ED8", "#0EA5E9", "#06B6D4", "#0D9488", "#10B981",
    "#16A34A", "#84CC16", "#EAB308", "#F59E0B", "#F97316", "#EF4444",
    "#DC2626", "#E11D48", "#DB2777", "#C026D3", "#9333EA", "#7C3AED",
    "#4F46E5", "#0F172A", "#334155", "#64748B", "#F8FAFC", "#FFFFFF"
)

// =============================================================================
// Util warna
// =============================================================================

private fun Color.toHex(): String = String.format(Locale.US, "#%06X", toArgb() and 0xFFFFFF)

private fun hexToColor(hex: String): Color? = parseHexColor(hex)

private fun colorToHsv(c: Color): FloatArray =
    FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }

/** Rasio kontras WCAG antara dua warna. */
private fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance() + 0.05f
    val l2 = b.luminance() + 0.05f
    return if (l1 > l2) l1 / l2 else l2 / l1
}

// =============================================================================
// Tile pemicu
// =============================================================================

/**
 * Baris kartu ringkas: judul peran warna + kode hex (mono) di kiri,
 * lingkaran pratinjau 36dp ber-border kontras di kanan. Ketuk → buka ColorPickerBottomSheet.
 */
@Composable
fun ColorSelectorTile(
    title: String,
    hex: String,
    onColorChange: (String) -> Unit,
    subtitle: String? = null,
    error: String? = null
) {
    var open by remember { mutableStateOf(false) }
    val color = hexToColor(hex)
    val animated by animateColorAsState(color ?: Color.Transparent, label = "tileColor")
    Surface(
        onClick = { open = true },
        shape = RoundedCornerShape(Dimens.RadiusControl),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    error ?: (hex.uppercase() + (subtitle?.let { " • $it" } ?: "")),
                    style = WebifyTheme.mono.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(animated)
                    .border(2.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(Dimens.IconInline), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) {
        ColorPickerBottomSheet(
            title = title,
            initialHex = if (color != null) hex else "#2563EB",
            onDismiss = { open = false },
            onApply = { onColorChange(it); open = false }
        )
    }
}

// =============================================================================
// Bottom sheet
// =============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerBottomSheet(
    title: String,
    initialHex: String,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    val ctx = LocalContext.current
    val init = remember { hexToColor(initialHex) ?: Color(0xFF2563EB) }
    val initHsv = remember { colorToHsv(init) }
    var hue by remember { mutableFloatStateOf(initHsv[0]) }
    var sat by remember { mutableFloatStateOf(initHsv[1]) }
    var value by remember { mutableFloatStateOf(initHsv[2]) }
    var hexText by remember { mutableStateOf(init.toHex()) }

    val current = Color.hsv(hue, sat, value)
    val currentHex = current.toHex()

    /** Sinkron dari slider → hex. */
    fun fromSliders(h: Float = hue, s: Float = sat, v: Float = value) {
        hue = h; sat = s; value = v
        hexText = Color.hsv(h, s, v).toHex()
    }

    /** Sinkron dari hex / preset → slider. */
    fun fromHex(hex: String) {
        val c = hexToColor(hex) ?: return
        val hsv = colorToHsv(c)
        // pertahankan hue saat warna abu-abu (saturasi 0) agar slider tidak melompat
        if (hsv[1] > 0.001f) hue = hsv[0]
        sat = hsv[1]; value = hsv[2]
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = Dimens.RadiusCard, topEnd = Dimens.RadiusCard)
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = Dimens.ScreenPadding, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)

            // 1. Live contrast preview
            ContrastPreview(current)

            // 2. Preset harmonis
            Text("Preset", style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HarmonicPresets.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { hex ->
                            val c = hexToColor(hex)!!
                            val selected = hex.equals(currentHex, ignoreCase = true)
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(c)
                                    .border(
                                        if (selected) 3.dp else 1.dp,
                                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                                    .clickable { hexText = hex.uppercase(); fromHex(hex) },
                                contentAlignment = Alignment.Center
                            ) {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = selected,
                                    enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = 500f)),
                                    exit = scaleOut()
                                ) {
                                    Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = if (c.luminance() > 0.5f) Color.Black else Color.White)
                                }
                            }
                        }
                    }
                }
            }

            // 3. Slider HSV
            Text("Racik warna", style = MaterialTheme.typography.titleSmall)
            GradientSlider(
                label = "Hue", valueText = "${hue.toInt()}°",
                value = hue, range = 0f..360f,
                brush = Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }),
                onChange = { fromSliders(h = it) }
            )
            GradientSlider(
                label = "Saturation", valueText = "${(sat * 100).toInt()}%",
                value = sat, range = 0f..1f,
                brush = Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value))),
                onChange = { fromSliders(s = it) }
            )
            GradientSlider(
                label = "Brightness", valueText = "${(value * 100).toInt()}%",
                value = value, range = 0f..1f,
                brush = Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, sat, 1f))),
                onChange = { fromSliders(v = it) }
            )

            // 4. Input hex + tempel
            OutlinedTextField(
                value = hexText,
                onValueChange = { raw ->
                    val cleaned = "#" + raw.removePrefix("#").filter { it.isLetterOrDigit() }.take(6).uppercase()
                    hexText = cleaned
                    if (Validators.isHexColor(cleaned)) fromHex(cleaned)
                },
                label = { Text("Kode hex") },
                isError = !Validators.isHexColor(hexText),
                supportingText = { Text(if (Validators.isHexColor(hexText)) "Format #RRGGBB" else "Masukkan 6 digit heksadesimal") },
                singleLine = true,
                textStyle = WebifyTheme.mono.copy(fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                leadingIcon = {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(current).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
                },
                trailingIcon = {
                    IconButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty()
                        val candidate = "#" + text.removePrefix("#").take(6).uppercase()
                        if (Validators.isHexColor(candidate)) { hexText = candidate; fromHex(candidate) }
                        else FileActions.toast(ctx, "Clipboard tidak berisi kode warna #RRGGBB")
                    }) { Icon(Icons.Rounded.ContentPaste, "Tempel dari clipboard", Modifier.size(Dimens.IconInline)) }
                },
                shape = RoundedCornerShape(Dimens.RadiusControl),
                modifier = Modifier.fillMaxWidth()
            )

            // 5. Konfirmasi
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton("Batal", onClick = onDismiss, modifier = Modifier.weight(1f).height(Dimens.PrimaryButtonHeight))
                PrimaryButton(
                    "Terapkan Warna", onClick = { onApply(currentHex) }, icon = Icons.Rounded.Check,
                    modifier = Modifier.weight(1f), enabled = Validators.isHexColor(hexText)
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Pratinjau sebagai status bar + splash, dengan uji keterbacaan teks putih vs hitam. */
@Composable
private fun ContrastPreview(color: Color) {
    val whiteRatio = contrastRatio(color, Color.White)
    val blackRatio = contrastRatio(color, Color.Black)
    val best = if (whiteRatio >= blackRatio) Color.White else Color.Black
    Surface(
        shape = RoundedCornerShape(Dimens.RadiusCard),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // simulasi status bar
            Row(
                Modifier.fillMaxWidth().background(color).padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("12:30", color = best, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("▾ ◢ ▮", color = best, fontSize = 11.sp)
            }
            // simulasi splash
            Box(Modifier.fillMaxWidth().height(96.dp).background(color.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    ReadabilitySample("Teks putih", Color.White, whiteRatio, color, Modifier.weight(1f))
                    ReadabilitySample("Teks hitam", Color.Black, blackRatio, color, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReadabilitySample(label: String, text: Color, ratio: Float, bg: Color, modifier: Modifier) {
    val verdict = when {
        ratio >= 7f -> "AAA"
        ratio >= 4.5f -> "AA"
        ratio >= 3f -> "AA besar"
        else -> "Sulit dibaca"
    }
    Column(modifier.background(bg).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        Text(String.format(Locale.US, "%.1f:1 • %s", ratio, verdict), color = text.copy(alpha = 0.85f), fontSize = 11.sp, maxLines = 1)
    }
}

/** Slider dengan track gradien (hue / saturasi / kecerahan). */
@Composable
private fun GradientSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    brush: Brush,
    onChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text(valueText, style = WebifyTheme.mono.copy(fontSize = 12.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(10.dp)
                    .clip(RoundedCornerShape(50)).background(brush)
            )
            Slider(
                value = value,
                onValueChange = onChange,
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
        }
    }
}
