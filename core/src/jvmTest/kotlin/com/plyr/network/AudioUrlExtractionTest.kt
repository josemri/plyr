package com.plyr.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Extracción de URL de audio: deduplicación en vuelo (B5), salto de caché
 * (B4) y cancelaciones (B57).
 *
 * Se ejercita el núcleo con la extracción inyectada, así que ni NewPipe ni la
 * red entran en juego: lo que se comprueba es la política de caché, la de
 * "una sola extracción por video" y qué le pasa a quien esperaba cuando la
 * extracción se cancela.
 */
class AudioUrlExtractionTest {

    @Before
    fun setUp() {
        YouTubeManager.clearCache()
    }

    /** Extracción que tarda un poco, como la de verdad, y cuenta cuántas veces corre. */
    private class CountingExtractor(
        private val url: String?,
        private val delayMs: Long = 60
    ) {
        val calls = AtomicInteger(0)

        fun extract(): String? {
            calls.incrementAndGet()
            Thread.sleep(delayMs)
            return url
        }
    }

    @Test
    fun concurrentCallsForSameVideo_extractOnlyOnce() = runBlocking {
        val videoId = "b5-concurrent"
        val extractor = CountingExtractor("https://audio/$videoId")

        val results = coroutineScope {
            (1..8).map {
                async(Dispatchers.IO) {
                    YouTubeManager.getAudioUrl(videoId, false) { extractor.extract() }
                }
            }.awaitAll()
        }

        assertEquals("Solo una extracción para ocho peticiones", 1, extractor.calls.get())
        assertEquals(8, results.size)
        results.forEach { assertEquals("https://audio/$videoId", it) }
    }

    @Test
    fun inFlightExtraction_isSharedWithLaterCallInSameWindow() = runBlocking {
        val videoId = "b5-inflight"
        val extractor = CountingExtractor("https://audio/$videoId")

        val first = async(Dispatchers.IO) {
            YouTubeManager.getAudioUrl(videoId, false) { extractor.extract() }
        }
        // Llega mientras la primera sigue extrayendo: espera a su resultado.
        val second = YouTubeManager.getAudioUrl(videoId, false) { extractor.extract() }

        assertEquals("https://audio/$videoId", first.await())
        assertEquals("https://audio/$videoId", second)
        assertEquals(1, extractor.calls.get())
    }

    @Test
    fun sequentialCalls_useCache() = runBlocking {
        val videoId = "b5-cache"
        val extractor = CountingExtractor("https://audio/$videoId")

        val first = YouTubeManager.getAudioUrl(videoId, false) { extractor.extract() }
        val second = YouTubeManager.getAudioUrl(videoId, false) { extractor.extract() }

        assertEquals("https://audio/$videoId", first)
        assertEquals("https://audio/$videoId", second)
        assertEquals("La segunda debe salir de caché", 1, extractor.calls.get())
    }

    @Test
    fun forceRefresh_reextractsInsteadOfReturningCachedUrl() = runBlocking {
        val videoId = "b4-forcerefresh"
        val stale = CountingExtractor("https://audio/stale")
        val fresh = CountingExtractor("https://audio/fresh")

        YouTubeManager.getAudioUrl(videoId, false) { stale.extract() }
        val retried = YouTubeManager.getAudioUrl(videoId, true) { fresh.extract() }

        assertEquals("Debe llegar la URL nueva, no la caducada", "https://audio/fresh", retried)
        assertEquals(1, stale.calls.get())
        assertEquals(1, fresh.calls.get())
        assertEquals("https://audio/fresh", YouTubeManager.cachedUrl(videoId))
    }

    @Test
    fun invalidate_forcesReextractionOnNextCall() = runBlocking {
        val videoId = "b4-invalidate"
        val stale = CountingExtractor("https://audio/stale")
        val fresh = CountingExtractor("https://audio/fresh")

        YouTubeManager.getAudioUrl(videoId, false) { stale.extract() }
        YouTubeManager.invalidate(videoId)
        assertNull(YouTubeManager.cachedUrl(videoId))

        val retried = YouTubeManager.getAudioUrl(videoId, false) { fresh.extract() }
        assertEquals("https://audio/fresh", retried)
        assertEquals(1, stale.calls.get())
        assertEquals("Tras invalidar, la extracción corre de nuevo", 1, fresh.calls.get())
    }

    @Test
    fun failedExtraction_isNotCachedAndCanBeRetried() = runBlocking {
        val videoId = "b5-failure"
        val failing = CountingExtractor(null, delayMs = 10)
        val working = CountingExtractor("https://audio/$videoId")

        assertNull(YouTubeManager.getAudioUrl(videoId, false) { failing.extract() })
        assertNull("Un fallo no debe quedar en caché", YouTubeManager.cachedUrl(videoId))
        assertEquals(
            "https://audio/$videoId",
            YouTubeManager.getAudioUrl(videoId, false) { working.extract() }
        )
    }

    @Test
    fun differentVideos_doNotShareExtraction() = runBlocking {
        val first = CountingExtractor("https://audio/one")
        val second = CountingExtractor("https://audio/two")

        val results = coroutineScope {
            listOf(
                async(Dispatchers.IO) { YouTubeManager.getAudioUrl("b5-distinct-1", false) { first.extract() } },
                async(Dispatchers.IO) { YouTubeManager.getAudioUrl("b5-distinct-2", false) { second.extract() } }
            ).awaitAll()
        }

        assertEquals("https://audio/one", results[0])
        assertEquals("https://audio/two", results[1])
        assertEquals(1, first.calls.get())
        assertEquals(1, second.calls.get())
    }

    @Test
    fun slowExtraction_doesNotBlockOtherVideos() = runBlocking {
        val slow = CountingExtractor("https://audio/slow", delayMs = 300)
        val fast = CountingExtractor("https://audio/fast", delayMs = 10)

        val results = coroutineScope {
            listOf(
                async(Dispatchers.IO) { YouTubeManager.getAudioUrl("b5-slow", false) { slow.extract() } },
                async(Dispatchers.IO) { YouTubeManager.getAudioUrl("b5-fast", false) { fast.extract() } }
            ).awaitAll()
        }

        assertEquals("https://audio/slow", results[0])
        assertEquals("https://audio/fast", results[1])
    }

    @Test
    fun cachedUrl_expiresAfterTtl() = runBlocking {
        val videoId = "b5-ttl"
        val extractor = CountingExtractor("https://audio/$videoId")
        YouTubeManager.getAudioUrl(videoId, false) { extractor.extract() }

        assertEquals("https://audio/$videoId", YouTubeManager.cachedUrl(videoId))
        delay(1)
        assertEquals("https://audio/$videoId", YouTubeManager.cachedUrl(videoId))
    }

    // --- B57: cancelaciones ---

    /**
     * El productor se va sin haber extraído nada. Para quien esperaba eso no es
     * un fallo, es que el intento no llegó a serlo: si se le devolvía `null`
     * descartaba la canción y el `>>` no encontraba nada que reproducir. Tiene
     * que volver a intentarlo él mismo.
     */
    @Test
    fun extraccionCancelada_elQueEsperabaLoVuelveAEintentar() = runBlocking {
        val videoId = "b57-interrupted"
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)

        // La extracción no acaba nunca hasta que lo liberemos, y aun así no
        // devuelve nada: es lo que pasa cuando se corta a mitad.
        val producer = async(Dispatchers.IO) {
            YouTubeManager.getAudioUrl(videoId, false) {
                started.countDown()
                release.await()
                null
            }
        }
        assertTrue("la extracción debe empezar", started.await(5, TimeUnit.SECONDS))

        val waiter = async(Dispatchers.IO) {
            YouTubeManager.getAudioUrl(videoId, false) { "https://audio/$videoId" }
        }
        // Sirve tanto si entra mientras la primera sigue en vuelo como si llega
        // ya después de cancelarla: las dos rutas acaban reintentando.
        Thread.sleep(200)

        producer.cancel()
        release.countDown()

        assertEquals("https://audio/$videoId", waiter.await())
        producer.join()
    }

    /**
     * El caso contrario: cancelar a quien esperaba no puede arrastrar a quien
     * estaba extrayendo, ni contar la extracción dos veces.
     */
    @Test
    fun cancelarAlQueEsperaba_noMolestaAlProductor() = runBlocking {
        val videoId = "b57-waiter-cancel"
        val started = CountDownLatch(1)
        val extractor = CountingExtractor("https://audio/$videoId", delayMs = 500)

        val producer = async(Dispatchers.IO) {
            YouTubeManager.getAudioUrl(videoId, false) {
                started.countDown()
                extractor.extract()
            }
        }
        assertTrue("la extracción debe empezar", started.await(5, TimeUnit.SECONDS))

        val waiter = async(Dispatchers.IO) {
            YouTubeManager.getAudioUrl(videoId, false) { extractor.extract() }
        }
        Thread.sleep(100)
        waiter.cancel()
        waiter.join()

        assertEquals("https://audio/$videoId", producer.await())
        assertEquals("Solo la extracción del productor", 1, extractor.calls.get())
        assertEquals("https://audio/$videoId", YouTubeManager.cachedUrl(videoId))
    }
}
