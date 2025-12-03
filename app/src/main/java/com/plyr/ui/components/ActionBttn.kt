package com.plyr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modelo que representa los datos de un botón de acción.
 */
data class ActionButtonData(
    val text: String,
    val color: Color = Color.Unspecified,
    val onClick: () -> Unit,
    val enabled: Boolean = true
)

/**
 * Composable individual para un botón de acción estilo texto.
 */
@Composable
fun ActionButton(
    data: ActionButtonData,
    modifier: Modifier = Modifier
) {
    // Helper: pick a readable color based on theme and background
    @Composable
    fun readableColor(candidate: Color): Color {
        val background = MaterialTheme.colorScheme.background
        // If candidate is unspecified, use the headline/title color (same source as logo)
        val base = if (candidate == Color.Unspecified) {
            // headlineMedium color in typography mirrors how the logo/title color is chosen in the theme
            MaterialTheme.typography.headlineMedium.color
        } else {
            candidate
        }

        // Simple contrast check using luminance difference; if too close, fall back to onBackground
        val lumDiff = kotlin.math.abs(base.luminance() - background.luminance())
        return if (lumDiff < 0.32f) MaterialTheme.colorScheme.onBackground else base
    }

    val textColor = if (data.enabled) readableColor(data.color) else MaterialTheme.colorScheme.outline

    Text(
        text = data.text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            color = textColor
        ),
        textAlign = TextAlign.Center,
        modifier = modifier
            .clickable(enabled = data.enabled) { data.onClick() }
            .padding(8.dp)
    )
}

/**
 * Grupo de botones de acción configurable.
 *
 * Tiene valores por defecto que permiten usarlo directamente sin parámetros extra.
 *
 * Ejemplo mínimo:
 *     ActionButtonsGroup(buttons = myButtons)
 */
@Composable
fun ActionButtonsGroup(
    buttons: List<ActionButtonData>,
    isHorizontal: Boolean = true,
    spacing: Dp = 16.dp,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp)
) {
    if (isHorizontal) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(spacing)
        ) {
            val childModifier = Modifier
                .wrapContentWidth()
            buttons.forEach { ActionButton(it, modifier = childModifier) }
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            val childModifier = Modifier
                .fillMaxWidth()
            buttons.forEach { ActionButton(it, modifier = childModifier) }
        }
    }
}
