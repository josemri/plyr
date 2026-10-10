package com.plyr.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de `swipeActionFor`: la decisión "desplazamiento → acción" que estaba
 * embebida en las lambdas de `pointerInput` de `SongListItem`.
 */
class SongSwipeTest {

    private val threshold = 30f

    @Test
    fun offsetAboveThresholdIsRight() {
        assertEquals(SwipeAction.RIGHT, swipeActionFor(30.1f, threshold))
        assertEquals(SwipeAction.RIGHT, swipeActionFor(151f, threshold))
    }

    @Test
    fun offsetBelowNegativeThresholdIsLeft() {
        assertEquals(SwipeAction.LEFT, swipeActionFor(-30.1f, threshold))
        assertEquals(SwipeAction.LEFT, swipeActionFor(-201f, threshold))
    }

    @Test
    fun exactlyAtThresholdIsNeutral() {
        // El original usaba una comparación estricta: `>` y `<`, por lo que el
        // valor exacto del umbral devuelve la fila al centro.
        assertEquals(SwipeAction.NONE, swipeActionFor(threshold, threshold))
        assertEquals(SwipeAction.NONE, swipeActionFor(-threshold, threshold))
    }

    @Test
    fun insideThresholdIsNeutral() {
        assertEquals(SwipeAction.NONE, swipeActionFor(0f, threshold))
        assertEquals(SwipeAction.NONE, swipeActionFor(29f, threshold))
        assertEquals(SwipeAction.NONE, swipeActionFor(-29f, threshold))
    }

    @Test
    fun zeroThresholdMeansAnyOffsetCounts() {
        assertEquals(SwipeAction.RIGHT, swipeActionFor(0.1f, 0f))
        assertEquals(SwipeAction.LEFT, swipeActionFor(-0.1f, 0f))
    }

    @Test
    fun staleOffsetsAreNeutral() {
        assertEquals(SwipeAction.NONE, swipeActionFor(0f, threshold))
    }
}