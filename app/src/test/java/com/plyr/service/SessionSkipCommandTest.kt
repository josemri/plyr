package com.plyr.service

import androidx.media3.common.Player
import androidx.media3.session.SessionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de [SessionSkipCommand], que decide qué botones de la notificación
 * atiende la app y cuáles el reproductor.
 *
 * El bug (B49): el `<<` de la notificación no hacía nada. La `MediaSession` se
 * lo pasaba al reproductor, que solo conoce su ventana de pistas — y esa ventana
 * se recorta por delante, así que al empezar a reproducir ya no había item
 * anterior. Aquí se comprueba que el salto lo decide la cola de la app.
 */
class SessionSkipCommandTest {

    private val backwards = SessionSkipCommand.Direction.BACKWARDS
    private val forwards = SessionSkipCommand.Direction.FORWARDS

    // --- Qué sentido corresponde a cada comando ---

    @Test
    fun anterior_esHaciaAtras() {
        assertEquals(backwards, SessionSkipCommand.directionOf(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertEquals(backwards, SessionSkipCommand.directionOf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
    }

    @Test
    fun siguiente_esHaciaAdelante() {
        assertEquals(forwards, SessionSkipCommand.directionOf(Player.COMMAND_SEEK_TO_NEXT))
        assertEquals(forwards, SessionSkipCommand.directionOf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
    }

    /**
     * El callback recibe **todos** los comandos del reproductor. Confundir "esto
     * no es un salto" con "no lo atiendas" dejaría reproduce, pausa y seek sin
     * funcionar, así que estos comandos tienen que pasar de largo.
     */
    @Test
    fun losDemasComandos_noSonSaltos() {
        assertNull(SessionSkipCommand.directionOf(Player.COMMAND_PLAY_PAUSE))
        assertNull(SessionSkipCommand.directionOf(Player.COMMAND_STOP))
        assertNull(SessionSkipCommand.directionOf(Player.COMMAND_SEEK_TO_DEFAULT_POSITION))
        assertNull(SessionSkipCommand.directionOf(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM))
        assertNull(SessionSkipCommand.directionOf(Player.COMMAND_SET_SPEED_AND_PITCH))
    }

    // --- Qué se devuelve al controlador de la sesión ---

    @Test
    fun elSaltoLoAtiendeLaApp() {
        var received: SessionSkipCommand.Direction? = null

        val result = SessionSkipCommand.handle(Player.COMMAND_SEEK_TO_PREVIOUS) {
            received = it
        }

        assertEquals(backwards, received)
        // Marcado como atendido: si no, el reproductor lo ejecutaría también y
        // el salto ocurriría dos veces.
        assertEquals(SessionResult.RESULT_INFO_SKIPPED, result)
    }

    @Test
    fun elSaltoAdelanteTambienLoAtiendeLaApp() {
        var received: SessionSkipCommand.Direction? = null

        val result = SessionSkipCommand.handle(Player.COMMAND_SEEK_TO_NEXT) {
            received = it
        }

        assertEquals(forwards, received)
        assertEquals(SessionResult.RESULT_INFO_SKIPPED, result)
    }

    /**
     * La parte delicada: si el comando no es de salto, se devuelve 0 para que lo
     * ejecute el reproductor y **no** se invoca el callback. Sin esto, poner
     * reproducir o pausar desde la notificación dejaría de funcionar.
     */
    @Test
    fun reproduceYPausaLosDejaAlReproductor() {
        var called = false
        val skip = { _: SessionSkipCommand.Direction -> called = true }

        assertEquals(
            SessionResult.RESULT_SUCCESS,
            SessionSkipCommand.handle(Player.COMMAND_PLAY_PAUSE, skip),
        )
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            SessionSkipCommand.handle(Player.COMMAND_STOP, skip),
        )
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            SessionSkipCommand.handle(Player.COMMAND_SEEK_TO_DEFAULT_POSITION, skip),
        )

        assertEquals(false, called)
    }

    /**
     * Sin app conectada no hay quien decida el salto. Delegarlo en el reproductor
     * saltaría a la canción equivocada (el anterior de la ventana, no de la
     * cola), así que es preferible no hacer nada.
     */
    @Test
    fun sinAppConectada_noSeAtiendeElSalto() {
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            SessionSkipCommand.handle(Player.COMMAND_SEEK_TO_PREVIOUS, null),
        )
        assertEquals(
            SessionResult.RESULT_SUCCESS,
            SessionSkipCommand.handle(Player.COMMAND_SEEK_TO_NEXT, null),
        )
    }

    /** Los tres casos de un salto tienen que acabar en el mismo sitio. */
    @Test
    fun losCuatroComandosDeSalto_conAppConectada_seAtienden() {
        val commands = listOf(
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        )

        commands.forEach { command ->
            var called = false
            val result = SessionSkipCommand.handle(command) { called = true }

            assertEquals("comando $command no atendido", SessionResult.RESULT_INFO_SKIPPED, result)
            assertEquals("comando $command no llegó a la app", true, called)
        }
    }
}
