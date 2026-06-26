package com.terminal.rootnode.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MatrixGreen,
    onPrimary = MatrixBlack,
    secondary = HackerBlue,
    onSecondary = MatrixBlack,
    tertiary = HackerAmber,
    onTertiary = MatrixBlack,
    error = HackerRed,
    onError = MatrixBlack,
    background = MatrixBlack,
    onBackground = MatrixGreen,
    surface = MatrixBlack,
    onSurface = MatrixGreen,
    surfaceVariant = MatrixDarkGreen,
    onSurfaceVariant = MatrixGreen,
    outline = HackerPurple
)

private val LightColorScheme = lightColorScheme(
    primary = MatrixDarkGreen,
    onPrimary = MatrixGreen,
    secondary = HackerBlue,
    onSecondary = MatrixBlack,
    tertiary = HackerAmber,
    onTertiary = MatrixBlack,
    background = MatrixGreen,
    onBackground = MatrixBlack,
    surface = MatrixGreen,
    onSurface = MatrixBlack
)

@Composable
fun RootNodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color disabled to preserve the custom hacker/matrix theme
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}