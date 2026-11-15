package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.model.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = MintPrimary,
    onPrimary = Color(0xFF042117),
    primaryContainer = MintSecondary,
    onPrimaryContainer = MintPrimaryLight,
    secondary = MintAccentGold,
    onSecondary = Color(0xFF281C00),
    secondaryContainer = Color(0xFF453500),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = MintTertiary,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkCardBorder
)

private val LightColorScheme = lightColorScheme(
    primary = MintPrimaryDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F3E2),
    onPrimaryContainer = Color(0xFF003823),
    secondary = Color(0xFF9E6E00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8B8),
    onSecondaryContainer = Color(0xFF382600),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightCardBorder
)

@Composable
fun GrowMintTheme(
    themeMode: AppThemeMode = AppThemeMode.AUTO,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        AppThemeMode.AUTO -> systemInDark
        AppThemeMode.DAY -> false
        AppThemeMode.NIGHT -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
