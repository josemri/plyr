@file:Suppress("unused")
package com.plyr.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults

// === SISTEMA DE COLORES UNIFICADO ===

// Dark Theme Colors
private val DarkTerminalGreen = Color(0xFF4ECDC4)
private val DarkTerminalBlack = Color(0xFF1A1A1A)
private val DarkTerminalGray = Color(0xFF95A5A6)
private val DarkTerminalYellow = Color(0xFFFFD93D)
private val DarkTerminalRed = Color(0xFFFF6B6B)
private val DarkTerminalWhite = Color(0xFFE0E0E0)

// Light Theme Colors
private val LightTerminalGreen = Color(0xFF1A73E8)
private val LightTerminalWhite = Color(0xFFFAFAFA)
private val LightTerminalDarkGray = Color(0xFF2C3E50)
private val LightTerminalYellow = Color(0xFFE67E22)
private val LightTerminalRed = Color(0xFFE74C3C)
private val LightTerminalBlack = Color(0xFF1A1A1A)

// === SISTEMA DE ESPACIADO UNIFICADO ===
object PlyrSpacing {
    val xxs = 2.dp      // Micro espacios
    val xs = 4.dp       // Espacios muy pequeños
    val small = 8.dp    // Espacios pequeños (padding interno)
    val medium = 12.dp  // Espacios medianos (entre elementos)
    val large = 16.dp   // Espacios grandes (padding de pantalla)
    val xl = 24.dp      // Espacios extra grandes
    val xxl = 32.dp     // Espacios muy grandes (separaciones principales)
    val huge = 48.dp    // Espacios enormes (espacios especiales)
}

// === SISTEMA DE DIMENSIONES UNIFICADO ===
object PlyrDimensions {
    // Alturas de componentes
    val buttonHeight = 40.dp
    val inputHeight = 48.dp
    val listItemHeight = 56.dp
    val controlsHeight = 72.dp
    val floatingControlsHeight = 140.dp

    // Radios de bordes
    val cornerRadiusSmall = 4.dp
    val cornerRadiusMedium = 8.dp
    val cornerRadiusLarge = 12.dp

    // Elevaciones
    val elevationNone = 0.dp
    val elevationSmall = 2.dp
    val elevationMedium = 4.dp
    val elevationLarge = 8.dp
}

// === SISTEMA DE ICONOS Y SÍMBOLOS UNIFICADO ===
object PlyrSymbols {
    const val PROMPT = "> "
    const val COMMAND = "$ "
    const val SEPARATOR = "/"
    const val BULLET = "●"
    const val ARROW = "→"
    const val BACK = "←"
    const val LOADING = "..."
}

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
    tertiary = DarkTerminalYellow,
    onTertiary = DarkTerminalBlack,
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
    tertiary = LightTerminalYellow,
    onTertiary = LightTerminalWhite,
)

// === SISTEMA DE TIPOGRAFÍA UNIFICADO ===

/**
 * Sistema de Tipografía Unificado para plyr
 *
 * Jerarquía de texto consistente:
 * 1. TÍTULOS DE COMANDO (headlineMedium) - Verde terminal
 * 2. OPCIONES DE MENÚ (titleMedium) - Texto normal
 * 3. CONTENIDO PRINCIPAL (bodyMedium) - Texto normal
 * 4. CONTENIDO SECUNDARIO (bodySmall) - Gris apagado
 * 5. SEPARADORES Y SÍMBOLOS (labelMedium) - Secundario
 */
@Composable
fun unifiedTypography(isDark: Boolean): Typography {
    val titleColor = if (isDark) DarkTerminalGreen else LightTerminalGreen
    val normalTextColor = if (isDark) DarkTerminalWhite else LightTerminalBlack
    val secondaryTextColor = if (isDark) DarkTerminalGray else LightTerminalDarkGray.copy(alpha = 0.7f)

    return Typography(
        // TÍTULOS DE COMANDO - Como "$ plyr_search"
        headlineMedium = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 24.sp,
            color = titleColor,
            fontWeight = FontWeight.Normal,
            lineHeight = 32.sp
        ),

        // OPCIONES DE MENÚ - Como "> search"
        titleMedium = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp,
            color = normalTextColor,
            fontWeight = FontWeight.Normal,
            lineHeight = 28.sp
        ),

        // CONTENIDO PRINCIPAL - Títulos de canciones, etc.
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = normalTextColor,
            fontWeight = FontWeight.Normal,
            lineHeight = 20.sp
        ),

        // CONTENIDO SECUNDARIO - Artistas, metadatos
        bodySmall = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = secondaryTextColor,
            fontWeight = FontWeight.Normal,
            lineHeight = 18.sp
        ),

        // SEPARADORES Y SÍMBOLOS
        labelMedium = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = secondaryTextColor,
            fontWeight = FontWeight.Normal,
            lineHeight = 20.sp
        ),

        // TEXTO DE ERROR Y ALERTAS
        titleSmall = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = if (isDark) DarkTerminalRed else LightTerminalRed,
            fontWeight = FontWeight.Normal,
            lineHeight = 20.sp
        ),

        // Mapeo de otros tamaños para compatibilidad
        displayLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 32.sp, color = titleColor, lineHeight = 40.sp),
        displayMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 28.sp, color = titleColor, lineHeight = 36.sp),
        displaySmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 24.sp, color = titleColor, lineHeight = 32.sp),

        headlineLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 28.sp, color = titleColor, lineHeight = 36.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 20.sp, color = titleColor, lineHeight = 28.sp),

        titleLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = normalTextColor, lineHeight = 26.sp),

        bodyLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = normalTextColor, lineHeight = 24.sp),

        labelLarge = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = normalTextColor, lineHeight = 20.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = secondaryTextColor, lineHeight = 16.sp),
    )
}

// === ESTILOS DE TEXTO PERSONALIZADOS ===
object PlyrTextStyles {
    @Composable
    fun commandTitle() = MaterialTheme.typography.headlineMedium

    @Composable
    fun menuOption() = MaterialTheme.typography.titleMedium

    @Composable
    fun trackTitle() = MaterialTheme.typography.bodyMedium

    @Composable
    fun trackArtist() = MaterialTheme.typography.bodySmall

    @Composable
    fun separator() = MaterialTheme.typography.labelMedium

    @Composable
    fun errorText() = MaterialTheme.typography.titleSmall

    @Composable
    fun infoText() = MaterialTheme.typography.bodySmall.copy(
        lineHeight = 18.sp
    )

    // Estilo específico para opciones clickeables con estado
    @Composable
    fun selectableOption(isSelected: Boolean) = MaterialTheme.typography.bodyMedium.copy(
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    )
}

// === FORMAS UNIFICADAS ===
object PlyrShapes {
    val small = RoundedCornerShape(PlyrDimensions.cornerRadiusSmall)
    val medium = RoundedCornerShape(PlyrDimensions.cornerRadiusMedium)
    val large = RoundedCornerShape(PlyrDimensions.cornerRadiusLarge)
    val none = RoundedCornerShape(0.dp)
}

// === ESTILOS DE BOTONES UNIFICADOS ===
object PlyrButtonStyles {
    @Composable
    fun primaryButton() = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )

    @Composable
    fun secondaryButton() = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    @Composable
    fun errorButton() = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError
    )
}

// Mantener función anterior para compatibilidad temporal
@Composable
fun adaptiveTypography(isDark: Boolean): Typography = unifiedTypography(isDark)

@Composable
fun PlyrTheme(
    darkTheme: Boolean = true, // Por defecto modo oscuro
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkTerminalColorScheme
    } else {
        LightTerminalColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = unifiedTypography(darkTheme),
        content = content
    )
}

// === PREVIEWS ===

@Preview(showBackground = true)
@Composable
fun PreviewTerminalThemeDark() {
    PlyrTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(PlyrSpacing.large),
            contentAlignment = Alignment.Center
        ) {
            Text("Hello, Dark Terminal Theme!", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewTerminalThemeLight() {
    PlyrTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(PlyrSpacing.large),
            contentAlignment = Alignment.Center
        ) {
            Text("Hello, Light Terminal Theme!", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
fun TerminalThemePreview() {
    PlyrTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(PlyrSpacing.large),
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
    PlyrTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(PlyrSpacing.large),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "> plyr.exe --status=ready\n$ Green on Black Terminal",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
