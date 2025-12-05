package com.plyr.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable

/**
 * Botón que muestra una animación ASCII donde del centro salen pares de paréntesis
 * "(   )" que se expanden hacia los lados y desaparecen cuando rebasan los bordes.
 * Cuando `isActive` es false muestra `normalText`.
 */
@Composable
fun AsciiWaveActionButton(
    isActive: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    normalText: String = "Share with NFC",
    frameDelayMs: Long = 200L,
    width: Int = 15,
    spawnEveryFrames: Int = 3
) {
    // State que contiene las "ondas" activas representadas por su radio (distancia desde el centro)
    val rings = remember { mutableStateListOf<Int>() }
    var frameCounter by remember { mutableStateOf(0) }
    val center = width / 2

    LaunchedEffect(isActive) {
        if (isActive) {
            // Reset state when starting
            rings.clear()
            frameCounter = 0
            while (isActive) {
                // cada ciclo avanzamos las ondas
                frameCounter++

                // agregar nueva onda cada spawnEveryFrames
                if (frameCounter % spawnEveryFrames == 0) {
                    // comienza en radio 0 (o 1 si se quiere un espacio inicial)
                    rings.add(0)
                }

                // incrementar radios existentes
                for (i in rings.indices) {
                    rings[i] = rings[i] + 1
                }

                // eliminar las ondas que se han salido completamente por ambos lados
                // condición: centro - radio < 0 y centro + radio > width-1 -> desaparece
                val toRemove = rings.withIndex().filter { (_, r) ->
                    (center - r < 0) && (center + r > width - 1)
                }.map { it.index }

                // remover de atrás hacia adelante
                for (index in toRemove.sortedDescending()) {
                    rings.removeAt(index)
                }

                delay(frameDelayMs)
            }
            // al salir limpiar
            rings.clear()
            frameCounter = 0
        } else {
            // Si se desactiva, resetear
            rings.clear()
            frameCounter = 0
        }
    }

    val displayText = if (!isActive) {
        normalText
    } else {
        // construir la línea con paréntesis para todas las ondas activas
        val chars = CharArray(width) { ' ' }
        // para que las ondas más nuevas sobrescriban las anteriores, iteramos por indices
        for (r in rings) {
            val left = center - r
            val right = center + r
            if (left == right && left in 0 until width) {
                chars[left] = '•'
            } else {
                if (left in 0 until width) chars[left] = '('
                if (right in 0 until width) chars[right] = ')'
            }
        }
        String(chars)
    }

    Text(
        text = displayText,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            color = MaterialTheme.typography.bodyMedium.color
        ),
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clickable { onToggle() }
            .padding(8.dp)
    )
}
