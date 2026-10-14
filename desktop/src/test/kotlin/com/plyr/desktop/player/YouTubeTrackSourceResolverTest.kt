package com.plyr.desktop.player

import com.plyr.desktop.download.DownloadedAudioStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

/**
 * Resolución de la fuente de audio (M16): el fichero descargado debe ganar a la
 * URL de streaming, y el streaming queda como respaldo. YouTube se inyecta, así
 * que el test no toca la red.
 */
class YouTubeTrackSourceResolverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = DownloadedAudioStore(directory = tmp.newFolder().toPath())

    private fun track(id: String, videoId: String? = null) =
        PlayerTrack(id = id, title = "Track $id", artist = "Artist $id", videoId = videoId)

    @Test
    fun resolve_prefiereElFicheroDescargado() = runBlocking {
        val store = store()
        Files.createDirectories(store.directory)
        Files.write(store.fileFor("vid"), byteArrayOf(1, 2, 3))
        var streamingCalls = 0
        val resolver = YouTubeTrackSourceResolver(
            store = store,
            resolveVideoId = { _, _ -> error("no debería buscar el id") },
            streamingUrl = { _, _ -> streamingCalls++; "https://stream" },
        )

        val resolved = resolver.resolve(track("a", "vid"), knownVideoId = null, forceRefresh = false)

        assertEquals("vid", resolved?.videoId)
        assertTrue(resolved?.uri!!.startsWith("file:"))
        assertEquals(0, streamingCalls)
    }

    @Test
    fun resolve_haceStreamingSiNoEstaDescargado() = runBlocking {
        val store = store()
        val resolver = YouTubeTrackSourceResolver(
            store = store,
            resolveVideoId = { _, _ -> null },
            streamingUrl = { _, _ -> "https://stream/vid" },
        )

        val resolved = resolver.resolve(track("a", "vid"), knownVideoId = null, forceRefresh = false)

        assertEquals(ResolvedSource("vid", "https://stream/vid"), resolved)
    }

    @Test
    fun resolve_pasaForceRefreshAlStreaming() = runBlocking {
        val store = store()
        var seenForceRefresh: Boolean? = null
        val resolver = YouTubeTrackSourceResolver(
            store = store,
            resolveVideoId = { _, _ -> null },
            streamingUrl = { _, force -> seenForceRefresh = force; "https://stream" },
        )

        resolver.resolve(track("a", "vid"), knownVideoId = null, forceRefresh = true)

        assertEquals(true, seenForceRefresh)
    }

    @Test
    fun resolve_buscaElIdPorTituloSiLaPistaNoLoTiene() = runBlocking {
        val store = store()
        Files.createDirectories(store.directory)
        Files.write(store.fileFor("found"), byteArrayOf(1))
        var searched: Pair<String, String>? = null
        val resolver = YouTubeTrackSourceResolver(
            store = store,
            resolveVideoId = { title, artist -> searched = title to artist; "found" },
            streamingUrl = { _, _ -> error("no debería hacer streaming") },
        )

        val resolved = resolver.resolve(track("a"), knownVideoId = null, forceRefresh = false)

        assertEquals("found", resolved?.videoId)
        assertEquals("Track a" to "Artist a", searched)
    }

    @Test
    fun resolve_devuelveNullSiNoHayVideoNiBusqueda() = runBlocking {
        val resolver = YouTubeTrackSourceResolver(
            store = store(),
            resolveVideoId = { _, _ -> null },
            streamingUrl = { _, _ -> "https://stream" },
        )

        assertNull(resolver.resolve(track("a"), knownVideoId = null, forceRefresh = false))
    }

    @Test
    fun resolve_devuelveNullSiElStreamingFalla() = runBlocking {
        val resolver = YouTubeTrackSourceResolver(
            store = store(),
            resolveVideoId = { _, _ -> null },
            streamingUrl = { _, _ -> null },
        )

        assertNull(resolver.resolve(track("a", "vid"), knownVideoId = null, forceRefresh = false))
    }
}
