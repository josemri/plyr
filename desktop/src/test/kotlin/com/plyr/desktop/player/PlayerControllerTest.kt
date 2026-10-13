package com.plyr.desktop.player

import com.plyr.domain.RepeatMode
import com.plyr.model.AudioItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de la orquestación de [PlayerController] sin VLC ni red.
 *
 * Es el "test del viewmodel" que pide la verificación de M7: cola + ventana
 * deslizante + navegación + repetición + errores, tal como hacía `PlayerViewModel`
 * en Android, pero sobre el doble [FakeAudioPlayer] y un resolutor falso.
 */
class PlayerControllerTest {

    private lateinit var scope: CoroutineScope
    private lateinit var player: FakeAudioPlayer
    private lateinit var resolver: FakeResolver
    private lateinit var controller: PlayerController

    @Before
    fun setUp() {
        // Unconfined: cada `scope.launch` del controlador corre en línea hasta el
        // primer punto de suspensión real. Con resolutores falsos (sin red) todo
        // termina de forma síncrona, así que los tests son deterministas.
        scope = CoroutineScope(Dispatchers.Unconfined)
        player = FakeAudioPlayer()
        resolver = FakeResolver()
        controller = PlayerController(scope, { player }, resolver)
    }

    @After
    fun tearDown() {
        controller.release()
    }

    private fun track(id: String, videoId: String? = null): PlayerTrack =
        PlayerTrack(id = id, title = "Track $id", artist = "Artist $id", videoId = videoId)

    private val state get() = controller.state.value

    @Test
    fun play_resolvesFirstAndPrefetchesWindow() {
        controller.play(listOf(track("a"), track("b"), track("c")), 0)

        // Arranca con la primera y rellena la ventana con las dos siguientes.
        assertEquals(1, player.setMediaCalls.size)
        assertEquals(1, player.setMediaCalls.first().first.size)
        assertEquals(listOf("a", "b", "c"), player.loadedItems.map { it.mediaId.substringBefore('#') })
        assertTrue(player.isPlaying)

        assertEquals(3, state.queue.size)
        assertEquals(0, state.currentIndex)
        assertEquals("Track a - Artist a", state.title)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun play_usesKnownVideoIdFromTrack() {
        controller.play(listOf(track("a", videoId = "vidA")), 0)

        assertTrue(resolver.calls.any { it.first == "a" && it.second == "vidA" })
    }

    @Test
    fun next_movesWithinPreparedWindowWithoutResolving() {
        controller.play(listOf(track("a"), track("b"), track("c")), 0)
        val resolveCallsBefore = resolver.calls.size

        controller.next()

        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(1, state.currentIndex)
        // El salto dentro de la ventana no debe volver a resolver nada.
        assertEquals(resolveCallsBefore, resolver.calls.size)
    }

    @Test
    fun previous_beforeThresholdGoesBackOne() {
        controller.play(listOf(track("a"), track("b"), track("c")), 0)
        controller.next()
        assertEquals(1, player.currentMediaItemIndex)

        player.position = 0L
        controller.previous()

        assertEquals(0, player.currentMediaItemIndex)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun previous_firstTrackRestartsCurrent() {
        controller.play(listOf(track("a"), track("b")), 0)
        player.position = 500L

        controller.previous()

        assertEquals(0, player.currentMediaItemIndex)
        assertEquals(0L, player.position)
    }

    @Test
    fun cycleRepeat_goesOffOneAllAndTogglesEngineRepeatOne() {
        // Hace falta un reproductor vivo (se crea al reproducir) para que el
        // modo "repetir uno" llegue al motor.
        controller.play(listOf(track("a")), 0)

        assertEquals(RepeatMode.OFF, state.repeatMode)

        controller.cycleRepeat()
        assertEquals(RepeatMode.ONE, state.repeatMode)
        assertTrue(player.isRepeatOne)

        controller.cycleRepeat()
        assertEquals(RepeatMode.ALL, state.repeatMode)
        assertFalse(player.isRepeatOne)

        controller.cycleRepeat()
        assertEquals(RepeatMode.OFF, state.repeatMode)
    }

    @Test
    fun trackEnded_withoutRepeatStopsQueueAndClearsTitle() {
        controller.play(listOf(track("a"), track("b")), 0)
        controller.next()

        player.emitEnded()

        assertEquals(0, player.mediaItemCount)
        assertNull(state.title)
        assertFalse(state.isPlaying)
    }

    @Test
    fun trackEnded_withRepeatAllWrapsAround() {
        controller.setRepeatMode(RepeatMode.ALL)
        controller.play(listOf(track("a"), track("b")), 0)
        controller.next()

        player.emitEnded()

        assertEquals(0, player.currentMediaItemIndex)
        assertEquals(0, state.currentIndex)
    }

    @Test
    fun playerError_skipsToNextReResolving() {
        controller.play(listOf(track("a"), track("b")), 0)

        player.emitError()

        // La ventana se reconstruye anclada en la pista que falló + 1: el índice
        // de cola avanza y el reproductor carga esa pista como único item local.
        assertEquals(1, state.currentIndex)
        assertTrue(player.loadedItems.first().mediaId.startsWith("b"))
        // El reintento fuerza la re-resolución (B4): no reusa la URL que falló.
        assertTrue(resolver.calls.any { it.third })
    }

    @Test
    fun moveQueueItem_reordersAndFollowsCurrentTrack() {
        controller.play(listOf(track("a"), track("b"), track("c")), 0)

        controller.moveQueueItem(2, 0)

        assertEquals(listOf("c", "a", "b"), state.queue.map { it.id })
        assertEquals(1, state.currentIndex)
    }

    @Test
    fun unresolvedStartShowsErrorAndStopsLoading() {
        resolver.failFor = { true }

        controller.play(listOf(track("a")), 0)

        assertNotNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun seekTo_updatesPlayerAndState() {
        controller.play(listOf(track("a")), 0)

        controller.seekTo(1_234L)

        assertEquals(1_234L, player.position)
        assertEquals(1_234L, state.positionMs)
    }

    @Test
    fun togglePlayPause_pausesAndResumes() {
        controller.play(listOf(track("a")), 0)
        assertTrue(player.isPlaying)

        controller.togglePlayPause()
        assertFalse(player.isPlaying)

        controller.togglePlayPause()
        assertTrue(player.isPlaying)
    }

    @Test
    fun playEmptyQueueClearsTitle() {
        controller.play(emptyList())

        assertTrue(state.queue.isEmpty())
        assertNull(state.title)
        assertEquals(0, player.mediaItemCount)
    }

    @Test
    fun fromSearchResult_usesVideoIdAndFallsBackToIndex() {
        val withId = PlayerTrack.fromSearchResult(0, AudioItem(title = "T", url = "", videoId = "abcdefghijk", channel = "C"))
        assertEquals("abcdefghijk", withId.videoId)
        assertTrue(withId.id.contains("abcdefghijk"))

        val withoutId = PlayerTrack.fromSearchResult(3, AudioItem(title = "T", url = "", channel = "C"))
        assertNull(withoutId.videoId)
        assertTrue(withoutId.id.contains("3"))
    }

    /** Resolutor falso: nunca toca red; permite forzar fallos y registra llamadas. */
    private class FakeResolver : TrackSourceResolver {
        val calls = mutableListOf<Triple<String, String?, Boolean>>()
        var failFor: (PlayerTrack) -> Boolean = { false }

        override suspend fun resolve(
            track: PlayerTrack,
            knownVideoId: String?,
            forceRefresh: Boolean,
        ): ResolvedSource? {
            calls += Triple(track.id, knownVideoId, forceRefresh)
            if (failFor(track)) return null
            val videoId = knownVideoId ?: track.videoId ?: "vid_${track.id}"
            return ResolvedSource(videoId = videoId, uri = "https://audio/${track.id}")
        }
    }
}
