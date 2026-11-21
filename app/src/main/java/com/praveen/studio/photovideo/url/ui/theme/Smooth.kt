package com.praveen.studio.photovideo.url.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The "smooth texture" behind every screen: a calm vertical gradient plus two very faint glows
 * (sky blue at the top-left, violet at the bottom-right) — the same glow as the app icon.
 * Draw it ONCE at the root; the screens on top are transparent.
 */
@Composable
fun Modifier.appBackdrop(): Modifier {
    val base = MaterialTheme.colorScheme.background
    val isDark = base.luminance() < 0.5f
    val top = if (isDark) lerp(base, BrandIndigo, 0.07f) else lerp(base, Color.White, 0.8f)
    val glowSky = BrandSky.copy(alpha = if (isDark) 0.10f else 0.07f)
    val glowViolet = BrandViolet.copy(alpha = if (isDark) 0.09f else 0.06f)
    return this.drawBehind {
        drawRect(Brush.verticalGradient(listOf(top, base)))
        drawRect(
            Brush.radialGradient(
                colors = listOf(glowSky, Color.Transparent),
                center = Offset(0f, 0f),
                radius = size.maxDimension * 0.6f
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(glowViolet, Color.Transparent),
                center = Offset(size.width, size.height),
                radius = size.maxDimension * 0.6f
            )
        )
    }
}

/** Minor animation: the element shrinks a little while it is pressed and springs back (never longer than a blink). */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Minor animation: fades in and rises a few dp when it first appears. [index] staggers a list (45 ms each). */
@Composable
fun Modifier.softEnter(index: Int = 0): Modifier {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = 380,
            delayMillis = (index * 45).coerceIn(0, 270),
            easing = FastOutSlowInEasing
        ),
        label = "softEnter"
    )
    return this.graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * 16.dp.toPx()
    }
}

/** A readable text/icon colour on top of any accent colour: white, unless the accent is too light for white. */
fun onAccent(accent: Color): Color {
    val contrastWithWhite = 1.05f / (accent.luminance() + 0.05f)
    return if (contrastWithWhite >= 3.0f) Color.White else Color(0xFF0F1330)
}

/** The main button: brand gradient (indigo -> violet), soft press animation. Disabled = quiet grey like Material. */
@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val background = if (enabled) {
        Brush.horizontalGradient(listOf(CyanPrimary, IndigoSecondary))
    } else {
        SolidColor(scheme.onSurface.copy(alpha = 0.12f))
    }
    val contentColor = if (enabled) Color.White else scheme.onSurface.copy(alpha = 0.38f)

    Row(
        modifier = modifier
            .pressScale(interaction)
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                content()
            }
        }
    }
}
