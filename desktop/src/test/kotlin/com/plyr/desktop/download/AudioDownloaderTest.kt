package com.plyr.desktop.download

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Orquestación de [AudioDownloader] con la resolución de URL y la descarga
 * inyectadas: reintentos, "ya descargado", progreso y fallo definitivo, sin red
 * ni disco del usuario.
 */
class AudioDownloaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = DownloadedAudioStore(directory = tmp.newFolder().toPath())

    /** Fetcher que escribe [bytes] y avisa del progreso; las veces de [failures] falla. */
    private fun fetcher(
        bytes: ByteArray = byteArrayOf(1, 2, 3),
        failures: Int = 0,
    ): Pair<AudioFetcher, () -> Int> {
        var calls = 0
        val fetcher = AudioFetcher { _, sink, onProgress ->
            calls++
            if (calls <= failures) {
                false
            } else {
                sink.write(bytes)
                onProgress(0.5f)
                true
            }
        }
        return fetcher to { calls }
    }

    private fun downloader(
        store: DownloadedAudioStore,
        fetcher: AudioFetcher,
        resolve: suspend (String) -> String? = { "https://audio/$it" },
        maxRetries: Int = 3,
    ) = AudioDownloader(
        store = store,
        resolveAudioUrl = resolve,
        fetcher = fetcher,
        maxRetries = maxRetries,
        retryDelayMs = 0,
    )

    @Test
    fun download_escribeElFicheroYTerminaAl100() = runBlocking {
        val store = store()
        val (fetch, _) = fetcher()
        val progress = mutableListOf<Float>()

        val ok = downloader(store, fetch).download("vid") { progress += it }

        assertTrue(ok)
        assertTrue(store.isDownloaded("vid"))
        assertEquals(1f, progress.last(), 0f)
    }

    @Test
    fun download_noVuelveABajarLoQueYaEstaba() = runBlocking {
        val store = store()
        val (fetch, calls) = fetcher()
        store.write("vid") { it.write(byteArrayOf(9)); true }

        val ok = downloader(store, fetch).download("vid")

        assertTrue(ok)
        assertEquals(0, calls())
    }

    @Test
    fun download_reintentaHastaTenerExito() = runBlocking {
        val store = store()
        val (fetch, calls) = fetcher(failures = 2)

        val ok = downloader(store, fetch).download("vid")

        assertTrue(ok)
        assertEquals(3, calls())
        assertTrue(store.isDownloaded("vid"))
    }

    @Test
    fun download_fallaTrasAgotarReintentos() = runBlocking {
        val store = store()
        val (fetch, calls) = fetcher(failures = 99)

        val ok = downloader(store, fetch).download("vid")

        assertFalse(ok)
        assertEquals(3, calls())
        assertFalse(store.isDownloaded("vid"))
    }

    @Test
    fun download_resuelveLaUrlEnCadaIntento() = runBlocking {
        val store = store()
        val (fetch, _) = fetcher(failures = 1)
        var resolutions = 0
        val downloader = downloader(store, fetch, resolve = { resolutions++; "https://audio/$it" })

        assertTrue(downloader.download("vid"))
        assertEquals(2, resolutions)
    }

    @Test
    fun download_reintentaSiLaUrlNoSeResuelve() = runBlocking {
        val store = store()
        val (fetch, calls) = fetcher()
        var resolutions = 0
        val downloader = downloader(store, fetch, resolve = {
            resolutions++
            if (resolutions < 2) null else "https://audio/$it"
        })

        assertTrue(downloader.download("vid"))
        assertEquals(2, resolutions)
        assertEquals(1, calls())
    }
}
