package com.plyr.desktop.utils

import com.plyr.utils.CoverCacheKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

/**
 * Almacenamiento en disco de [CoverCache]. Se inyectan la carpeta y la descarga
 * para no tocar ni la red ni la caché real del usuario.
 */
class CoverCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun cacheFile_usaLaClaveCompartidaConAndroid() {
        val dir = tmp.newFolder().toPath()
        val cache = CoverCache(directory = dir, download = { null })

        val url = "https://example.com/cover.jpg"
        assertEquals(dir.resolve(CoverCacheKey.cacheKey(url) + ".jpg"), cache.cacheFile(url))
    }

    @Test
    fun load_descargaUnaVezYLuegoLeeDeDisco() = runBlocking {
        val dir = tmp.newFolder().toPath()
        var descargas = 0
        val bytes = byteArrayOf(1, 2, 3, 4)
        val cache = CoverCache(directory = dir, download = { descargas++; bytes })

        assertArrayEquals(bytes, cache.load("https://example.com/a.jpg"))
        // La segunda vez no vuelve a descargar: sale del archivo cacheado.
        assertArrayEquals(bytes, cache.load("https://example.com/a.jpg"))
        assertEquals(1, descargas)
    }

    @Test
    fun load_devuelveNullYNoCacheaSiLaDescargaFalla() = runBlocking {
        val dir = tmp.newFolder().toPath()
        val cache = CoverCache(directory = dir, download = { null })

        assertNull(cache.load("https://example.com/falla.jpg"))
        assertTrue(Files.list(dir).use { it.count() } == 0L)
    }

    @Test
    fun load_ignoraArchivosVaciosOCorruptos() = runBlocking {
        val url = "https://example.com/vacia.jpg"
        val dir = tmp.newFolder().toPath()
        val cache = CoverCache(directory = dir, download = { null })
        // Un archivo vacío no cuenta como portada válida: se reintenta la descarga.
        Files.createDirectories(dir)
        Files.write(cache.cacheFile(url), ByteArray(0))

        assertNull(cache.load(url))
    }
}
