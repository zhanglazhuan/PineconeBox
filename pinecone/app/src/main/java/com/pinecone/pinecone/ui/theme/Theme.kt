package com.pinecone.pinecone.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════════════════════════
//  Apple-Style Dark Color Scheme (primary)
//  Matches the spatial depth model: Background → Surface → Glass
// ═══════════════════════════════════════════════════════════

private val PineconeDarkColorScheme = darkColorScheme(
    // ── Brand colors ──
    primary            = PinePrimary,
    onPrimary          = PineTextOnAccent,
    primaryContainer   = PinePrimaryDim,
    onPrimaryContainer  = PineTextPrimary,

    // ── Secondary accent (warm orange) ──
    secondary          = PineSecondary,
    onSecondary        = PineTextOnAccent,
    secondaryContainer  = Color(0x33FF9F43), // rgba(255,159,67, 0.20)
    onSecondaryContainer = PineTextPrimary,

    // ── Tertiary ──
    tertiary           = PinePrimaryDim,
    onTertiary         = PineTextPrimary,

    // ── Surface layers ──
    background         = PineBackground,
    onBackground       = PineTextPrimary,
    surface            = PineSidebar,
    onSurface          = PineTextPrimary,
    surfaceVariant     = PineSurface,
    onSurfaceVariant   = PineTextSecondary,

    // ── Outline / divider ──
    outline            = PineGlassBorder,
    outlineVariant     = Color(0x0AFFFFFF), // rgba(255,255,255, 0.04)

    // ── Error ──
    error              = PineError,
    onError            = Color.White,
    errorContainer     = Color(0x33FF453A), // rgba(255,69,58, 0.20)
    onErrorContainer   = PineError,

    // ── Inverse ──
    inverseSurface     = Color(0xFFF0F0F0),
    inverseOnSurface   = Color(0xFF1C1C1E),
    inversePrimary     = Color(0xFF0A84FF),

    // ── Surface tint (subtle blue cast for depth) ──
    surfaceTint        = Color(0x0D5E9EFF)  // rgba(94,158,255, 0.05)
)

// ═══════════════════════════════════════════════════════════
//  Light Color Scheme (for settings / daytime use)
// ═══════════════════════════════════════════════════════════

private val PineconeLightColorScheme = lightColorScheme(
    primary            = Color(0xFF0A84FF),  // Apple system blue
    onPrimary          = Color.White,
    primaryContainer   = Color(0xFFD6E8FF),
    onPrimaryContainer  = Color(0xFF001A41),

    secondary          = Color(0xFFFF9F43),
    onSecondary        = Color.White,
    secondaryContainer  = Color(0xFFFFE8D6),
    onSecondaryContainer = Color(0xFF3D1F00),

    tertiary           = Color(0xFF5E5CE6),
    onTertiary         = Color.White,

    background         = Color(0xFFF2F2F7),  // iOS light background
    onBackground       = Color(0xFF1C1C1E),
    surface            = Color(0xFFFFFFFF),
    onSurface          = Color(0xFF1C1C1E),
    surfaceVariant     = Color(0xFFF9F9FB),
    onSurfaceVariant   = Color(0xFF6E6E73),

    outline            = Color(0xFFE5E5EA),
    outlineVariant     = Color(0xFFF2F2F7),

    error              = Color(0xFFFF453A),
    onError            = Color.White,
    errorContainer     = Color(0xFFFFE5E5),
    onErrorContainer   = Color(0xFF6B0000),

    inverseSurface     = Color(0xFF1C1C1E),
    inverseOnSurface   = Color(0xFFF0F0F0),
    inversePrimary     = Color(0xFF0A84FF),

    surfaceTint        = Color(0x0D0A84FF)
)

// ═══════════════════════════════════════════════════════════
//  Theme composable
// ═══════════════════════════════════════════════════════════

@Composable
fun PineconeTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PineconeDarkColorScheme else PineconeLightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = PineBackground.toArgb()
            window.navigationBarColor = PineBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PineconeTypography,
        content = content
    )
}
