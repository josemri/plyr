package com.plyr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Modelo que representa los datos de un botón de acción.
 */
data class ActionButtonData(
    val text: String,
    val color: Color,
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
    Text(
        text = data.text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            color = if (data.enabled) data.color else MaterialTheme.colorScheme.outline
        ),
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
            buttons.forEach { ActionButton(it) }
        }
    } else {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(spacing)
        ) {
            buttons.forEach { ActionButton(it) }
        }
    }
}
