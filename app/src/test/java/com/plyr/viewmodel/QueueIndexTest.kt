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

    // --- retryIndexFor (reintento cuando la canción no resuelve, B49) ---

    /**
     * Este es el bug de B49 en su forma más clara: al pedir `<<` a una pista que
     * no se podía resolver, el reintento caminaba hacia delante, así que el
     * usuario oía otra canción distinta, sin ninguna señal de por qué.
     */
    @Test
    fun retry_haciaAtras_noAvanzaHaciaDelante() {
        // El destino pedido era el 1 y falló; el 0 sí está detrás. Con el
        // reintento de antes habría saltado al 2.
        assertEquals(0, QueueIndex.retryIndexFor(failed = 1, size = 3, repeatMode = off, backwards = true))
    }

    @Test
    fun retry_haciaAdelante_sigueAvanzando() {
        assertEquals(2, QueueIndex.retryIndexFor(failed = 1, size = 3, repeatMode = off, backwards = false))
    }

    @Test
    fun retry_haciaAtras_enLaPrimera_devuelveNull() {
        assertNull(QueueIndex.retryIndexFor(failed = 0, size = 3, repeatMode = off, backwards = true))
    }

    @Test
    fun retry_haciaAtras_conRepeatAll_daLaVuelta() {
        assertEquals(2, QueueIndex.retryIndexFor(failed = 0, size = 3, repeatMode = all, backwards = true))
        assertEquals(1, QueueIndex.retryIndexFor(failed = 2, size = 3, repeatMode = all, backwards = true))
    }

    @Test
    fun retry_haciaAdelante_enLaUltima_devuelveNull() {
        assertNull(QueueIndex.retryIndexFor(failed = 2, size = 3, repeatMode = off, backwards = false))
    }

    /**
     * A diferencia de [QueueIndex.previousIndex], aquí no hay umbral de 3 s: es
     * un destino alternativo, no una pulsación de "anterior" que interpretar.
     */
    @Test
    fun retry_noAplicaElUmbralDeReinicio() {
        assertEquals(1, QueueIndex.retryIndexFor(failed = 2, size = 3, repeatMode = off, backwards = true))
    }

    /**
     * En la primera canción, y antes de los 3 s, no hay a dónde ir. Devolver
     * `null` es lo que hace que el botón quede sin hacer nada; la app lo
     * convierte en un reinicio (B49). Este test fija el contrato que hace esa
     * conversión posible: "no hay destino" y "el destino soy yo" son cosas
     * distintas, y solo la primera debe acabar reiniciando.
     */
    @Test
    fun previous_enLaPrimera_noHayDestino() {
        assertNull(QueueIndex.previousIndex(0, 3, positionMs = 0, repeatMode = off))
    }

    @Test
    fun previous_pasadosLos3s_devuelveElMismoParaReiniciar() {
        assertEquals(0, QueueIndex.previousIndex(0, 3, positionMs = 3_001, repeatMode = off))
    }

    @Test
    fun retry_conColaVaciaOIndiceInvalido_devuelveNull() {
        assertNull(QueueIndex.retryIndexFor(failed = 0, size = 0, repeatMode = all, backwards = true))
        assertNull(QueueIndex.retryIndexFor(failed = 3, size = 3, repeatMode = all, backwards = true))
        assertNull(QueueIndex.retryIndexFor(failed = -1, size = 3, repeatMode = off, backwards = false))
    }
}
