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
 * MultiToggle: genera un toggle con N opciones a partir de una lista de strings
 * @param options Lista de etiquetas a mostrar
 * @param modifier Modifier opcional
 * @param initialIndex Índice inicial seleccionado (se ajusta dentro de rango)
 * @param onChange Callback que devuelve el índice seleccionado
 */
@Composable
fun MultiToggle(
    options: List<String>,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
    onChange: ((Int) -> Unit)? = null
) {
    if (options.isEmpty()) return

    var selectedIndex by remember { mutableStateOf(initialIndex.coerceIn(0, options.size - 1)) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        options.forEachIndexed { idx, option ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable {
                        selectedIndex = idx
                        onChange?.invoke(selectedIndex)
                    }
                    .padding(8.dp)
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (selectedIndex == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                    )
                )
            }

            if (idx < options.lastIndex) {
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
            }
        }
    }
}
