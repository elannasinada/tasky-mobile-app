package io.tasky.taskyapp.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat

// Define a stronger purple for primary to ensure it's clearly visible
private val StrongPurple = Purple60.copy(alpha = 1f) // Make sure alpha is 1f for full opacity

private val DarkColorScheme = darkColorScheme(
    primary = StrongPurple,       // Changed from Purple80 to make primary more distinct
    secondary = Purple80,
    tertiary = Pink80,
    background = DarkGray,
    surface = Purple90,
    onPrimary = White,
    onSecondary = White,
    onTertiary = DarkGray,
    onBackground = White,
    onSurface = White,
    // Adding more color assignments to ensure buttons use purple
    primaryContainer = StrongPurple,
    onPrimaryContainer = White
)

private val LightColorScheme = lightColorScheme(
    primary = StrongPurple,       // Changed from Purple60 to make primary more distinct
    secondary = Purple60,
    tertiary = Pink40,
    background = White,
    surface = LightPurple,
    onPrimary = White,
    onSecondary = White,
    onTertiary = DarkGray,
    onBackground = DarkGray,
    onSurface = DarkGray,
    // Adding more color assignments to ensure buttons use purple
    primaryContainer = StrongPurple,
    onPrimaryContainer = White
)

@Composable
fun TaskyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as Activity).window.statusBarColor = colorScheme.primary.toArgb()
            ViewCompat.getWindowInsetsController(view)?.isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}