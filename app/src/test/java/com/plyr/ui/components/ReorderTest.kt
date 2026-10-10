package com.plyr.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests de `Reorder`: la decisión "índice de destino → nueva lista" del
 * reordenado por arrastre. Son los cálculos donde se cuelan los off-by-one.
 */
class ReorderTest {

    @Test
    fun moveDownShiftsIntermediatesUp() {
        assertEquals(listOf("a", "c", "d", "b"), Reorder.move(listOf("a", "b", "c", "d"), 1, 3))
    }

    @Test
    fun moveUpShiftsIntermediatesDown() {
        assertEquals(listOf("a", "d", "b", "c"), Reorder.move(listOf("a", "b", "c", "d"), 3, 1))
    }

    @Test
    fun moveWithinRangeKeepsSize() {
        val result = Reorder.move(listOf(1, 2, 3, 4, 5), 0, 4)
        assertEquals(5, result.size)
        assertEquals(listOf(2, 3, 4, 5, 1), result)
    }

    @Test
    fun noOpReturnsSameInstance() {
        val items = listOf("a", "b", "c")
        assertSame(items, Reorder.move(items, 1, 1))
    }

    @Test
    fun outOfRangeReturnsSameInstance() {
        val items = listOf("a", "b", "c")
        assertSame(items, Reorder.move(items, -1, 1))
        assertSame(items, Reorder.move(items, 0, 3))
        assertSame(items, Reorder.move(items, 5, 1))
    }

    @Test
    fun targetIndexRoundsToNearestSlot() {
        assertEquals(1, Reorder.targetIndex(from = 1, offsetY = 0f, itemHeightPx = 100f, count = 5))
        assertEquals(2, Reorder.targetIndex(from = 1, offsetY = 51f, itemHeightPx = 100f, count = 5))
        assertEquals(1, Reorder.targetIndex(from = 1, offsetY = 49f, itemHeightPx = 100f, count = 5))
        assertEquals(0, Reorder.targetIndex(from = 1, offsetY = -100f, itemHeightPx = 100f, count = 5))
    }

    @Test
    fun targetIndexClampsToBounds() {
        assertEquals(4, Reorder.targetIndex(from = 4, offsetY = 999f, itemHeightPx = 100f, count = 5))
        assertEquals(0, Reorder.targetIndex(from = 0, offsetY = -999f, itemHeightPx = 100f, count = 5))
    }

    @Test
    fun targetIndexWithoutMeasurementReturnsFrom() {
        assertEquals(2, Reorder.targetIndex(from = 2, offsetY = 300f, itemHeightPx = 0f, count = 5))
    }
}
