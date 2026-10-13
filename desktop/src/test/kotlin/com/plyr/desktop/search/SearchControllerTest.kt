package com.plyr.desktop.search

import com.plyr.desktop.player.PlayerTrack
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
 * La búsqueda es el segundo parámetro del constructor y [SearchController] tiene
 * un `maxResults` con valor por defecto al final, así que hay que pasarla con
 * nombre (`search =`) y no como lambda de cola.
 */
class SearchControllerTest {

    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined)
    }

    private fun track(id: String) = PlayerTrack(id = id, title = "Track $id", artist = "Artist $id")

    @Test
    fun performSearch_publishesResultsAndStopsLoading() {
        val controller = SearchController(
            scope = scope,
            search = { query, _ -> listOf(track("$query-1"), track("$query-2")) },
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
    fun performSearch_withBlankQueryDoesNothing() {
        var called = false
        val controller = SearchController(
            scope = scope,
            search = { _, _ ->
                called = true
                emptyList()
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
                emptyList()
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
    }

    @Test
    fun clear_resetsEverything() {
        val controller = SearchController(
            scope = scope,
            search = { _, _ -> listOf(track("a")) },
        )

        controller.performSearch("algo")
        controller.clear()

        assertEquals(SearchState(), controller.state.value)
    }

    @Test
    fun performSearch_keepsLastResults() {
        val controller = SearchController(
            scope = scope,
            search = { query, _ -> listOf(track(query)) },
        )

        controller.performSearch("primera")
        controller.performSearch("segunda")

        assertEquals("segunda", controller.state.value.results.first().id)
    }
}
