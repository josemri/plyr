package com.plyr.desktop.playlists

import com.plyr.desktop.player.PlayerTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests de [TrackSearchController] (M22) sin red ni Compose.
 *
 * La búsqueda se inyecta como lambda, así que se comprueba la lógica de estado
 * (resultados, error, selección sin duplicar) con `Dispatchers.Unconfined`.
 */
class TrackSearchControllerTest {

    private lateinit var scope: CoroutineScope
    private lateinit var controller: TrackSearchController

    @Before
    fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined)
        controller = TrackSearchController(scope) { query ->
            if (query == "boom") throw IllegalStateException("sin red") else listOf(track("a"), track("b"))
        }
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun track(id: String) = PlayerTrack(id = id, title = "T $id", artist = "A", videoId = "vid_$id")

    @Test
    fun performSearch_publicaLosResultados() {
        controller.onQueryChange("  hola  ")
        controller.performSearch()

        assertEquals(listOf("vid_a", "vid_b"), controller.state.value.results.map { it.videoId })
        assertFalse(controller.state.value.isSearching)
        assertNull(controller.state.value.error)
    }

    @Test
    fun performSearch_conConsultaVaciaNoHaceNada() {
        controller.onQueryChange("   ")
        controller.performSearch()

        assertTrue(controller.state.value.results.isEmpty())
        assertFalse(controller.state.value.isSearching)
    }

    @Test
    fun performSearch_conErrorLoGuardaYDejaDeCargar() {
        controller.onQueryChange("boom")
        controller.performSearch()

        assertEquals("sin red", controller.state.value.error)
        assertTrue(controller.state.value.results.isEmpty())
        assertFalse(controller.state.value.isSearching)
    }

    @Test
    fun toggleSelected_anadeYQuitaSinDuplicar() {
        val a = track("a")
        controller.toggleSelected(a)
        controller.toggleSelected(track("b"))
        controller.toggleSelected(a)

        assertEquals(listOf("vid_b"), controller.state.value.selected.map { it.videoId })
        assertFalse(controller.state.value.isSelected(a))

        controller.toggleSelected(a)
        assertTrue(controller.state.value.isSelected(a))
    }

    @Test
    fun toggleSelected_mismaCancionConOtroIdNoDuplica() {
        controller.toggleSelected(PlayerTrack(id = "otro", title = "T a", artist = "A", videoId = "vid_a"))
        controller.toggleSelected(track("a"))

        assertTrue(controller.state.value.selected.isEmpty())
    }

    @Test
    fun clear_vaciaBusquedaPeroConservaLaSeleccion() {
        controller.onQueryChange("hola")
        controller.performSearch()
        controller.toggleSelected(track("a"))

        controller.clear()

        assertEquals("", controller.state.value.query)
        assertTrue(controller.state.value.results.isEmpty())
        assertNull(controller.state.value.error)
        assertEquals(listOf("vid_a"), controller.state.value.selected.map { it.videoId })
    }
}
