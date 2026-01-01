package com.plyr.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Clases de utilidad para manejar layouts responsivos según el tamaño de pantalla.
 *
 * Proporciona dimensiones adaptativas para:
 * - Paddings
 * - Tamaños de texto
 * - Espaciados
 * - Tamaños de iconos
 */

/**
 * Dimensiones responsivas calculadas a partir del tamaño de pantalla
 */
data class ResponsiveDimensions(
    // Paddings
    val screenPadding: Dp,
    val contentPadding: Dp,
    val itemSpacing: Dp,
    val sectionSpacing: Dp,

    // Tamaños de texto
    val titleSize: TextUnit,
    val bodySize: TextUnit,
    val captionSize: TextUnit,

    // Iconos
    val iconSizeSmall: Dp,
    val iconSizeMedium: Dp,
    val iconSizeLarge: Dp,

    // Controles de música flotantes
    val floatingControlsBottomPadding: Dp,
    val floatingControlsHeight: Dp,
    val contentBottomPadding: Dp,

    // Imágenes/ASCII art
    val imageMaxWidth: Dp,
    val imageMaxHeight: Dp,

    // Botones
    val buttonHeight: Dp,
    val buttonMinWidth: Dp,

    // Layout flags
    val isCompact: Boolean,
    val isLandscape: Boolean,
    val showSideBySideLayout: Boolean
)

/**
 * Calcula las dimensiones responsivas basadas en LocalConfiguration.
 * Funciona en todas las versiones de Android y no requiere dependencias adicionales.
 * 
 * Esta función implementa la lógica equivalente a WindowSizeClass:
 * - Compact: width < 600dp
 * - Medium: width 600-839dp
 * - Expanded: width >= 840dp
 */
@Composable
fun calculateResponsiveDimensionsFallback(): ResponsiveDimensions {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val isLandscape = screenWidthDp > screenHeightDp

    // Clasificación similar a WindowSizeClass
    val isCompact = screenWidthDp < 600
    val isMedium = screenWidthDp in 600..839
    val isHeightCompact = screenHeightDp < 480

    return ResponsiveDimensions(
        screenPadding = when {
            isCompact -> 12.dp
            isMedium -> 16.dp
            else -> 24.dp
        },
        contentPadding = when {
            isCompact -> 8.dp
            isMedium -> 12.dp
            else -> 16.dp
        },
        itemSpacing = when {
            isCompact -> 8.dp
            isMedium -> 12.dp
            else -> 16.dp
        },
        sectionSpacing = when {
            isHeightCompact -> 12.dp
            isCompact -> 16.dp
            else -> 24.dp
        },
        titleSize = when {
            isCompact -> 18.sp
            isMedium -> 20.sp
            else -> 24.sp
        },
        bodySize = when {
            isCompact -> 14.sp
            isMedium -> 15.sp
            else -> 16.sp
        },
        captionSize = when {
            isCompact -> 11.sp
            isMedium -> 12.sp
            else -> 13.sp
        },
        iconSizeSmall = when {
            isCompact -> 18.dp
            isMedium -> 20.dp
            else -> 24.dp
        },
        iconSizeMedium = when {
            isCompact -> 22.dp
            isMedium -> 24.dp
            else -> 28.dp
        },
        iconSizeLarge = when {
            isCompact -> 28.dp
            isMedium -> 32.dp
            else -> 40.dp
        },
        floatingControlsBottomPadding = when {
            isHeightCompact -> 4.dp
            isLandscape -> 8.dp
            else -> 48.dp
        },
        floatingControlsHeight = when {
            isHeightCompact -> 56.dp
            isLandscape -> 64.dp
            else -> 80.dp
        },
        contentBottomPadding = when {
            isHeightCompact -> 64.dp
            isLandscape -> 80.dp
            else -> 140.dp
        },
        imageMaxWidth = when {
            isLandscape -> (screenWidthDp * 0.4f).dp
            isCompact -> (screenWidthDp * 0.8f).dp
            else -> (screenWidthDp * 0.7f).dp
        },
        imageMaxHeight = when {
            isHeightCompact -> (screenHeightDp * 0.3f).dp
            isLandscape -> (screenHeightDp * 0.5f).dp
            else -> (screenHeightDp * 0.4f).dp
        },
        buttonHeight = when {
            isHeightCompact -> 40.dp
            isCompact -> 44.dp
            else -> 48.dp
        },
        buttonMinWidth = when {
            isCompact -> 100.dp
            isMedium -> 120.dp
            else -> 150.dp
        },
        isCompact = isCompact,
        isLandscape = isLandscape,
        showSideBySideLayout = isLandscape && !isCompact
    )
}
