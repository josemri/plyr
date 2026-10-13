package com.plyr.desktop.ui.components

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

object Reorder {

    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        if (from !in items.indices || to !in items.indices || from == to) return items
        return items.toMutableList().also { it.add(to, it.removeAt(from)) }
    }

    fun targetIndex(from: Int, offsetY: Float, itemHeightPx: Float, count: Int): Int {
        if (count <= 0 || itemHeightPx <= 0f) return from.coerceIn(0, (count - 1).coerceAtLeast(0))
        val delta = (offsetY / itemHeightPx).roundToInt()
        return (from + delta).coerceIn(0, count - 1)
    }
}

class ReorderState {
    private var draggingId by mutableStateOf<String?>(null)
    private var offsetY by mutableFloatStateOf(0f)
    private var itemHeightPx by mutableFloatStateOf(0f)

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
