package com.plyr.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de lógica pura de SupabaseClient: código de invitación y parseo de timestamps.
 */
class SupabaseClientTest {

    @Test
    fun generateInviteCode_lengthIsEight() {
        assertEquals(8, SupabaseClient.generateInviteCode().length)
    }

    @Test
    fun generateInviteCode_isUppercase() {
        val code = SupabaseClient.generateInviteCode()
        assertEquals(code.uppercase(), code)
    }

    @Test
    fun generateInviteCode_containsOnlyHexChars() {
        val allowed = "0123456789ABCDEF"
        assertTrue(SupabaseClient.generateInviteCode().all { it in allowed })
    }

    @Test
    fun generateInviteCode_uniqueAcrossCalls() {
        assertNotEquals(SupabaseClient.generateInviteCode(), SupabaseClient.generateInviteCode())
    }

    @Test
    fun parseTimestamp_isoWithMillis() {
        assertEquals(1_736_677_800_000L, parseTimestamp("2025-01-12T10:30:00.000Z"))
    }

    @Test
    fun parseTimestamp_isoWithoutMillis() {
        assertEquals(1_736_677_800_000L, parseTimestamp("2025-01-12T10:30:00Z"))
    }

    @Test
    fun parseTimestamp_isoWithOffset() {
        // 10:30 +02:00 == 08:30 UTC (B25: el offset explícito ya no se descarta)
        assertEquals(1_736_670_600_000L, parseTimestamp("2025-01-12T10:30:00+02:00"))
    }

    @Test
    fun parseTimestamp_invalidReturnsZeroNotNow() {
        // B25: una fecha ilegible ya no se disfraza de "ahora"
        assertEquals(0L, parseTimestamp("not-a-date"))
    }

    @Test
    fun parseTimestamp_blankReturnsZero() {
        assertEquals(0L, parseTimestamp(""))
    }

    @Test
    fun parseTimestamp_rejectsTrailingGarbage() {
        // El patrón sin milis no debe aceptar texto sobrante tras la 'Z'
        assertEquals(0L, parseTimestamp("2025-01-12T10:30:00Zjunk"))
    }

    private fun parseTimestamp(timestamp: String): Long {
        val method = SupabaseClient::class.java.getDeclaredMethod("parseTimestamp", String::class.java)
        method.isAccessible = true
        return method.invoke(SupabaseClient, timestamp) as Long
    }
}
