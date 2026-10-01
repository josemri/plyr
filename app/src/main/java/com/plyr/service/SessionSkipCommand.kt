package com.plyr.service

import androidx.media3.common.Player
import androidx.media3.session.SessionResult

/**
 * A qué sentido de salto corresponde un comando del reproductor, y qué hacer
 * con él.
 *
 * El bug (B49): los botones `<<` y `>>` de la notificación los decide la
 * `MediaSession`, que se los pasa al reproductor. Pero el reproductor solo
 * conoce su *ventana* de pistas, y esa ventana se recorta por delante para no
 * guardar la cola entera: al empezar a reproducir, lo anterior ya no está. Así
 * que su "anterior" era el anterior de la ventana, que no existe, y el botón no
 * hacía nada.
 *
 * Aquí se decide qué comandos se atienden en la app (donde vive la cola
 * completa) y cuáles se dejan al reproductor. Es lógica pura a propósito:
 * `MediaSession.Callback` no se puede instanciar en un test JVM, pero esta
 * decisión sí se testea sin Android.
 */
object SessionSkipCommand {

    /** Sentido del salto que pide el comando. */
    enum class Direction {
        BACKWARDS,
        FORWARDS,
    }

    /**
     * Sentido del salto de [playerCommand], o `null` si no es un salto y hay que
     * dejar que lo ejecute el reproductor.
     *
     * Ojo: este callback recibe **todos** los comandos, no solo los de salto.
     * Confundir "no es un salto" con "no lo atiendas" dejaría sin funcionar
     * reproduce, pausa y seek, que también pasan por aquí.
     */
    fun directionOf(playerCommand: Int): Direction? = when (playerCommand) {
        Player.COMMAND_SEEK_TO_PREVIOUS,
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> Direction.BACKWARDS

        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> Direction.FORWARDS

        else -> null
    }

    /**
     * Ejecuta [playerCommand] y devuelve el resultado que hay que darle al
     * controlador de la sesión.
     *
     * @param skipRequest quién decide el salto, o `null` si no hay nadie (la app
     *   no está conectada). Sin ella el salto no se puede hacer bien: el
     *   reproductor solo ve la ventana y saltaría a la canción equivocada, o no
     *   saltaría. Es preferible no hacer nada antes que hacer algo incorrecto.
     * @return `RESULT_SUCCESS` (0) para que lo ejecute el reproductor, o
     *   `RESULT_INFO_SKIPPED` para marcar el comando como ya atendido.
     */
    fun handle(
        playerCommand: Int,
        skipRequest: ((Direction) -> Unit)?,
    ): Int {
        val direction = directionOf(playerCommand) ?: return SessionResult.RESULT_SUCCESS
        val skip = skipRequest ?: return SessionResult.RESULT_SUCCESS
        skip(direction)
        // Un resultado distinto de 0 marca el comando como atendido para que el
        // reproductor no lo ejecute también: si no, el salto ocurriría dos veces.
        return SessionResult.RESULT_INFO_SKIPPED
    }
}
