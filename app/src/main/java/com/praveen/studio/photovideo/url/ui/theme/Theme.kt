package com.praveen.studio.photovideo.url.ui.theme

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
import com.praveen.studio.photovideo.url.data.model.ThemePreference

private val DarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.Black,
    primaryContainer = CyanDark,
    onPrimaryContainer = CyanPrimaryLight,
    secondary = IndigoLight,
    onSecondary = Color.Black,
    secondaryContainer = IndigoDark,
    onSecondaryContainer = Color.White,
    tertiary = AccentEmerald,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkCardBorder,
    error = AccentRose,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = CyanPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = CyanDark,
    secondary = IndigoSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = IndigoDark,
    tertiary = AccentEmerald,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightCardBorder,
    error = AccentRose,
    onError = Color.White
)

@Composable
fun PhotoVideoUrlTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themePreference) {
        ThemePreference.SYSTEM -> systemInDark
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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
