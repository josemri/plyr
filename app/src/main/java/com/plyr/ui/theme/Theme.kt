package com.plyr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Dark Theme Colors
private val DarkTerminalGreen = Color(0xFF4ECDC4)
private val DarkTerminalBlack = Color(0xFF1A1A1A)
private val DarkTerminalGray = Color(0xFF95A5A6)
private val DarkTerminalYellow = Color(0xFFFFD93D)
private val DarkTerminalRed = Color(0xFFFF6B6B)
private val DarkTerminalWhite = Color(0xFFE0E0E0)

// Light Theme Colors
private val LightTerminalGreen = Color(0xFF2D7A78)
private val LightTerminalWhite = Color(0xFFFAFAFA)
private val LightTerminalDarkGray = Color(0xFF2C3E50)
private val LightTerminalYellow = Color(0xFFE67E22)
private val LightTerminalRed = Color(0xFFE74C3C)
private val LightTerminalBlack = Color(0xFF1A1A1A)

// Dark Color Scheme
private val DarkTerminalColorScheme = darkColorScheme(
    primary = DarkTerminalGreen,
    onPrimary = DarkTerminalBlack,
    background = DarkTerminalBlack,
    onBackground = DarkTerminalWhite,
    surface = DarkTerminalBlack,
    onSurface = DarkTerminalWhite,
    secondary = DarkTerminalGray,
    onSecondary = DarkTerminalBlack,
    error = DarkTerminalRed,
    onError = DarkTerminalBlack,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = DarkTerminalGray,
    outline = DarkTerminalGray.copy(alpha = 0.5f),
)

// Light Color Scheme
private val LightTerminalColorScheme = lightColorScheme(
    primary = LightTerminalGreen,
    onPrimary = LightTerminalWhite,
    background = LightTerminalWhite,
    onBackground = LightTerminalBlack,
    surface = LightTerminalWhite,
    onSurface = LightTerminalBlack,
    secondary = LightTerminalDarkGray,
    onSecondary = LightTerminalWhite,
    error = LightTerminalRed,
    onError = LightTerminalWhite,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = LightTerminalDarkGray,
    outline = LightTerminalDarkGray.copy(alpha = 0.5f),
)


@Composable
fun adaptiveTypography(isDark: Boolean): Typography {
    val configuration = LocalConfiguration.current
    val textColor = if (isDark) DarkTerminalWhite else LightTerminalBlack
    val accentColor = if (isDark) DarkTerminalGreen else LightTerminalGreen

    return Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 32.sp, color = accentColor),
        displayMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 28.sp, color = accentColor),
        displaySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 24.sp, color = accentColor),

        headlineLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, color = accentColor),
        headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = accentColor),
        headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = accentColor),

        titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = textColor),
        titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = textColor),
        titleSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = textColor),

        bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = textColor),
        bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = textColor),
        bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = textColor),

        labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = textColor),
        labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = textColor),
        labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = textColor),
    )
}

@Composable
fun TerminalTheme(
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isDark) DarkTerminalColorScheme else LightTerminalColorScheme
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = adaptiveTypography(isDark),
        content = content
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewTerminalThemeDark() {
    TerminalTheme(isDark = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Hello, Dark Terminal Theme!", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTerminalThemeLight() {
    TerminalTheme(isDark = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Hello, Light Terminal Theme!", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
fun TerminalThemePreview() {
    TerminalTheme(isDark = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PLYR - Terminal Theme",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 200, name = "Terminal Colors Demo")
@Composable
fun TerminalColorsPreview() {
    TerminalTheme(isDark = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "> plyr.exe --status=ready\n$ Green on Black Terminal",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}