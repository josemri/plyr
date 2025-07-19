package com.plyr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

// Define Colors
private val TerminalGreen = Color(0xFF4ECDC4)  // Verde agua terminal
private val TerminalBlack = Color(0xFF1A1A1A)  // Negro terminal
private val TerminalGray = Color(0xFF95A5A6)   // Gris terminal
private val TerminalYellow = Color(0xFFFFD93D) // Amarillo terminal
private val TerminalRed = Color(0xFFFF6B6B)    // Rojo terminal

// Define Color Scheme
private val TerminalColorScheme = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = TerminalBlack,
    background = TerminalBlack,
    onBackground = TerminalGreen,
    surface = TerminalBlack,
    onSurface = TerminalGreen,
    secondary = TerminalGray,
    onSecondary = TerminalBlack,
    error = TerminalRed,
    onError = TerminalBlack,
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = TerminalGray,
    outline = TerminalGray.copy(alpha = 0.5f),
)


@Composable
fun adaptiveTypography(): Typography {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    // Tamaños más grandes y legibles para terminal
    return Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 32.sp, color = TerminalGreen),
        displayMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 28.sp, color = TerminalGreen),
        displaySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 24.sp, color = TerminalGreen),

        headlineLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, color = TerminalGreen),
        headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = TerminalGreen),
        headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = TerminalGreen),

        titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = TerminalGreen),
        titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = TerminalGreen),
        titleSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = TerminalGreen),

        bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = TerminalGreen),
        bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = TerminalGreen),
        bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TerminalGreen),

        labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = TerminalGreen),
        labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TerminalGreen),
        labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = TerminalGreen),
    )
}

@Composable
fun TerminalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = adaptiveTypography(),
        content = content
    )
}

@Preview(showBackground = true)
@Composable
fun PreviewTerminalTheme() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Hello, Terminal Theme!", style = MaterialTheme.typography.titleLarge)
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
fun TerminalThemePreview() {
    TerminalTheme {
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
    TerminalTheme {
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