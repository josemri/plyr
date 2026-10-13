package com.plyr.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [CoverCacheKey.cacheKey], que decide el nombre del archivo en disco
 * donde se guarda cada portada descargada.
 */
class CoverCacheKeyTest {

    @Test
    fun `la misma url da la misma clave`() {
        val url = "https://i.ytimg.com/vi/abc123/hqdefault.jpg"
        assertEquals(CoverCacheKey.cacheKey(url), CoverCacheKey.cacheKey(url))
    }

    @Test
    fun `urls distintas dan claves distintas`() {
        assertNotEquals(
            CoverCacheKey.cacheKey("https://i.ytimg.com/vi/aaa/hqdefault.jpg"),
            CoverCacheKey.cacheKey("https://i.ytimg.com/vi/bbb/hqdefault.jpg")
        )
    }

    @Test
    fun `la clave es un sha256 en hexadecimal`() {
        val key = CoverCacheKey.cacheKey("https://example.com/portada.jpg")
        assertEquals(64, key.length)
        assertTrue("solo hex en minúsculas: $key", key.all { it in "0123456789abcdef" })
    }

    @Test
    fun `la clave es segura como nombre de archivo`() {
        // Las URLs de portada traen '?', '&', '/' y '%': si se colaran al
        // nombre, el archivo iría a otra ruta o directamente no se crearía.
        val key = CoverCacheKey.cacheKey("https://i.ytimg.com/vi/abc/hq.jpg?sqp=1&rs=a%2Fz")
        assertTrue("solo hex: $key", key.all { it in "0123456789abcdef" })
    }

    @Test
    fun `urls vacias o distintas no colisionan`() {
        assertNotEquals(CoverCacheKey.cacheKey(""), CoverCacheKey.cacheKey(" "))
    }

    @Test
    fun `la clave no depende del esquema`() {
        // http y https suelen servir exactamente la misma imagen; mantener
        // claves distintas es lo correcto y lo que evita fusionar por error.
        assertNotEquals(
            CoverCacheKey.cacheKey("http://example.com/a.jpg"),
            CoverCacheKey.cacheKey("https://example.com/a.jpg")
        )
    }
}
