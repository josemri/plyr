package com.plyr.desktop.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.database.PlaylistRecord
import com.plyr.database.PlaylistSource
import com.plyr.desktop.player.PlayerState
import com.plyr.desktop.player.PlayerTrack
import com.plyr.desktop.ui.components.TrackMenuActions
import com.plyr.desktop.ui.theme.PlyrTheme
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests de UI de la pantalla de cola (M7): listado, marcado de la pista actual,
 * salto al pulsar y reordenación con `^`/`v`.
 */
@OptIn(ExperimentalTestApi::class)
class QueueScreenTest {

    private fun track(id: String) = PlayerTrack(id = id, title = "Track $id", artist = "Artist $id")

    private fun state() = PlayerState(
        queue = listOf(track("a"), track("b"), track("c")),
        currentIndex = 0,
        title = "Track a - Artist a",
    )

    @Test
    fun listaLaColaYAlPulsarSalta() = runComposeUiTest {
        var jumped: Int? = null
        setContent {
            PlyrTheme {
                QueueScreen(state = state(), onJump = { jumped = it }, onMove = { _, _ -> }, onBack = {})
            }
        }

        onNodeWithText("1. Track a").assertIsDisplayed()
        onNodeWithText("3. Track c").assertIsDisplayed()

        onNodeWithTag("queue_item_2").performClick()
        assertEquals(2, jumped)
    }

    @Test
    fun losBotonesReordenan() = runComposeUiTest {
        val moves = mutableListOf<Pair<Int, Int>>()
        setContent {
            PlyrTheme {
                QueueScreen(state = state(), onJump = {}, onMove = { from, to -> moves += from to to }, onBack = {})
            }
        }

        onNodeWithTag("queue_down_0").performClick()
        onNodeWithTag("queue_up_2").performClick()

        assertEquals(listOf(0 to 1, 2 to 1), moves)
    }

    @Test
    fun colaVaciaMuestraMensaje() = runComposeUiTest {
        setContent {
            PlyrTheme {
                QueueScreen(state = PlayerState(), onJump = {}, onMove = { _, _ -> }, onBack = {})
            }
        }

        onNodeWithTag("queue_empty").assertIsDisplayed()
    }

    @Test
    fun elMenuOfreceLasAccionesPorPista() = runComposeUiTest {
        val liked = mutableListOf<PlayerTrack>()
        var requestedAdd: PlayerTrack? = null
        val playlistId = "p1"

        setContent {
            PlyrTheme {
                QueueScreen(
                    state = state(),
                    onJump = {},
                    onMove = { _, _ -> },
                    onBack = {},
                    playlists = listOf(
                        PlaylistRecord(
                            remoteId = playlistId,
                            name = "Lista",
                            description = null,
                            trackCount = 0,
                            imageUrl = null,
                            lastSyncTime = 0L,
                            source = PlaylistSource.LOCAL,
                            sourceId = null,
                        )
                    ),
                    onAddToPlaylist = { track, _ -> requestedAdd = track },
                    menuActions = TrackMenuActions(
                        isLiked = { false },
                        onToggleLike = { liked += it },
                        onAddToQueue = {},
                        onShare = {},
                    ),
                )
            }
        }

        onNodeWithTag("queue_menu_1").performClick()
        onNodeWithTag("track_like", useUnmergedTree = true).performClick()

        assertEquals(listOf(track("b")), liked)

        // "Añadir a lista" abre el selector; la acción se dispara al elegir lista.
        onNodeWithTag("queue_menu_1").performClick()
        onNodeWithTag("track_add_playlist", useUnmergedTree = true).performClick()
        onNodeWithTag("add_to_$playlistId", useUnmergedTree = true).performClick()

        assertEquals(track("b"), requestedAdd)
    }

    @Test
    fun elBotonVolverSigueVisibleConUnaColaLarga() = runComposeUiTest {
        // M36: el `LazyColumn` de la cola se llevaba todo el alto que quedaba y
        // empujaba el `←` fuera de la ventana; solo se veía con la cola vacía.
        var volvido = false
        val larga = PlayerState(
            queue = (1..80).map { track("t$it") },
            currentIndex = 0,
            title = "Track t1 - Artist t1",
        )

        setContent {
            PlyrTheme {
                QueueScreen(state = larga, onJump = {}, onMove = { _, _ -> }, onBack = { volvido = true })
            }
        }

        onNodeWithTag("back_from_QUEUE").assertIsDisplayed()
        onNodeWithTag("back_from_QUEUE").performClick()

        assertEquals(true, volvido)
    }
}
