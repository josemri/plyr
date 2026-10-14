package com.plyr.desktop.download

import com.plyr.database.TrackRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Flujo de [DownloadController]: planificación de lo pendiente (una por vídeo),
 * progreso, resultado, cancelación y limpieza del almacén. La descarga va con un
 * fetcher inyectado, así que no se toca la red.
 */
class DownloadControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = DownloadedAudioStore(directory = tmp.newFolder().toPath())

    private fun track(id: String, videoId: String?) = TrackRecord(
        id = id,
        playlistId = "pl",
        remoteTrackId = "remote_$id",
        name = "track $id",
        artists = "artist",
        youtubeVideoId = videoId,
        audioUrl = null,
        position = 0,
        lastSyncTime = 0,
    )

    /** Fetcher que escribe un byte por descarga y cuenta las llamadas. */
    private fun countingFetcher(failFor: Set<String> = emptySet()): Pair<AudioFetcher, () -> Int> {
        var calls = 0
        val fetcher = AudioFetcher { url, sink, _ ->
            calls++
            val id = url.substringAfterLast('/')
            if (id in failFor) {
                false
            } else {
                sink.write(byteArrayOf(1))
                true
            }
        }
        return fetcher to { calls }
    }

    private fun controller(
        scope: CoroutineScope,
        store: DownloadedAudioStore,
        fetcher: AudioFetcher,
    ) = DownloadController(
        scope = scope,
        store = store,
        downloader = AudioDownloader(
            store = store,
            resolveAudioUrl = { "https://audio/$it" },
            fetcher = fetcher,
            retryDelayMs = 0,
        ),
    )

    /** Espera a que terminen las descargas lanzadas en [scope]. */
    private suspend fun awaitDownloads(scope: CoroutineScope) {
        scope.coroutineContext.job.children.forEach { it.join() }
    }

    @Test
    fun startDownload_bajaSoloLasPendientesUnaPorVideo() = runBlocking {
        val store = store()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val (fetcher, calls) = countingFetcher()
        val controller = controller(scope, store, fetcher)

        controller.startDownload(
            "pl",
            "Lista",
            listOf(track("a", "vidA"), track("b", null), track("c", "vidA"), track("d", "vidB")),
        )
        awaitDownloads(scope)

        val state = controller.state.value
        assertFalse(state.isDownloading)
        assertEquals(2, state.total) // vidA y vidB; la duplicada y la sin id se descartan
        assertEquals(2, state.completed)
        assertEquals(DownloadOutcome.DOWNLOADED, state.outcome)
        assertEquals(2, calls())
        assertTrue(store.isDownloaded("vidA"))
        assertTrue(store.isDownloaded("vidB"))
    }

    @Test
    fun startDownload_siTodoEstabaBajadoNoDescargaNada() = runBlocking {
        val store = store()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        store.write("vidA") { it.write(byteArrayOf(1)); true }
        val (fetcher, calls) = countingFetcher()
        val controller = controller(scope, store, fetcher)

        controller.startDownload("pl", "Lista", listOf(track("a", "vidA")))
        awaitDownloads(scope)

        assertEquals(DownloadOutcome.ALREADY_DOWNLOADED, controller.state.value.outcome)
        assertEquals(0, calls())
    }

    @Test
    fun startDownload_marcaParcialSiAlgunaFalla() = runBlocking {
        val store = store()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val (fetcher, _) = countingFetcher(failFor = setOf("vidB"))
        val controller = controller(scope, store, fetcher)

        controller.startDownload("pl", "Lista", listOf(track("a", "vidA"), track("b", "vidB")))
        awaitDownloads(scope)

        val state = controller.state.value
        assertEquals(DownloadOutcome.PARTIAL, state.outcome)
        assertEquals(1, state.completed)
        assertEquals(2, state.total)
        assertTrue(store.isDownloaded("vidA"))
        assertFalse(store.isDownloaded("vidB"))
    }

    @Test
    fun startDownload_ignoraSiYaHayUnaEnCurso() = runBlocking {
        val store = store()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fetcher = AudioFetcher { _, sink, _ ->
            started.complete(Unit)
            release.await()
            sink.write(byteArrayOf(1))
            true
        }
        val controller = controller(scope, store, fetcher)

        controller.startDownload("pl", "Primera", listOf(track("a", "vidA")))
        withTimeout(5_000) { started.await() }
        controller.startDownload("pl", "Segunda", listOf(track("b", "vidB")))

        controller.cancel()
        release.complete(Unit)
        assertEquals("Primera", controller.state.value.playlistTitle)
    }

    @Test
    fun cancel_detieneYMarcaCancelado() = runBlocking {
        val store = store()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fetcher = AudioFetcher { _, sink, _ ->
            started.complete(Unit)
            release.await()
            sink.write(byteArrayOf(1))
            true
        }
        val controller = controller(scope, store, fetcher)

        controller.startDownload("pl", "Lista", listOf(track("a", "vidA")))
        withTimeout(5_000) { started.await() }
        assertTrue(controller.state.value.isDownloading)

        controller.cancel()
        awaitDownloads(scope)

        val state = controller.state.value
        assertFalse(state.isDownloading)
        assertEquals(DownloadOutcome.CANCELLED, state.outcome)
        assertFalse(store.isDownloaded("vidA"))
    }

    @Test
    fun removeYClearSubenRevisionYPidenLosFicheros() = runBlocking {
        val store = store()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        store.write("vidA") { it.write(byteArrayOf(1)); true }
        store.write("vidB") { it.write(byteArrayOf(2)); true }
        val (fetcher, _) = countingFetcher()
        val controller = controller(scope, store, fetcher)

        controller.removeDownload("vidA")
        awaitDownloads(scope)
        assertFalse(controller.isDownloaded("vidA"))
        assertTrue(controller.state.value.revision >= 1)

        controller.clearDownloads()
        awaitDownloads(scope)
        assertEquals(0, controller.summary().count)
        assertFalse(controller.isDownloaded("vidB"))
    }

    @Test
    fun dismissResult_olvidaElDesenlace() = runBlocking {
        val store = store()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val (fetcher, _) = countingFetcher()
        val controller = controller(scope, store, fetcher)

        controller.startDownload("pl", "Lista", listOf(track("a", "vidA")))
        awaitDownloads(scope)
        assertEquals(DownloadOutcome.DOWNLOADED, controller.state.value.outcome)

        controller.dismissResult()
        assertNull(controller.state.value.outcome)
    }
}
