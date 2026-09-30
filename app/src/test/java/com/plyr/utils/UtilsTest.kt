package com.plyr.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de utilidades de formateo de tiempo.
 */
class UtilsTest {

    @Test
    fun formatTime_zero() {
        assertEquals("00:00", formatTime(0))
    }

    @Test
    fun formatTime_secondsOnly() {
        assertEquals("00:45", formatTime(45_000))
    }

    @Test
    fun formatTime_minutesAndSeconds() {
        assertEquals("03:45", formatTime(225_000))
    }

    @Test
    fun formatTime_exactMinute() {
        assertEquals("01:00", formatTime(60_000))
    }

    @Test
    fun formatTime_moreThanAnHour() {
        assertEquals("61:01", formatTime(3_661_000))
    }

    @Test
    fun formatTime_roundsDownSubSecond() {
        assertEquals("00:00", formatTime(999))
    }

    @Test
    fun formatTime_oneAndAHalfSeconds() {
        assertEquals("00:01", formatTime(1_500))
    }

    @Test
    fun formatTimestamp_nowUnderAMinute() {
        assertEquals("now", formatTimestamp(System.currentTimeMillis() - 30_000))
    }

    @Test
    fun formatTimestamp_minutes() {
        assertEquals("5m", formatTimestamp(System.currentTimeMillis() - 5 * 60_000))
    }

    @Test
    fun formatTimestamp_hours() {
        assertEquals("3h", formatTimestamp(System.currentTimeMillis() - 3 * 60 * 60_000))
    }

    @Test
    fun formatTimestamp_days() {
        assertEquals("2d", formatTimestamp(System.currentTimeMillis() - 2 * 24 * 60 * 60_000))
    }

    @Test
    fun formatDurationMs_minutesNotPadded() {
        assertEquals("3:45", formatDurationMs(3 * 60_000 + 45_000))
    }

    @Test
    fun formatDurationMs_zero() {
        assertEquals("0:00", formatDurationMs(0))
    }

    @Test
    fun formatDurationMs_lessThanMinute() {
        assertEquals("0:09", formatDurationMs(9_500))
    }

    @Test
    fun formatDurationSeconds_underHour() {
        assertEquals("3:45", formatDurationSeconds(225))
    }

    @Test
    fun formatDurationSeconds_hours() {
        assertEquals("1:02:03", formatDurationSeconds(3723))
    }

    @Test
    fun formatDurationSeconds_live() {
        assertEquals("En vivo", formatDurationSeconds(0))
    }
}
