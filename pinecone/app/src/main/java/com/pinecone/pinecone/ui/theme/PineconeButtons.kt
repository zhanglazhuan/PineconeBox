package com.pinecone.pinecone.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ═══════════════════════════════════════════════════════════
//  Pinecone Button System
//
//  Hierarchy:
//  - PinePrimaryButton    → filled accent, main action
//  - PineSecondaryButton  → outlined glass, secondary action
//  - PineGhostButton      → minimal text-only, tertiary
//  - PineChipButton       → toggle chip for option groups
// ═══════════════════════════════════════════════════════════

// ── Shared shape tokens ──
private val ButtonRadius = 12.dp
private val ChipRadius = 8.dp
private val ButtonHeight = 48.dp
private val ChipHeight = 40.dp

// ═══════════════════════════════════════════════════════════
//  Primary Button — filled accent, white text
// ═══════════════════════════════════════════════════════════

@Composable
fun PinePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: Int = 18
) {
    val bgColor by animateColorAsState(
        targetValue = if (enabled) PinePrimary else PinePrimaryDim.copy(alpha = 0.4f),
        animationSpec = tween(DURATION_FOCUS_IN),
        label = "primaryBtnBg"
    )

    Box(
        modifier = modifier
            .height(ButtonHeight)
            .clip(RoundedCornerShape(ButtonRadius))
            .background(bgColor)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) PineTextOnAccent else PineTextOnAccent.copy(alpha = 0.5f)
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  Secondary Button — glass outline, transparent fill
// ═══════════════════════════════════════════════════════════

@Composable
fun PineSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: Int = 18
) {
    val borderAlpha = if (enabled) 1f else 0.3f
    val textAlpha = if (enabled) 1f else 0.4f

    Box(
        modifier = modifier
            .height(ButtonHeight)
            .clip(RoundedCornerShape(ButtonRadius))
            .background(PineGlass)
            .then(
                if (enabled) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = textAlpha)
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  Ghost Button — minimal text-only, for back / cancel
// ═══════════════════════════════════════════════════════════

@Composable
fun PineGhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    fontSize: Int = 18
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  Chip Button — toggle-able, for option groups
//  Selected: accent fill + white text
//  Unselected: surface fill + muted text
// ═══════════════════════════════════════════════════════════

@Composable
fun PineChipButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            !enabled -> PineSurface.copy(alpha = 0.3f)
            selected -> PinePrimary
            else -> PineSurface
        },
        animationSpec = tween(DURATION_FOCUS_IN),
        label = "chipBg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> PineCardBorder.copy(alpha = 0.3f)
            selected -> PinePrimary
            else -> PineCardBorder
        },
        animationSpec = tween(DURATION_FOCUS_IN),
        label = "chipBorder"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            !enabled -> PineTextMuted
            selected -> Color.White
            else -> PineTextSecondary
        },
        animationSpec = tween(DURATION_FOCUS_IN),
        label = "chipText"
    )

    Box(
        modifier = modifier
            .height(ChipHeight)
            .clip(RoundedCornerShape(ChipRadius))
            .background(bgColor)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor
        )
    }
}

// ═══════════════════════════════════════════════════════════
//  Small Stepper Button — for ▲/▼ increment/decrement
//  Compact, minimal chrome
// ═══════════════════════════════════════════════════════════

@Composable
fun PineStepperButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(PineGlass)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = PineTextSecondary
        )
    }
}
