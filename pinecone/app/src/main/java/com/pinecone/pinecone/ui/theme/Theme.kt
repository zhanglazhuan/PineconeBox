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

private val PineconeDarkColorScheme = darkColorScheme(
    primary = PineAccent,
    secondary = PineHighlight,
    tertiary = PineAccent,
    background = PineBackground,
    surface = PineSidebar,
    surfaceVariant = PineSurface,
    onPrimary = PineBackground,
    onSecondary = PineTextPrimary,
    onTertiary = PineBackground,
    onBackground = PineTextPrimary,
    onSurface = PineTextPrimary,
    onSurfaceVariant = PineTextSecondary
)

private val PineconeLightColorScheme = lightColorScheme(
    primary = Color(0xFF0288D1),
    secondary = Color(0xFF546E7A),
    tertiary = Color(0xFF0288D1)
)

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
        typography = Typography,
        content = content
    )
}
