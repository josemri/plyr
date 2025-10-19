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
 * Composable ternario genérico para 3 opciones
 *
 * @param option1 Texto de la primera opción
 * @param option2 Texto de la segunda opción
 * @param option3 Texto de la tercera opción
 * @param initialValue Valor inicial (0, 1, o 2)
 * @param onChange Callback cuando cambia la selección (devuelve 0, 1, o 2)
 */
@Composable
fun TernaryToggle(
    option1: String,
    option2: String,
    option3: String,
    initialValue: Int = 0,
    onChange: ((Int) -> Unit)? = null
) {
    var selectedOption by remember { mutableStateOf(initialValue) }

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
                    selectedOption = 0
                    onChange?.invoke(selectedOption)
                }
                .padding(8.dp)
        ) {
            Text(
                text = option1,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = if (selectedOption == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            )
        }

        // Separador 1
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
                    selectedOption = 1
                    onChange?.invoke(selectedOption)
                }
                .padding(8.dp)
        ) {
            Text(
                text = option2,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = if (selectedOption == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            )
        }

        // Separador 2
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

        // Opción 3
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable {
                    selectedOption = 2
                    onChange?.invoke(selectedOption)
                }
                .padding(8.dp)
        ) {
            Text(
                text = option3,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = if (selectedOption == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}

