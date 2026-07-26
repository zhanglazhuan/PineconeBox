package com.pinecone.pinecone.ui.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════
//  Apple-Style Animation Presets
//
//  Principles:
//  - Spring for natural feel, never linear
//  - Exit faster than enter
//  - GPU-safe: only scale/alpha/shadow, not layout
// ═══════════════════════════════════════════════════════════

// ── Duration tokens (ms) ──
const val DURATION_FOCUS_IN   = 150
const val DURATION_FOCUS_OUT  = 100
const val DURATION_PRESS      = 50
const val DURATION_TAB_SWITCH = 250
const val DURATION_EXPAND     = 200
const val DURATION_PAGE_IN    = 300
const val DURATION_STAGGER    = 50
const val DURATION_FADE       = 200

// ── Spring presets ──
val springGentle = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

val springSnappy = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)

val springBouncy = spring<Float>(
    dampingRatio = Spring.DampingRatioLowBouncy,
    stiffness = Spring.StiffnessMedium
)

// ── Easing ──
val easeOutExpo = CubicBezierEasing(0.16f, 1.0f, 0.3f, 1.0f)

// ── Tween specs ──
fun <T> tweenOut(): TweenSpec<T> = tween(DURATION_FOCUS_IN, easing = easeOutExpo)
fun <T> tweenIn(): TweenSpec<T> = tween(DURATION_FOCUS_OUT)
fun <T> tweenPressSpec(): TweenSpec<T> = tween(DURATION_PRESS)

// ── Scale constants ──
const val SCALE_REST   = 1.0f
const val SCALE_FOCUS  = 1.03f
const val SCALE_PRESS  = 0.97f

// ── Shadow elevations ──
val SHADOW_REST  = 0.dp
val SHADOW_FOCUS = 8.dp
val SHADOW_HIGH  = 16.dp

// ═══════════════════════════════════════════════════════════
//  Composable focus animation modifier
//  For TV D-Pad focus + click/press feedback
// ═══════════════════════════════════════════════════════════

@Composable
fun Modifier.appleFocusable(
    isFocused: Boolean = false,
    isPressed: Boolean = false,
    cornerRadius: Dp = 16.dp,
    glowColor: Color = PineFocusRing,
    focusElevation: Dp = SHADOW_FOCUS
): Modifier = composed {
    val targetScale = when {
        isPressed -> SCALE_PRESS
        isFocused -> SCALE_FOCUS
        else      -> SCALE_REST
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = when {
            isPressed                                   -> tweenPressSpec()
            targetScale > SCALE_REST                    -> tweenOut()
            else                                        -> tweenIn()
        },
        label = "focusScale"
    )

    val shadowDp by animateDpAsState(
        targetValue = if (isFocused || isPressed) focusElevation else SHADOW_REST,
        animationSpec = if (isFocused) tweenOut() else tweenIn(),
        label = "focusShadow"
    )

    val shape = RoundedCornerShape(cornerRadius)

    this
        .scale(animatedScale)
        .shadow(
            elevation = shadowDp,
            shape = shape,
            ambientColor = glowColor.copy(alpha = 0.2f),
            spotColor = glowColor.copy(alpha = 0.1f)
        )
}

// ═══════════════════════════════════════════════════════════
//  Staggered fade-in (for list/page entrance)
// ═══════════════════════════════════════════════════════════

@Composable
fun rememberStaggerAlpha(index: Int, isVisible: Boolean = true): Float {
    val targetAlpha = if (isVisible) 1f else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(
            durationMillis = DURATION_PAGE_IN,
            delayMillis = index * DURATION_STAGGER,
            easing = easeOutExpo
        ),
        label = "staggerAlpha"
    )
    return alpha
}

// ═══════════════════════════════════════════════════════════
//  AnimatedVisibility wrapper (expand/collapse + fade)
// ═══════════════════════════════════════════════════════════

@Composable
fun AppleAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = expandVertically(animationSpec = spring()) +
                fadeIn(animationSpec = tweenOut()),
        exit = shrinkVertically(animationSpec = tweenIn()) +
               fadeOut(animationSpec = tweenIn()),
        content = content
    )
}

// ═══════════════════════════════════════════════════════════
//  AnimatedContent transition for tab/page switches
// ═══════════════════════════════════════════════════════════

@Composable
fun AppleCrossfade(
    targetState: Any,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedContentScope.() -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            fadeIn(animationSpec = tween(DURATION_FADE, easing = easeOutExpo)) togetherWith
            fadeOut(animationSpec = tween(DURATION_FADE / 2))
        },
        label = "crossfade",
        content = { content() }
    )
}
