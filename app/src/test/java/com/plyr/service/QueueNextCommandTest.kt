package com.plyr.service

import androidx.media3.common.Player
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de [QueueNextCommand], que decide si el `>>` existe.
 *
 * El bug (B57): el `>>` de los controles de reproducción de Android
 * desaparecía tras dos saltos seguidos. La sesión monta el `PlaybackState`
 * cruzando los comandos del reproductor con los que ella permite, y el
 * reproductor solo añade `COMMAND_SEEK_TO_NEXT` si `hasNextMediaItem()` es
 * cierto: es decir, si el **trozo de cola que tiene cargado** tiene un item
 * después del actual. Ese trozo se recorta por delante y a veces no llega a
 * rellenarse, así que con canciones aún por delante en la cola el botón se
 * escondía.
 *
 * Aquí la fuente de verdad es la cola entera. Se comprueba solo la decisión
 * (pura) porque el resto es `Player.Commands`, que no se puede construir en un
 * test JVM.
 */
class QueueNextCommandTest {

    private val seekToNext = Player.COMMAND_SEEK_TO_NEXT
    private val seekToNextItem = Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM

    // --- Qué añade cuando la cola tiene siguiente ---

    @Test
    fun conSiguiente_enLaCola_anadeLosDosComandosDeSalto() {
        assertArrayEquals(
            intArrayOf(seekToNext, seekToNextItem),
            QueueNextCommand.extra(true),
        )
    }

    @Test
    fun sinSiguiente_enLaCola_noAnadeNada() {
        assertArrayEquals(IntArray(0), QueueNextCommand.extra(false))
    }

    /**
     * El parámetro es lo que `hasNextInQueue()` devuelve. Con él, cualquiera de
     * los dos comandos tiene que estar disponible: `MediaSessionLegacyStub`
     * comprueba uno y si no el otro, y los dos acaban en `ACTION_SKIP_TO_NEXT`,
     * que es lo que el sistema mira para pintar el slot.
     */
    @Test
    fun conSiguiente_ambosComandosEstanCubiertos() {
        assertTrue(QueueNextCommand.covers(seekToNext, true))
        assertTrue(QueueNextCommand.covers(seekToNextItem, true))
    }

    @Test
    fun sinSiguiente_ningunComandoEstaCubierto() {
        assertFalse(QueueNextCommand.covers(seekToNext, false))
        assertFalse(QueueNextCommand.covers(seekToNextItem, false))
    }

    /**
     * El callback del reproductor recibe **todos** los comandos. Confundir
     * "esto no es un salto siguiente" con "lo cubre la cola" dejaría reproducir,
     * pausar y el `<<` resueltos por la cola en lugar de por el reproductor.
     */
    @Test
    fun losDemasComandos_nuncaEstanCubiertos() {
        assertFalse(QueueNextCommand.covers(Player.COMMAND_SEEK_TO_PREVIOUS, true))
        assertFalse(QueueNextCommand.covers(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, true))
        assertFalse(QueueNextCommand.covers(Player.COMMAND_PLAY_PAUSE, true))
        assertFalse(QueueNextCommand.covers(Player.COMMAND_STOP, true))
        assertFalse(QueueNextCommand.covers(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, true))
    }

    /**
     * `extra` devuelve una copia: el llamante se queda con el array para meterlo
     * en el builder, y mutarlo rompería los cálculos siguientes.
     */
    @Test
    fun extra_devuelveUnaCopiaNoElMismoArray() {
        val extra = QueueNextCommand.extra(true)
        extra[0] = 0
        assertArrayEquals(
            intArrayOf(seekToNext, seekToNextItem),
            QueueNextCommand.extra(true),
        )
    }
}
