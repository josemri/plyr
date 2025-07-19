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
private val TerminalGreen = Color(0xFF00FF00)
private val TerminalBlack = Color.Black // Or Color(0xFF000000)

// Define Color Scheme
private val TerminalColorScheme = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = TerminalBlack,      // Text/icons on primary background
    background = TerminalBlack,
    onBackground = TerminalGreen,   // Text/icons on background
    surface = TerminalBlack,        // Color of components like Cards, Sheets, Menus
    onSurface = TerminalGreen,      // Text/icons on surface
    // You might want to define other colors too for a complete theme:
    // secondary = ...,
    // onSecondary = ...,
    // error = ...,
    // onError = ...,
    // surfaceVariant = ..., // A subtle variation of surface
    // onSurfaceVariant = ...,
    // outline = ...,
)

@Composable
fun adaptiveTypography(): Typography {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    // Define factor o base para escalar tamaño de fuente, ej:
    val scaleFactor = when {
        screenWidthDp > 600 -> 0.5f  // tablets o pantallas grandes
        screenWidthDp > 400 -> 0.5f  // móviles medianos
        else -> 0.1f                   // móviles pequeños
    }

    // Aplica el scaleFactor a los tamaños base, ej. 22.sp * scaleFactor
    return Typography(
        displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (57 * scaleFactor).sp, color = TerminalGreen),
        displayMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (45 * scaleFactor).sp, color = TerminalGreen),
        displaySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (36 * scaleFactor).sp, color = TerminalGreen),

        headlineLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (32 * scaleFactor).sp, color = TerminalGreen),
        headlineMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (28 * scaleFactor).sp, color = TerminalGreen),
        headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (24 * scaleFactor).sp, color = TerminalGreen),

        titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (22 * scaleFactor).sp, color = TerminalGreen),
        titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (34 * scaleFactor).sp, color = TerminalGreen),
        titleSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (14 * scaleFactor).sp, color = TerminalGreen),

        bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (30 * scaleFactor).sp, color = TerminalGreen),
        bodyMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (14 * scaleFactor).sp, color = TerminalGreen),
        bodySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (12 * scaleFactor).sp, color = TerminalGreen),

        labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (14 * scaleFactor).sp, color = TerminalGreen),
        labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (12 * scaleFactor).sp, color = TerminalGreen),
        labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = (11 * scaleFactor).sp, color = TerminalGreen),
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