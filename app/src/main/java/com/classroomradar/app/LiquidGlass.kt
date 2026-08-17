package com.classroomradar.app

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.roundToInt

internal enum class GlassTone(val surface: Color, val outline: Color) {
    Neutral(Color(0xFFFFFFFF), Color(0xFFE6E5E3)),
    Accent(Color(0xFFFFF1E8), Color(0xFFFFD9C2)),
    Success(Color(0xFFFF670D), Color(0xFFFF670D)),
    Warning(Color(0xFFFFF6E0), Color(0xFFFFE1A3)),
    Danger(Color(0xFFE5484D), Color(0xFFE5484D)),
    AlertSoft(Color(0xFFFDECEC), Color(0xFFF5C8C9)),
}

/** Limits expensive background sampling to the few surfaces that need depth. */
internal enum class GlassEffect { Static, Soft, Elevated }

internal val LiquidTextPrimary = Color(0xFF171717)
internal val LiquidTextSecondary = Color(0xFF6F6F6F)
internal val LiquidCyan = Color(0xFFFF670D)
internal val LiquidViolet = Color(0xFFE04E00)

/** Brand palette shared with the product site (shuike.seanyan.store). */
internal val RadarBg = Color(0xFFFCFCFC)
internal val RadarSection = Color(0xFFF3F2F1)
internal val RadarInk3 = Color(0xFF9B9B9B)
internal val RadarBrand = Color(0xFFFF670D)
internal val RadarBrandDeep = Color(0xFFE04E00)
internal val RadarBrandSoft = Color(0xFFFFF1E8)
internal val RadarWarn = Color(0xFFFFB020)
internal val RadarWarnSoft = Color(0xFFFFF6E0)
internal val RadarWarnText = Color(0xFF8A5A00)
internal val RadarAlert = Color(0xFFE5484D)
internal val RadarAlertSoft = Color(0xFFFDECEC)
internal val RadarOk = Color(0xFF2F9E6E)

/**
 * The window-level backdrop every glass surface samples. Null below API 31 (no RenderEffect
 * support) and inside dialog windows, where panels must fall back to the static style.
 */
internal val LocalLiquidBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

@Composable
internal fun LiquidGlassRoot(content: @Composable BoxScope.() -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // The backdrop records the *page content* layer (applied in AppScaffold) so floating
        // chrome has something real to refract. No glass may live inside that recorded layer:
        // sampling the layer while it is being drawn loops inside HWUI and kills the process.
        val backdrop = rememberLayerBackdrop {
            drawRect(RadarBg)
            drawContent()
        }
        Box(modifier = Modifier.fillMaxSize().background(RadarBg)) {
            CompositionLocalProvider(LocalLiquidBackdrop provides backdrop) {
                Box(modifier = Modifier.fillMaxSize(), content = content)
            }
        }
    } else {
        Box(
            modifier = Modifier.fillMaxSize().background(RadarBg),
            content = content,
        )
    }
}

/**
 * Brand-tinted glass surface backed by AndroidLiquidGlass (Kyant0 backdrop). Soft/Elevated
 * panels refract the page behind them on API 31+ (lens refraction needs API 33+); solid
 * tones (primary/destructive actions) and low API levels keep the static fill for
 * readability and startup stability.
 */
@Composable
internal fun GlassPanel(
    modifier: Modifier = Modifier,
    tone: GlassTone = GlassTone.Neutral,
    effect: GlassEffect = GlassEffect.Static,
    shape: Shape = RoundedCornerShape(28.dp),
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(18.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalLiquidBackdrop.current
    val solidTone = tone == GlassTone.Success || tone == GlassTone.Danger
    if (backdrop != null && effect != GlassEffect.Static && !solidTone) {
        // Chrome floats over real page content, so surfaces can be quite translucent; the
        // lens supplies the visible refraction (droplet-style edge distortion).
        val surfaceAlpha = if (effect == GlassEffect.Elevated) 0.3f else 0.24f
        Box(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(10f.dp.toPx())
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            lens(14f.dp.toPx(), 32f.dp.toPx())
                        }
                    },
                    onDrawSurface = { drawRect(tone.surface.copy(alpha = surfaceAlpha)) },
                )
                .border(BorderStroke(1.dp, tone.outline.copy(alpha = 0.6f)), shape)
                .padding(contentPadding),
            content = content,
        )
        return
    }
    val staticSurface = when (effect) {
        GlassEffect.Static -> tone.surface
        GlassEffect.Soft -> tone.surface.copy(alpha = tone.surface.alpha * 0.92f)
        GlassEffect.Elevated -> tone.surface.copy(alpha = minOf(1f, tone.surface.alpha * 1.06f))
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(staticSurface, shape)
            .border(BorderStroke(1.dp, tone.outline), shape)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
internal fun GlassIconAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    tone: GlassTone = GlassTone.Success,
    iconTint: Color = LiquidTextPrimary,
) {
    val effectiveTone = if (emphasized) tone else GlassTone.Neutral
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Droplet-like press feedback: the whole button squashes and springs back.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "GlassIconActionPress",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        GlassPanel(
            modifier = Modifier
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .size(58.dp)
                .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick),
            tone = effectiveTone,
            effect = if (emphasized) GlassEffect.Elevated else GlassEffect.Soft,
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.align(Alignment.Center),
                tint = if (enabled && emphasized) Color.White else if (enabled) iconTint else LiquidTextSecondary.copy(alpha = 0.45f),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) LiquidTextSecondary else LiquidTextSecondary.copy(alpha = 0.45f),
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

/** Compact icon-only action used inside rows without falling back to a plain Material button. */
@Composable
internal fun GlassRoundIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: GlassTone = GlassTone.Accent,
    tint: Color = LiquidCyan,
) {
    val iconTint = if (tone == GlassTone.Danger || tone == GlassTone.Success) Color.White else tint
    GlassPanel(
        modifier = modifier
            .size(48.dp)
            .clickable(enabled = enabled, onClick = onClick),
        tone = tone,
        effect = GlassEffect.Static,
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.align(Alignment.Center),
            tint = if (enabled) iconTint else iconTint.copy(alpha = 0.38f),
        )
    }
}

/**
 * A regular Material text editor rendered inside the app's glass substrate. Keeping the
 * platform editor preserves IME, selection, autofill and accessibility behaviour.
 */
@Composable
internal fun GlassTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    singleLine: Boolean = true,
) {
    GlassPanel(
        modifier = modifier,
        tone = GlassTone.Neutral,
        effect = GlassEffect.Static,
        shape = RoundedCornerShape(20.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            enabled = enabled,
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = LiquidTextPrimary,
                unfocusedTextColor = LiquidTextPrimary,
                disabledTextColor = LiquidTextSecondary.copy(alpha = 0.45f),
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                cursorColor = LiquidCyan,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                disabledBorderColor = Color.Transparent,
                focusedLabelColor = LiquidCyan,
                unfocusedLabelColor = LiquidTextSecondary,
                disabledLabelColor = LiquidTextSecondary.copy(alpha = 0.45f),
            ),
        )
    }
}

/**
 * Accessible, 48 dp touch-target switch with a compact glass track. The surrounding Box owns
 * toggle semantics so TalkBack exposes the same checked/action state as a normal Switch.
 */
@Composable
internal fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 0.dp,
        label = "GlassToggleThumb",
    )
    val thumbColor = if (checked) LiquidTextPrimary else LiquidTextSecondary
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 54.dp, minHeight = 48.dp)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .semantics {
                label?.let { contentDescription = it }
            },
    ) {
        GlassPanel(
            modifier = Modifier
                .size(width = 54.dp, height = 34.dp)
                .align(Alignment.Center),
            tone = if (checked) GlassTone.Accent else GlassTone.Neutral,
            effect = GlassEffect.Static,
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(3.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        // Keep offset measurement inside the layout pass; passing an animated
                        // Dp directly would recompose this modifier on every animation frame.
                        .offset { IntOffset((thumbOffset.value * density).roundToInt(), 0) }
                        .size(24.dp)
                        .background(thumbColor, CircleShape)
                        .border(
                            BorderStroke(1.dp, Color.White.copy(alpha = if (checked) 0.72f else 0.32f)),
                            CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun GlassTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tone: GlassTone = GlassTone.Neutral,
    enabled: Boolean = true,
) {
    val effectiveTone = if (enabled) tone else GlassTone.Neutral
    val contentColor = when {
        !enabled -> LiquidTextSecondary.copy(alpha = 0.45f)
        effectiveTone == GlassTone.Danger || effectiveTone == GlassTone.Success -> Color.White
        else -> LiquidTextPrimary
    }
    GlassPanel(
        modifier = modifier
            .defaultMinSize(minHeight = 46.dp)
            .clickable(enabled = enabled, onClick = onClick),
        tone = effectiveTone,
        effect = GlassEffect.Static,
        shape = RoundedCornerShape(18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp), tint = contentColor) }
            Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor)
        }
    }
}

@Composable
internal fun GlassInlineAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit = {},
) {
    GlassPanel(
        modifier = modifier
            .defaultMinSize(minHeight = 40.dp)
            .clickable(enabled = enabled, onClick = onClick),
        tone = if (enabled) GlassTone.Accent else GlassTone.Neutral,
        effect = GlassEffect.Static,
        shape = RoundedCornerShape(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) LiquidCyan else LiquidTextSecondary.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
internal fun GlassConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    tone: GlassTone = GlassTone.Accent,
) {
    val isInverted = tone == GlassTone.Danger || tone == GlassTone.Success
    val titleColor = if (isInverted) Color.White else LiquidTextPrimary
    val bodyColor = if (isInverted) Color.White.copy(alpha = 0.74f) else LiquidTextSecondary
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Dialogs live in a separate window and cannot sample the main window's backdrop;
        // disabling glass here keeps dialog panels on the stable static renderer.
        CompositionLocalProvider(LocalLiquidBackdrop provides null) {
            GlassPanel(
                // Capped width keeps dialogs readable on tablets and unfolded foldables
                // instead of stretching edge to edge.
                modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).padding(horizontal = 22.dp),
                tone = tone,
                effect = GlassEffect.Elevated,
                shape = RoundedCornerShape(30.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(22.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, color = titleColor)
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = bodyColor)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    ) {
                        GlassTextAction(dismissLabel, onDismiss)
                        GlassTextAction(confirmLabel, onConfirm, tone = tone)
                    }
                }
            }
        }
    }
}
