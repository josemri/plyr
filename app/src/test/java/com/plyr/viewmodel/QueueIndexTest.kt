package com.plyr.viewmodel

import com.plyr.utils.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests de la navegación de la cola ([QueueIndex]).
 *
 * Cubre los casos que provocaban que la reproducción se detuviera al
 * terminar una canción: final de lista, índices fuera de rango y modos de
 * repetición mal interpretados.
 */
class QueueIndexTest {

    private val off = Config.REPEAT_MODE_OFF
    private val one = Config.REPEAT_MODE_ONE
    private val all = Config.REPEAT_MODE_ALL

    // --- nextIndex (botón "siguiente") ---

    @Test
    fun next_avanzaDentroDeLaCola() {
        assertEquals(1, QueueIndex.nextIndex(0, 3, off))
        assertEquals(2, QueueIndex.nextIndex(1, 3, off))
    }

    @Test
    fun next_enUltimaPosicion_devuelveNull() {
        assertNull(QueueIndex.nextIndex(2, 3, off))
    }

    @Test
    fun next_conRepeatAll_daLaVuelta() {
        assertEquals(0, QueueIndex.nextIndex(2, 3, all))
    }

    @Test
    fun next_conRepeatOne_avanzaIgual() {
        assertEquals(1, QueueIndex.nextIndex(0, 3, one))
        assertEquals(2, QueueIndex.nextIndex(1, 3, one))
    }

    @Test
    fun next_conColaVacia_devuelveNull() {
        assertNull(QueueIndex.nextIndex(0, 0, off))
        assertNull(QueueIndex.nextIndex(0, 0, all))
    }

    @Test
    fun next_conIndiceFueraDeRango_devuelveNull() {
        assertNull(QueueIndex.nextIndex(-1, 3, all))
        assertNull(QueueIndex.nextIndex(5, 3, all))
    }

    // --- previousIndex (botón "anterior") ---

    @Test
    fun previous_siLaCancionNoHaEmpezado_retrocede() {
        assertEquals(0, QueueIndex.previousIndex(1, 3, 0L, off))
        assertEquals(1, QueueIndex.previousIndex(2, 3, 2000L, off))
    }

    @Test
    fun previous_siLaCancionHaEmpezado_reinicia() {
        assertEquals(1, QueueIndex.previousIndex(1, 3, 5000L, off))
    }

    @Test
    fun previous_enPrimeraPosicion_devuelveNull() {
        assertNull(QueueIndex.previousIndex(0, 3, 0L, off))
    }

    @Test
    fun previous_enPrimeraPosicionYHaEmpezado_reiniciaLaActual() {
        assertEquals(0, QueueIndex.previousIndex(0, 3, 9000L, off))
    }

    @Test
    fun previous_conRepeatAll_daLaVueltaAlFinal() {
        assertEquals(2, QueueIndex.previousIndex(0, 3, 0L, all))
    }

    @Test
    fun previous_conColaVacia_devuelveNull() {
        assertNull(QueueIndex.previousIndex(0, 0, 0L, all))
    }

    // --- onTrackEnded (transición automática, la que fallaba) ---

    @Test
    fun ended_avanzaALaSiguiente() {
        assertEquals(1, QueueIndex.onTrackEnded(0, 3, off))
        assertEquals(2, QueueIndex.onTrackEnded(1, 3, off))
    }

    @Test
    fun ended_enUltimaCancion_sinRepeticion_para() {
        assertNull(QueueIndex.onTrackEnded(2, 3, off))
    }

    @Test
    fun ended_enUltimaCancion_conRepeatAll_vuelveAlPrincipio() {
        assertEquals(0, QueueIndex.onTrackEnded(2, 3, all))
    }

    @Test
    fun ended_conRepeatOne_repiteLaMisma() {
        assertEquals(0, QueueIndex.onTrackEnded(0, 3, one))
        assertEquals(2, QueueIndex.onTrackEnded(2, 3, one))
    }

    @Test
    fun ended_conColaVacia_devuelveNull() {
        assertNull(QueueIndex.onTrackEnded(0, 0, off))
        assertNull(QueueIndex.onTrackEnded(0, 0, one))
        assertNull(QueueIndex.onTrackEnded(0, 0, all))
    }

    @Test
    fun ended_conIndiceFueraDeRango_devuelveNull() {
        assertNull(QueueIndex.onTrackEnded(3, 3, all))
    }
}
