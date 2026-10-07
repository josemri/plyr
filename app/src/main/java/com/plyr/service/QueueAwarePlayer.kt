package com.plyr.service

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Qué decide que el `>>` existe: si la cola tiene canción siguiente.
 *
 * El botón lo pinta el sistema a partir del `PlaybackState` de la `MediaSession`,
 * y esa sesión monta sus comandos cruzando los del reproductor con los que ella
 * misma permite. En el reproductor, `Util.getAvailableCommands` añade
 * `COMMAND_SEEK_TO_NEXT` solo si `hasNextMediaItem()` es cierto, así que el `>>`
 * existía si y solo si **la ventana de ExoPlayer** tenía un item después del
 * actual.
 *
 * Esa ventana es solo un trozo de la cola: se recorta por delante y a veces ni
 * llega a rellenarse, con lo que el `>>` se escondía en cuanto la canción actual
 * quedaba al final de la ventana, aunque quedaran canciones por delante en la
 * cola (B57). Aquí se responde con la cola, que es la fuente de verdad.
 *
 * Lógica pura a propósito, como `SessionSkipCommand`: se testea sin Android ni
 * media3.
 */
object QueueNextCommand {

    /**
     * `COMMAND_SEEK_TO_NEXT` y `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM` se traducen los
     * dos a `ACTION_SKIP_TO_NEXT`, que es lo que el sistema mira para pintar el
     * slot *Next*.
     */
    private val COMMANDS = intArrayOf(
        Player.COMMAND_SEEK_TO_NEXT,
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
    )

    /** `command` está disponible porque la cola tiene canción siguiente. */
    fun covers(command: Int, hasNextInQueue: Boolean): Boolean =
        hasNextInQueue && command in COMMANDS

    /**
     * Comandos que hay que sumar a los del reproductor, o vacío si la cola no
     * tiene siguiente.
     *
     * Solo se añaden, nunca se quitan: la `MediaSession` hace un `intersect` con
     * lo que declara el reproductor, de modo que lo único que puede ocurrir es
     * que falte algo que la cola sí permite.
     */
    fun extra(hasNextInQueue: Boolean): IntArray =
        if (hasNextInQueue) COMMANDS.copyOf() else IntArray(0)
}

/**
 * Reproductor que decide el `>>` con la cola completa en vez de con la ventana.
 *
 * Ver [QueueNextCommand] para el porqué. Al estar el comando disponible,
 * `MediaSessionLegacyStub.onSkipToNext` lo despacha como `COMMAND_SEEK_TO_NEXT`
 * y la sesión lo intercepta en `SessionSkipCommand`, que lo resuelve sobre la
 * cola entera: el reproductor nunca llega a ejecutar `seekToNext()`, así que el
 * salto sigue siendo único y lo decide la cola, no la ventana.
 *
 * `ForwardingPlayer.isCommandAvailable` delega en el reproductor de abajo sin
 * pasar por [getAvailableCommands], así que hay que sobrescribir los dos.
 */
@OptIn(UnstableApi::class)
class QueueAwarePlayer(
    delegate: Player,
    private val hasNextInQueue: () -> Boolean,
) : ForwardingPlayer(delegate) {

    override fun getAvailableCommands(): Player.Commands {
        val playerCommands = super.getAvailableCommands()
        if (!hasNextInQueue()) return playerCommands

        val builder = playerCommands.buildUpon()
        for (command in QueueNextCommand.extra(true)) builder.add(command)
        return builder.build()
    }

    override fun isCommandAvailable(command: Int): Boolean =
        QueueNextCommand.covers(command, hasNextInQueue()) || super.isCommandAvailable(command)
}
