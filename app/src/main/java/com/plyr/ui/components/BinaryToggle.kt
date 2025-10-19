package com.plyr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composable binario genérico
 *
 * @param option1 Texto de la primera opción
 * @param option2 Texto de la segunda opción
 * @param initialValue Valor inicial, true = option1, false = option2
 * @param onChange Callback cuando cambia la selección
 */
@Composable
fun BinaryToggle(
    option1: String,
    option2: String,
    initialValue: Boolean = true,
    onChange: ((Boolean) -> Unit)? = null
) {
    var isOption1Selected by remember { mutableStateOf(initialValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        // Opción 1
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    isOption1Selected = true
                    onChange?.invoke(isOption1Selected)
                }
                .padding(8.dp)
        ) {
            Text(
                text = option1,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = if (isOption1Selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            )
        }

        // Separador
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "/",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            )
        }

        // Opción 2
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    isOption1Selected = false
                    onChange?.invoke(isOption1Selected)
                }
                .padding(8.dp)
        ) {
            Text(
                text = option2,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = if (!isOption1Selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}
