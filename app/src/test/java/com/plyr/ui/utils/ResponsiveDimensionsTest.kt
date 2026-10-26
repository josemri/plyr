package com.plyr.ui.utils

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre la lógica pura que antes vivía dentro del composable y solo se podía
 * comprobar con la app en marcha (Lint avisaba de `LocalConfiguration`).
 */
class ResponsiveDimensionsTest {

    private fun assertDp(expected: Float, actual: Dp) =
        assertEquals(expected, actual.value, 0.01f)

    private fun assertSp(expected: Float, actual: TextUnit) =
        assertEquals(expected, actual.value, 0.01f)

    @Test
    fun compactPortraitUsesCompactSpacingAndImage() {
        val d = calculateResponsiveDimensions(screenWidthDp = 360, screenHeightDp = 800)

        assertDp(12f, d.screenPadding)
        assertDp(8f, d.contentPadding)
        assertDp(8f, d.itemSpacing)
        assertDp(16f, d.sectionSpacing)
        assertSp(14f, d.bodySize)
        assertSp(11f, d.captionSize)
        assertDp(48f, d.floatingControlsBottomPadding)
        assertDp(140f, d.contentBottomPadding)
        assertDp(288f, d.imageMaxWidth)
        assertDp(320f, d.imageMaxHeight)
        assertFalse(d.showSideBySideLayout)
    }

    @Test
    fun mediumPortraitUsesMediumSpacing() {
        val d = calculateResponsiveDimensions(screenWidthDp = 700, screenHeightDp = 900)

        assertDp(16f, d.screenPadding)
        assertDp(12f, d.contentPadding)
        assertDp(12f, d.itemSpacing)
        assertDp(24f, d.sectionSpacing)
        assertSp(15f, d.bodySize)
        assertSp(12f, d.captionSize)
        assertDp(48f, d.floatingControlsBottomPadding)
        assertDp(140f, d.contentBottomPadding)
        assertDp(490f, d.imageMaxWidth)
        assertDp(360f, d.imageMaxHeight)
        assertFalse(d.showSideBySideLayout)
    }

    @Test
    fun expandedLandscapeUsesSideBySideLayout() {
        val d = calculateResponsiveDimensions(screenWidthDp = 1000, screenHeightDp = 600)

        assertDp(24f, d.screenPadding)
        assertDp(16f, d.contentPadding)
        assertDp(16f, d.itemSpacing)
        assertDp(24f, d.sectionSpacing)
        assertSp(16f, d.bodySize)
        assertSp(13f, d.captionSize)
        assertDp(8f, d.floatingControlsBottomPadding)
        assertDp(80f, d.contentBottomPadding)
        assertDp(400f, d.imageMaxWidth)
        assertDp(300f, d.imageMaxHeight)
        assertTrue(d.showSideBySideLayout)
    }

    @Test
    fun compactHeightWinsOverLandscapeForVerticalSpacing() {
        val d = calculateResponsiveDimensions(screenWidthDp = 800, screenHeightDp = 400)

        assertDp(16f, d.screenPadding)
        assertDp(12f, d.contentPadding)
        assertDp(12f, d.itemSpacing)
        assertDp(12f, d.sectionSpacing)
        assertSp(15f, d.bodySize)
        assertSp(12f, d.captionSize)
        assertDp(4f, d.floatingControlsBottomPadding)
        assertDp(64f, d.contentBottomPadding)
        assertDp(320f, d.imageMaxWidth)
        assertDp(120f, d.imageMaxHeight)
        assertTrue(d.showSideBySideLayout)
    }

    @Test
    fun smallLandscapeDoesNotShowSideBySide() {
        val d = calculateResponsiveDimensions(screenWidthDp = 500, screenHeightDp = 400)

        assertDp(12f, d.screenPadding)
        assertDp(8f, d.contentPadding)
        assertSp(14f, d.bodySize)
        assertSp(11f, d.captionSize)
        assertDp(4f, d.floatingControlsBottomPadding)
        assertDp(64f, d.contentBottomPadding)
        assertDp(200f, d.imageMaxWidth)
        assertDp(120f, d.imageMaxHeight)
        assertFalse(d.showSideBySideLayout)
    }
}
