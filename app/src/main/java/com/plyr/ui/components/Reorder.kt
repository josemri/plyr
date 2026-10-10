package com.plyr.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Lógica pura del reordenado por arrastre.
 *
 * Se mantiene fuera de Compose a propósito: los índices de origen/destino son
 * justo donde se cuelan los errores (fuera de rango, sin cambio), y así se
 * cubren con tests JVM.
 */
object Reorder {

    /**
     * Devuelve [items] con el elemento de [from] movido a [to]. Un índice fuera
     * de rango o [from] == [to] devuelve la **misma** lista (no reasigna ni
     * dispara recomposición de más).
     */
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        return items.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    /**
     * Índice de destino tras arrastrar [offsetY] píxeles desde [from]. Se redondea
     * al hueco más cercano y se recorta a la lista; si aún no se ha medido la
     * altura, devuelve [from].
     */
    fun targetIndex(from: Int, offsetY: Float, itemHeightPx: Float, count: Int): Int {
        if (count <= 0 || itemHeightPx <= 0f) return from.coerceIn(0, (count - 1).coerceAtLeast(0))
        val delta = (offsetY / itemHeightPx).roundToInt()
        return (from + delta).coerceIn(0, count - 1)
    }
}

/**
 * Estado del arrastre de una fila para reordenar una lista. Se hoistea fuera del
 * `LazyColumn`; cada fila aplica [itemModifier] y, al soltar, se decide el nuevo
 * orden con [Reorder.move] y se persiste.
 *
 * El reordenado visual es "diferido" (solo se mueve la fila arrastrada) para no
 * reordenar la lista a mitad del gesto: relayout + claves que cambian en pleno
 * arrastre es la fuente clásica de saltos y gestos que se pierden.
 */
class ReorderState {

    private var draggingId by mutableStateOf<String?>(null)
    private var offsetY by mutableFloatStateOf(0f)
    private var itemHeightPx by mutableFloatStateOf(0f)

    /**
     * Modificador de una fila: eleva y desplaza la que se arrastra, mide su alto
     * (para el cálculo de destino) y arranca el arrastre tras una pulsación
     * larga. [index] entra en la clave del `pointerInput`: si la fila cambia de
     * posición entre gestos, el bloque se recrea con el índice nuevo.
     */
    fun itemModifier(
        id: String,
        index: Int,
        itemCount: Int,
        onDrop: (from: Int, to: Int) -> Unit,
    ): Modifier {
        val dragging = draggingId == id
        return Modifier
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer { translationY = if (dragging) offsetY else 0f }
            .onSizeChanged { itemHeightPx = it.height.toFloat() }
            .pointerInput(id, index) {
                var start = 0
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        start = index
                        draggingId = id
                        offsetY = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        offsetY += amount.y
                    },
                    onDragEnd = {
                        val to = Reorder.targetIndex(start, offsetY, itemHeightPx, itemCount)
                        draggingId = null
                        offsetY = 0f
                        onDrop(start, to)
                    },
                    onDragCancel = {
                        draggingId = null
                        offsetY = 0f
                    }
                )
            }
    }
}
