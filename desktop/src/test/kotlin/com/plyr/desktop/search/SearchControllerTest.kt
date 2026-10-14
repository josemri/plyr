package com.plyr.desktop.search

import com.plyr.desktop.player.PlayerTrack
import com.plyr.network.YouTubePlaylistInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de [SearchController] con la búsqueda inyectada, sin red.
 *
 * La búsqueda es el segundo parámetro del constructor, así que se pasa con
 * nombre (`search =`) y no como lambda de cola. Desde M21 recibe también la
 * página a pedir, de modo que estos tests controlan qué devuelve cada página.
 */
class SearchControllerTest {

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined)
    }

    private fun track(id: String) = PlayerTrack(id = id, title = "Track $id", artist = "Artist $id")

    private fun playlist(id: String) = YouTubePlaylistInfo(
        playlistId = id,
        title = "Playlist $id",
        uploader = "Uploader",
        videoCount = 3,
        thumbnailUrl = null,
    )

    @Test
    fun performSearch_publishesResultsAndStopsLoading() {
        val controller = SearchController(
            scope = scope,
            search = { query, _ -> SearchResults(videos = listOf(track("$query-1"), track("$query-2"))) },
        )

        controller.onQueryChange("daft punk")
        controller.performSearch()

        val state = controller.state.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(2, state.results.size)
        assertEquals("daft punk", state.query)
    }

    @Test
    fun performSearch_publishesPlaylistsToo() {
        val controller = SearchController(
            scope = scope,
            search = { _, _ ->
                SearchResults(videos = listOf(track("v")), playlists = listOf(playlist("PL1")))
            },
        )

        controller.performSearch("mix")

        val state = controller.state.value
        assertEquals(1, state.results.size)
        assertEquals(listOf("PL1"), state.playlists.map { it.playlistId })
        assertTrue(state.hasResults)
        assertFalse(state.isEmptyResult)
    }

    @Test
    fun toggles_flipEachSectionIndependently() {
        val controller = SearchController(scope = scope, search = { _, _ -> SearchResults() })

        assertTrue(controller.state.value.videosExpanded)
        assertTrue(controller.state.value.playlistsExpanded)

        controller.toggleVideos()
        assertFalse(controller.state.value.videosExpanded)
        assertTrue(controller.state.value.playlistsExpanded)

        controller.togglePlaylists()
        assertFalse(controller.state.value.playlistsExpanded)
    }

    @Test
    fun performSearch_withBlankQueryDoesNothing() {
        var called = false
        val controller = SearchController(
            scope = scope,
            search = { _, _ ->
                called = true
                SearchResults()
            },
        )

        controller.onQueryChange("   ")
        controller.performSearch()

        assertFalse(called)
        assertFalse(controller.state.value.isLoading)
    }

    @Test
    fun performSearch_trimsQuery() {
        var received: String? = null
        val controller = SearchController(
            scope = scope,
            search = { query, _ ->
                received = query
                SearchResults()
            },
        )

        controller.performSearch("  nina simone  ")

        assertEquals("nina simone", received)
        assertEquals("nina simone", controller.state.value.query)
        assertTrue(controller.state.value.isEmptyResult)
    }

    @Test
    fun performSearch_errorIsPublishedAndLoadingStops() {
        val controller = SearchController(
            scope = scope,
            search = { _, _ -> error("red caída") },
        )

        controller.performSearch("falla")

        val state = controller.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertTrue(state.results.isEmpty())
        assertTrue(state.playlists.isEmpty())
    }

    @Test
    fun clear_resetsEverything() {
        val controller = SearchController(
            scope = scope,
            search = { _, _ -> SearchResults(videos = listOf(track("a"))) },
        )

        controller.performSearch("algo")
        controller.clear()

        assertEquals(SearchState(), controller.state.value)
    }

    @Test
    fun performSearch_keepsLastResults() {
        val controller = SearchController(
            scope = scope,
            search = { query, _ -> SearchResults(videos = listOf(track(query))) },
        )

        controller.performSearch("primera")
        controller.performSearch("segunda")

        assertEquals("segunda", controller.state.value.results.first().id)
    }

    // --- M21: paginación («cargar más») ---

    @Test
    fun canLoadMore_needsTheAndroidThreshold() {
        val controller = SearchController(
            scope = scope,
            search = { _, _ -> SearchResults(videos = (1..9).map { track("t$it") }, hasMore = true) },
        )

        controller.performSearch("mix")

        // 9 vídeos con página detrás: por debajo del umbral (>=10) no se ofrece.
        assertFalse(controller.state.value.canLoadMore)
    }

    @Test
    fun canLoadMore_isOfferedAtTheThresholdWithMorePages() {
        val controller = SearchController(
            scope = scope,
            search = { _, _ -> SearchResults(videos = (1..10).map { track("t$it") }, hasMore = true) },
        )

        controller.performSearch("mix")

        assertTrue(controller.state.value.canLoadMore)
    }

    @Test
    fun loadMore_appendsTheNextPageAndStopsWhenExhausted() {
        val pages = mutableListOf<Int>()
        val controller = SearchController(
            scope = scope,
            search = { _, page ->
                pages += page
                if (page == 1) {
                    SearchResults(videos = (1..12).map { track("p1-$it") }, hasMore = true)
                } else {
                    SearchResults(videos = (1..3).map { track("p2-$it") }, hasMore = false)
                }
            },
        )

        controller.performSearch("mix")
        assertTrue(controller.state.value.canLoadMore)

        controller.loadMore()

        val state = controller.state.value
        assertEquals(listOf(1, 2), pages)
        assertEquals(15, state.results.size)
        assertFalse(state.canLoadMore)
        assertFalse(state.isLoadingMore)
        assertNull(state.error)
    }

    @Test
    fun loadMore_doesNothingWhenThereIsNoNextPage() {
        var calls = 0
        val controller = SearchController(
            scope = scope,
            search = { _, _ ->
                calls++
                SearchResults(videos = (1..12).map { track("t$it") }, hasMore = false)
            },
        )

        controller.performSearch("mix")
        assertFalse(controller.state.value.canLoadMore)

        controller.loadMore()

        assertEquals(1, calls)
        assertEquals(12, controller.state.value.results.size)
    }

    @Test
    fun loadMore_errorKeepsAlreadyLoadedResults() {
        val controller = SearchController(
            scope = scope,
            search = { _, page ->
                if (page == 1) {
                    SearchResults(videos = (1..10).map { track("t$it") }, hasMore = true)
                } else {
                    error("página caída")
                }
            },
        )

        controller.performSearch("mix")
        controller.loadMore()

        val state = controller.state.value
        assertNotNull(state.error)
        assertFalse(state.isLoadingMore)
        assertEquals(10, state.results.size)
    }

    @Test
    fun performSearch_resetsToTheFirstPage() {
        val pages = mutableListOf<Int>()
        val controller = SearchController(
            scope = scope,
            search = { _, page ->
                pages += page
                SearchResults(videos = (1..12).map { track("t$it") }, hasMore = true)
            },
        )

        controller.performSearch("primera")
        controller.loadMore()
        controller.performSearch("segunda")

        assertEquals(listOf(1, 2, 1), pages)
    }
}
