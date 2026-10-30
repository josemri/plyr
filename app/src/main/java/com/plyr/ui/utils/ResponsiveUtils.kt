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
    val bodySize: TextUnit,
    val captionSize: TextUnit,

    // Controles de música flotantes
    val floatingControlsBottomPadding: Dp,
    val contentBottomPadding: Dp,

    // Imágenes/ASCII art
    val imageMaxWidth: Dp,
    val imageMaxHeight: Dp,

    // Layout flags
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
        screenPadding = screenPadding(isCompact, isMedium),
        contentPadding = contentPadding(isCompact, isMedium),
        itemSpacing = itemSpacing(isCompact, isMedium),
        sectionSpacing = sectionSpacing(isCompact, isHeightCompact),
        bodySize = bodySize(isCompact, isMedium),
        captionSize = captionSize(isCompact, isMedium),
        floatingControlsBottomPadding = floatingControlsBottomPadding(isLandscape, isHeightCompact),
        contentBottomPadding = contentBottomPadding(isLandscape, isHeightCompact),
        imageMaxWidth = imageMaxWidth(screenWidthDp, isCompact, isLandscape),
        imageMaxHeight = imageMaxHeight(screenHeightDp, isHeightCompact, isLandscape),
        showSideBySideLayout = isLandscape && !isCompact
    )
}

private fun screenPadding(isCompact: Boolean, isMedium: Boolean): Dp = when {
    isCompact -> 12.dp
    isMedium -> 16.dp
    else -> 24.dp
}

private fun contentPadding(isCompact: Boolean, isMedium: Boolean): Dp = when {
    isCompact -> 8.dp
    isMedium -> 12.dp
    else -> 16.dp
}

private fun itemSpacing(isCompact: Boolean, isMedium: Boolean): Dp = when {
    isCompact -> 8.dp
    isMedium -> 12.dp
    else -> 16.dp
}

private fun sectionSpacing(isCompact: Boolean, isHeightCompact: Boolean): Dp = when {
    isHeightCompact -> 12.dp
    isCompact -> 16.dp
    else -> 24.dp
}

private fun bodySize(isCompact: Boolean, isMedium: Boolean): TextUnit = when {
    isCompact -> 14.sp
    isMedium -> 15.sp
    else -> 16.sp
}

private fun captionSize(isCompact: Boolean, isMedium: Boolean): TextUnit = when {
    isCompact -> 11.sp
    isMedium -> 12.sp
    else -> 13.sp
}

private fun floatingControlsBottomPadding(isLandscape: Boolean, isHeightCompact: Boolean): Dp = when {
    isHeightCompact -> 4.dp
    isLandscape -> 8.dp
    else -> 48.dp
}

private fun contentBottomPadding(isLandscape: Boolean, isHeightCompact: Boolean): Dp = when {
    isHeightCompact -> 64.dp
    isLandscape -> 80.dp
    else -> 140.dp
}

private fun imageMaxWidth(screenWidthDp: Int, isCompact: Boolean, isLandscape: Boolean): Dp = when {
    isLandscape -> (screenWidthDp * 0.4f).dp
    isCompact -> (screenWidthDp * 0.8f).dp
    else -> (screenWidthDp * 0.7f).dp
}

private fun imageMaxHeight(screenHeightDp: Int, isHeightCompact: Boolean, isLandscape: Boolean): Dp = when {
    isHeightCompact -> (screenHeightDp * 0.3f).dp
    isLandscape -> (screenHeightDp * 0.5f).dp
    else -> (screenHeightDp * 0.4f).dp
}
