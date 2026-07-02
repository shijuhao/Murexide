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

private fun getOledColorScheme(baseScheme: String): ColorScheme {
    val baseColors = when (baseScheme) {
        "PURPLE", "DYNAMIC" -> PurpleDarkColorScheme
        "BLUE" -> BlueDarkColorScheme
        "GREEN" -> GreenDarkColorScheme
        "ORANGE" -> OrangeDarkColorScheme
        else -> PurpleDarkColorScheme
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
                if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
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
            if (darkTheme) PurpleDarkColorScheme else PurpleLightColorScheme
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