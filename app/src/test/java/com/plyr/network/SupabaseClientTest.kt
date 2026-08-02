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
    fun parseTimestamp_invalidFallsBackToNow() {
        val before = System.currentTimeMillis()
        val parsed = parseTimestamp("not-a-date")
        val after = System.currentTimeMillis()
        assertTrue("parsed=$parsed", parsed in before..after)
    }

    private fun parseTimestamp(timestamp: String): Long {
        val method = SupabaseClient::class.java.getDeclaredMethod("parseTimestamp", String::class.java)
        method.isAccessible = true
        return method.invoke(SupabaseClient, timestamp) as Long
    }
}
