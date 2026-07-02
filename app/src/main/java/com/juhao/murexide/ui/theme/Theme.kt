package com.juhao.murexide.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.juhao.murexide.datastore.SettingsStorage
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_primary,
    onPrimary = md_theme_onPrimary,
    primaryContainer = md_theme_primaryContainer,
    onPrimaryContainer = md_theme_onPrimaryContainer,
    secondary = md_theme_secondary,
    onSecondary = md_theme_onSecondary,
    secondaryContainer = md_theme_secondaryContainer,
    onSecondaryContainer = md_theme_onSecondaryContainer,
    tertiary = md_theme_tertiary,
    onTertiary = md_theme_onTertiary,
    tertiaryContainer = md_theme_tertiaryContainer,
    onTertiaryContainer = md_theme_onTertiaryContainer,
    error = md_theme_error,
    onError = md_theme_onError,
    errorContainer = md_theme_errorContainer,
    onErrorContainer = md_theme_onErrorContainer,
    background = md_theme_background,
    onBackground = md_theme_onBackground,
    surface = md_theme_surface,
    onSurface = md_theme_onSurface,
    surfaceVariant = md_theme_surfaceVariant,
    onSurfaceVariant = md_theme_onSurfaceVariant,
    outline = md_theme_outline,
    outlineVariant = md_theme_outlineVariant,
    surfaceTint = md_theme_surfaceTint,
    inverseSurface = md_theme_inverseSurface,
    inverseOnSurface = md_theme_inverseOnSurface,
    inversePrimary = md_theme_inversePrimary,
    surfaceContainerHighest = md_theme_surfaceContainerHighest,
    surfaceContainerHigh = md_theme_surfaceContainerHigh,
    surfaceContainer = md_theme_surfaceContainer,
    surfaceContainerLow = md_theme_surfaceContainerLow,
    surfaceContainerLowest = md_theme_surfaceContainerLowest,
    primaryFixed = md_theme_primaryFixed,
    primaryFixedDim = md_theme_primaryFixedDim,
    onPrimaryFixed = md_theme_onPrimaryFixed,
    onPrimaryFixedVariant = md_theme_onPrimaryFixedVariant,
    secondaryFixed = md_theme_secondaryFixed,
    secondaryFixedDim = md_theme_secondaryFixedDim,
    onSecondaryFixed = md_theme_onSecondaryFixed,
    onSecondaryFixedVariant = md_theme_onSecondaryFixedVariant,
    tertiaryFixed = md_theme_tertiaryFixed,
    tertiaryFixedDim = md_theme_tertiaryFixedDim,
    onTertiaryFixed = md_theme_onTertiaryFixed,
    onTertiaryFixedVariant = md_theme_onTertiaryFixedVariant,
    scrim = md_theme_scrim
)

private val LightColorScheme = lightColorScheme(
    primary = md_theme_primary,
    onPrimary = md_theme_onPrimary,
    primaryContainer = md_theme_primaryContainer,
    onPrimaryContainer = md_theme_onPrimaryContainer,
    secondary = md_theme_secondary,
    onSecondary = md_theme_onSecondary,
    secondaryContainer = md_theme_secondaryContainer,
    onSecondaryContainer = md_theme_onSecondaryContainer,
    tertiary = md_theme_tertiary,
    onTertiary = md_theme_onTertiary,
    tertiaryContainer = md_theme_tertiaryContainer,
    onTertiaryContainer = md_theme_onTertiaryContainer,
    error = md_theme_error,
    onError = md_theme_onError,
    errorContainer = md_theme_errorContainer,
    onErrorContainer = md_theme_onErrorContainer,
    background = md_theme_background,
    onBackground = md_theme_onBackground,
    surface = md_theme_surface,
    onSurface = md_theme_onSurface,
    surfaceVariant = md_theme_surfaceVariant,
    onSurfaceVariant = md_theme_onSurfaceVariant,
    outline = md_theme_outline,
    outlineVariant = md_theme_outlineVariant,
    surfaceTint = md_theme_surfaceTint,
    inverseSurface = md_theme_inverseSurface,
    inverseOnSurface = md_theme_inverseOnSurface,
    inversePrimary = md_theme_inversePrimary,
    surfaceContainerHighest = md_theme_surfaceContainerHighest,
    surfaceContainerHigh = md_theme_surfaceContainerHigh,
    surfaceContainer = md_theme_surfaceContainer,
    surfaceContainerLow = md_theme_surfaceContainerLow,
    surfaceContainerLowest = md_theme_surfaceContainerLowest,
    primaryFixed = md_theme_primaryFixed,
    primaryFixedDim = md_theme_primaryFixedDim,
    onPrimaryFixed = md_theme_onPrimaryFixed,
    onPrimaryFixedVariant = md_theme_onPrimaryFixedVariant,
    secondaryFixed = md_theme_secondaryFixed,
    secondaryFixedDim = md_theme_secondaryFixedDim,
    onSecondaryFixed = md_theme_onSecondaryFixed,
    onSecondaryFixedVariant = md_theme_onSecondaryFixedVariant,
    tertiaryFixed = md_theme_tertiaryFixed,
    tertiaryFixedDim = md_theme_tertiaryFixedDim,
    onTertiaryFixed = md_theme_onTertiaryFixed,
    onTertiaryFixedVariant = md_theme_onTertiaryFixedVariant,
    scrim = md_theme_scrim
)

private fun getOledColorScheme(baseScheme: String): ColorScheme {
    val baseColors = when (baseScheme) {
        "DYNAMIC" -> DarkColorScheme
        "PURPLE" -> PurpleDarkColorScheme
        "BLUE" -> BlueDarkColorScheme
        "GREEN" -> GreenDarkColorScheme
        "ORANGE" -> OrangeDarkColorScheme
        else -> DarkColorScheme
    }

    return darkColorScheme(
        primary = baseColors.primary,
        onPrimary = baseColors.onPrimary,
        primaryContainer = baseColors.primaryContainer,
        onPrimaryContainer = baseColors.onPrimaryContainer,
        secondary = baseColors.secondary,
        onSecondary = baseColors.onSecondary,
        secondaryContainer = baseColors.secondaryContainer,
        onSecondaryContainer = baseColors.onSecondaryContainer,
        tertiary = baseColors.tertiary,
        onTertiary = baseColors.onTertiary,
        tertiaryContainer = baseColors.tertiaryContainer,
        onTertiaryContainer = baseColors.onTertiaryContainer,
        error = baseColors.error,
        onError = baseColors.onError,
        errorContainer = baseColors.errorContainer,
        onErrorContainer = baseColors.onErrorContainer,
        background = Color(0xFF000000),
        onBackground = baseColors.onBackground,
        surface = Color(0xFF000000),
        onSurface = baseColors.onSurface,
        surfaceVariant = Color(0xFF1A1A1A),
        onSurfaceVariant = baseColors.onSurfaceVariant,
        outline = baseColors.outline,
        outlineVariant = Color(0xFF2A2A2A),
        surfaceTint = baseColors.surfaceTint,
        inverseSurface = baseColors.inverseSurface,
        inverseOnSurface = baseColors.inverseOnSurface,
        inversePrimary = baseColors.inversePrimary,
        surfaceContainerHighest = baseColors.surfaceContainerHighest,
        surfaceContainerHigh = baseColors.surfaceContainerHigh,
        surfaceContainer = baseColors.surfaceContainer,
        surfaceContainerLow = baseColors.surfaceContainerLow,
        surfaceContainerLowest = baseColors.surfaceContainerLowest,
        primaryFixed = baseColors.primaryFixed,
        primaryFixedDim = baseColors.primaryFixedDim,
        onPrimaryFixed = baseColors.onPrimaryFixed,
        onPrimaryFixedVariant = baseColors.onPrimaryFixedVariant,
        secondaryFixed = baseColors.secondaryFixed,
        secondaryFixedDim = baseColors.secondaryFixedDim,
        onSecondaryFixed = baseColors.onSecondaryFixed,
        onSecondaryFixedVariant = baseColors.onSecondaryFixedVariant,
        tertiaryFixed = baseColors.tertiaryFixed,
        tertiaryFixedDim = baseColors.tertiaryFixedDim,
        onTertiaryFixed = baseColors.onTertiaryFixed,
        onTertiaryFixedVariant = baseColors.onTertiaryFixedVariant,
        scrim = baseColors.scrim
    )
}

@Composable
fun MurexideTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val settingsStorage = remember { SettingsStorage(context) }

    LaunchedEffect(Unit) {
        ThemeState.themeMode.value = settingsStorage.getThemeMode()
        ThemeState.themeColor.value = settingsStorage.getThemeColor()
    }

    val themeMode by ThemeState.themeMode
    val themeColor by ThemeState.themeColor

    val darkTheme = when (themeMode) {
        "system" -> isSystemInDarkTheme()
        "light" -> false
        "dark", "oled" -> true
        else -> false
    }

    val colorScheme = when {
        themeMode == "oled" -> {
            getOledColorScheme(themeColor)
        }
        themeColor == "DYNAMIC" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        themeColor == "PURPLE" -> {
            if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
        }
        themeColor == "BLUE" -> {
            if (darkTheme) BlueDarkColorScheme else BlueLightColorScheme
        }
        themeColor == "GREEN" -> {
            if (darkTheme) GreenDarkColorScheme else GreenLightColorScheme
        }
        themeColor == "ORANGE" -> {
            if (darkTheme) OrangeDarkColorScheme else OrangeLightColorScheme
        }
        else -> {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}