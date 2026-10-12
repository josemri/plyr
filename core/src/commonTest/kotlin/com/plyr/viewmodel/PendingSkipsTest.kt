package com.plyr.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de [PendingSkips], la cola de saltos que llegan durante una transición.
 *
 * El bug (B59): la sesión ya le había devuelto `RESULT_INFO_SKIPPED` al
 * sistema —con lo que media3 no ejecuta el salto en el reproductor— y
 * `playIndex` lo descartaba con un `if (transitionInFlight) return` mudo, así
 * que no lo hacía nadie. Estos tests cubren la pieza nueva: que una petición
 * apuntada se conserva, se entrega en el orden en que llegó y no se pierde
 * salvo en el tope, que es a propósito.
 */
class PendingSkipsTest {

    private val forwards = PendingSkips.Direction.FORWARDS
    private val backwards = PendingSkips.Direction.BACKWARDS

    @Test
    fun recienCreada_noHayNadaPendiente() {
        val skips = PendingSkips()

        assertEquals(0, skips.size)
        assertNull(skips.poll())
    }

    @Test
    fun poll_entregaLasPeticionesEnElOrdenEnQueLlegaron() {
        val skips = PendingSkips()

        skips.request(forwards)
        skips.request(backwards)
        skips.request(forwards)

        assertEquals(forwards, skips.poll())
        assertEquals(backwards, skips.poll())
        assertEquals(forwards, skips.poll())
        assertNull(skips.poll())
    }

    @Test
    fun poll_sacaUnaPorLlamada_yElTamanoLaSigue() {
        val skips = PendingSkips()

        skips.request(forwards)
        skips.request(forwards)
        assertEquals(2, skips.size)

        skips.poll()
        assertEquals(1, skips.size)

        skips.poll()
        assertEquals(0, skips.size)
        assertNull(skips.poll())
    }

    /**
     * El tope es lo que impide que una racha de pulsaciones encadene
     * transiciones de red para siempre: lo que se ignora es lo que llega
     * **después** de llenar la cola, no lo que ya estaba.
     */
    @Test
    fun alSuperarElTope_lasPeticionesNuevasSeDescartan() {
        val skips = PendingSkips(limit = 2)

        skips.request(forwards)
        skips.request(backwards)
        skips.request(forwards) // tercera: no cabe

        assertEquals(2, skips.size)
        assertEquals(forwards, skips.poll())
        assertEquals(backwards, skips.poll())
        assertNull(skips.poll())
    }

    @Test
    fun elTopeCuentaLasQueQuedan_noLasAcumuladas() {
        val skips = PendingSkips(limit = 2)

        skips.request(forwards)
        skips.poll()
        skips.request(backwards)
        skips.request(forwards)

        assertEquals(2, skips.size)
        assertEquals(backwards, skips.poll())
        assertEquals(forwards, skips.poll())
    }

    @Test
    fun clear_olvidaTodoLoPendiente() {
        val skips = PendingSkips()

        skips.request(forwards)
        skips.request(backwards)

        skips.clear()

        assertEquals(0, skips.size)
        assertNull(skips.poll())
    }

    @Test
    fun clear_conLaColaVacia_esInocuo() {
        val skips = PendingSkips()

        skips.clear()

        assertNull(skips.poll())
    }

    @Test
    fun despuesDeVaciarla_vuelveAAceptarPeticiones() {
        val skips = PendingSkips()

        skips.request(forwards)
        skips.clear()
        skips.request(backwards)

        assertEquals(1, skips.size)
        assertEquals(backwards, skips.poll())
        assertNull(skips.poll())
    }
}
