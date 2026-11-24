package com.webify.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Item tujuan dock: ikon outlined (tidak aktif) & filled (aktif). */
data class DockItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val accent: Boolean = false
)

/**
 * Ruang bawah yang harus disisakan layar agar konten terakhir tidak tertutup dock.
 * Bernilai 0.dp bila dock tersembunyi (layar lebar, konsol build, Studio, atau keyboard terbuka).
 */
val LocalDockInset = compositionLocalOf<Dp> { 0.dp }

/** Tinggi dock (64dp) + margin bawah (12dp) + ruang napas. */
val DockContentPadding = 88.dp

/**
 * Floating Pill Navigation Bar.
 * Item aktif melebar (spring) menampilkan ikon filled + label di atas kapsul berwarna;
 * item tidak aktif menyusut menjadi ikon outlined saja.
 */
@Composable
fun WebifyBottomNavBar(
    items: List<DockItem>,
    selectedRoute: String?,
    onSelect: (DockItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        shadowElevation = 6.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                DockTab(item = item, selected = item.route == selectedRoute, onClick = { onSelect(item) })
            }
        }
    }
}

@Composable
private fun DockTab(item: DockItem, selected: Boolean, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        when {
            !selected -> Color.Transparent
            item.accent -> scheme.tertiaryContainer
            else -> scheme.primaryContainer
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dockContainer"
    )
    val content by animateColorAsState(
        when {
            !selected -> scheme.onSurfaceVariant
            item.accent -> scheme.onTertiaryContainer
            else -> scheme.onPrimaryContainer
        },
        label = "dockContent"
    )

    // Pantulan halus 0.92 → 1.0 setiap kali tab menjadi aktif
    val scale = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            scale.snapTo(0.92f)
            scale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
        }
    }

    Box(
        Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(24.dp))
            .background(container)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(bounded = true),
                role = Role.Tab
            ) {
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .animateContentSize(spring(dampingRatio = 0.75f, stiffness = 400f))
            .height(44.dp)
            .padding(horizontal = if (selected) 16.dp else 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (selected) item.selectedIcon else item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(24.dp),
                tint = content
            )
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(spring(stiffness = 400f)) + expandHorizontally(spring(dampingRatio = 0.75f, stiffness = 400f)),
                exit = fadeOut(spring(stiffness = 600f)) + shrinkHorizontally(spring(dampingRatio = 0.75f, stiffness = 400f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = content,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
