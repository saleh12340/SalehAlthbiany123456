package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val DarkColorScheme = darkColorScheme(
    primary = GroceryGreenDarkTheme,
    onPrimary = Color.Black,
    primaryContainer = GroceryGreenSurfaceDark,
    onPrimaryContainer = Color(0xFF86EFAC),
    secondary = GroceryGoldDark,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3E2D00),
    onSecondaryContainer = Color(0xFFFFE082),
    tertiary = Color(0xFF64B5F6),
    background = BackgroundDark,
    onBackground = Color(0xFFE2E8F0),
    surface = SurfaceDark,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = CardBackgroundDark,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = Color(0xFF334155),
    error = Color(0xFFEF4444)
)

private val LightColorScheme = lightColorScheme(
    primary = GroceryGreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GroceryGreenSubtle,
    onPrimaryContainer = GroceryGreenDark,
    secondary = GroceryGold,
    onSecondary = Color.White,
    secondaryContainer = GroceryGoldLight,
    onSecondaryContainer = Color(0xFF664400),
    tertiary = Color(0xFF1565C0),
    background = BackgroundLight,
    onBackground = Color(0xFF1E293B),
    surface = CardBackgroundLight,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF1F5F2),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0),
    error = GroceryRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        // Enforce RTL for full Arabic UX
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
