package com.plyr.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B32: el mapa de cookies se compartía entre hilos sin sincronizar y los logs
 * volcaban cookies, cabeceras y cuerpos de respuesta enteros (que incluyen
 * tokens y datos de la cuenta).
 *
 * Aquí se certifica la parte pura: qué se envía, qué se puede loguear y cómo se
 * resume el estado del Player API sin filtrar la respuesta.
 */
class SimpleDownloaderLogRedactionTest {

    // ---------- buildCookieHeader ----------

    @Test
    fun joinsBothCookieSources() {
        assertEquals(
            "PREF=f2=8000000; SID=abc",
            buildCookieHeader("PREF=f2=8000000", "SID=abc")
        )
    }

    @Test
    fun dropsMissingSources() {
        assertEquals("PREF=f2=8000000", buildCookieHeader("PREF=f2=8000000", null))
        assertEquals("", buildCookieHeader(null, null))
    }

    @Test
    fun dropsBlankSources() {
        assertEquals("SID=abc", buildCookieHeader("", "SID=abc"))
        assertEquals("", buildCookieHeader("", "   "))
    }

    @Test
    fun removesDuplicatePairs() {
        assertEquals(
            "PREF=f2=8000000; SID=abc",
            buildCookieHeader("PREF=f2=8000000", "PREF=f2=8000000; SID=abc; PREF=f2=8000000")
        )
    }

    @Test
    fun ignoresBlankSegments() {
        assertEquals("SID=abc", buildCookieHeader("; SID=abc; "))
    }

    // ---------- describeCookieNames ----------

    @Test
    fun describeCookieNames_neverLeaksValues() {
        val header = "PREF=f2=8000000; SID=super-secreto; HSID=otro-token"
        val described = describeCookieNames(header)

        assertEquals("PREF; SID; HSID", described)
        listOf("f2=8000000", "super-secreto", "otro-token").forEach { secret ->
            assertFalse("El log de cookies filtró '$secret'", described.contains(secret))
        }
    }

    @Test
    fun describeCookieNames_deduplicates() {
        assertEquals("PREF; SID", describeCookieNames("PREF=a; SID=b; PREF=c"))
    }

    @Test
    fun describeCookieNames_ofEmptyHeaderIsEmpty() {
        assertEquals("", describeCookieNames(""))
    }

    // ---------- describeHeaders ----------

    @Test
    fun describeHeaders_hidesAuthorizationAndCookies() {
        val described = describeHeaders(
            linkedMapOf(
                "Authorization" to listOf("SAPISIDHASH con-clave"),
                "Cookie" to listOf("SID=secreto"),
                "Set-Cookie" to listOf("SID=otro-secreto"),
                "X-Goog-Visitor-Id" to listOf("visitor-secreto")
            )
        )

        listOf("con-clave", "SID=secreto", "otro-secreto", "visitor-secreto").forEach { secret ->
            assertFalse("El log de cabeceras filtró '$secret'", described.contains(secret))
        }
        assertTrue("Debe indicar que Authorization se ocultó", described.contains("Authorization"))
    }

    @Test
    fun describeHeaders_isCaseInsensitive() {
        val described = describeHeaders(linkedMapOf("AuThOrIzAtIoN" to listOf("secreto")))
        assertFalse(described.contains("secreto"))
    }

    @Test
    fun describeHeaders_keepsHarmlessHeaders() {
        val described = describeHeaders(linkedMapOf("User-Agent" to listOf("Firefox/140.0")))
        assertEquals("   User-Agent: Firefox/140.0", described)
    }

    @Test
    fun describeHeaders_joinsRepeatedValues() {
        val described = describeHeaders(linkedMapOf("Accept" to listOf("a", "b")))
        assertEquals("   Accept: a, b", described)
    }

    @Test
    fun describeHeaders_isSortedAndCountsRedactedLength() {
        val described = describeHeaders(
            linkedMapOf(
                "User-Agent" to listOf("Firefox/140.0"),
                "Cookie" to listOf("0123456789")
            )
        )
        val lines = described.lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("   Cookie:"))
        assertTrue(lines[1].startsWith("   User-Agent:"))
        assertTrue("Debe reportar el tamaño del valor oculto", described.contains("10 chars"))
    }

    @Test
    fun describeHeaders_ofEmptyMapIsEmpty() {
        assertEquals("", describeHeaders(emptyMap()))
    }

    // ---------- playabilityStatusOf ----------

    @Test
    fun playabilityStatusOf_readsStatus() {
        val body = """{"playabilityStatus":{"status":"OK","reason":null},"streamingData":{}}"""
        assertEquals("OK", playabilityStatusOf(body))
    }

    @Test
    fun playabilityStatusOf_readsLoginRequired() {
        val body = """{"playabilityStatus":{"status":"LOGIN_REQUIRED","reason":"x"}}"""
        assertEquals("LOGIN_REQUIRED", playabilityStatusOf(body))
    }

    @Test
    fun playabilityStatusOf_nullWhenAbsent() {
        assertNull(playabilityStatusOf("""{"foo":"bar"}"""))
        assertNull(playabilityStatusOf(""))
    }

    @Test
    fun playabilityStatusOf_ignoresOtherStatusFields() {
        val body = """{"other":{"status":"IGNORED"},"playabilityStatus":{"status":"UNPLAYABLE"}}"""
        assertEquals("UNPLAYABLE", playabilityStatusOf(body))
    }
}
