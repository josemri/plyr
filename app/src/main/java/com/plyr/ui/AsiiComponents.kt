package com.plyr.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun AsciiSlider(
    progress: Float,
    length: Int = 30,
    onSeek: (Float) -> Unit = {}
) {
    val filledBlocks = (progress * length).toInt().coerceIn(0, length)
    val emptyBlocks = length - filledBlocks

    val filledChar = '█' // bloque sólido
    val emptyChar = '░'  // bloque claro

    val bar = buildString {
        repeat(filledBlocks) { append(filledChar) }
        repeat(emptyBlocks) { append(emptyChar) }
    }

    Text(
        text = bar,
        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(8.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(newProgress)
                }
            }
    )
}
