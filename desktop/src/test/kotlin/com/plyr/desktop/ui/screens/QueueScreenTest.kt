package com.plyr.desktop.ui.screens

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.plyr.desktop.player.PlayerState
import com.plyr.desktop.player.PlayerTrack
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
}
