package com.example.postureguard.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val PostureGuardColors = lightColorScheme(
    primary = Teal,
    onPrimary = SurfaceWhite,
    primaryContainer = TealPale,
    onPrimaryContainer = TealDeep,
    secondary = Mint,
    onSecondary = Ink,
    secondaryContainer = MintPale,
    onSecondaryContainer = Ink,
    tertiary = Lavender,
    onTertiary = Ink,
    tertiaryContainer = LavenderPale,
    onTertiaryContainer = Ink,
    background = Mist,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = InkSoft,
    outline = Hairline,
    outlineVariant = Hairline,
    error = Alert,
    errorContainer = AlertPale,
    onErrorContainer = Alert,
)

@Composable
fun PostureGuardTheme(content: @Composable () -> Unit) {
    isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }
    MaterialTheme(
        colorScheme = PostureGuardColors,
        content = content,
    )
}
