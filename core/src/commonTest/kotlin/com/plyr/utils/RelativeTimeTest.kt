package com.plyr.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests del formateo de tiempo relativo compartido (M12).
 *
 * Se movieron desde `UtilsTest` de la app al compartir `formatTimestamp` en
 * `:core`.
 */
class RelativeTimeTest {

    @Test
    fun nowUnderAMinute() {
        assertEquals("now", formatTimestamp(System.currentTimeMillis() - 30_000))
    }

    @Test
    fun minutes() {
        assertEquals("5m", formatTimestamp(System.currentTimeMillis() - 5 * 60_000))
    }

    @Test
    fun hours() {
        assertEquals("3h", formatTimestamp(System.currentTimeMillis() - 3 * 60 * 60_000))
    }

    @Test
    fun days() {
        assertEquals("2d", formatTimestamp(System.currentTimeMillis() - 2 * 24 * 60 * 60_000))
    }

    @Test
    fun unknownTimestamp() {
        // 0L = timestamp ilegible/desconocido (B25)
        assertEquals("unknown", formatTimestamp(0L))
    }
}
