package com.plyr.viewmodel

import com.plyr.domain.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de la lógica pura de la cola ([QueueEngine]).
 *
 * Cubre la navegación (delegada en [QueueIndex]), el anclaje y la generación de
 * la ventana, y el cálculo de qué tramo rellenar y cuánto recortar, que son las
 * decisiones que antes vivían sueltas en `PlayerViewModel`.
 */
class QueueEngineTest {

    private fun engine(size: Int, startIndex: Int = 0): QueueEngine<Int> =
        QueueEngine<Int>().apply { setQueue((0 until size).toList(), startIndex) }

    // --- setQueue / cola ---

    @Test
    fun setQueue_vacia_dejaIndiceInvalidoYVentanaReiniciada() {
        val e = engine(3, startIndex = 1)
        e.setQueue(emptyList())

        assertEquals(-1, e.currentIndex)
        assertTrue(e.isEmpty)
        assertEquals(0, e.windowStart)
        assertNull(e.currentTrack)
    }

    @Test
    fun setQueue_anclaLaVentanaAlInicio() {
        val e = engine(5, startIndex = 3)

        assertEquals(3, e.currentIndex)
        assertEquals(3, e.windowStart)
        assertEquals(3, e.currentTrack)
        assertEquals(5, e.size)
    }

    @Test
    fun setQueue_startIndexFueraDeRango_seAcota() {
        assertEquals(0, engine(3, startIndex = -5).currentIndex)
        assertEquals(2, engine(3, startIndex = 99).currentIndex)
    }

    @Test
    fun appendTrack_anadeAlFinal() {
        val e = engine(2)
        e.appendTrack(2)

        assertEquals(3, e.size)
        assertEquals(2, e.trackAt(2))
    }

    @Test
    fun replaceQueue_noTocaElIndice() {
        val e = engine(2)
        e.moveTo(1)
        e.replaceQueue(listOf(7, 8, 9))

        assertEquals(1, e.currentIndex)
        assertEquals(8, e.currentTrack)
    }

    // --- Navegación ---

    @Test
    fun nextTarget_avanzaYDaLavueltaConRepeatAll() {
        assertEquals(1, engine(3).nextTarget())

        val all = engine(3, startIndex = 2).apply { setRepeatMode(RepeatMode.ALL) }
        assertEquals(0, all.nextTarget())
    }

    @Test
    fun nextTarget_enLaUltimaSinRepeat_devuelveNull() {
        assertNull(engine(3, startIndex = 2).nextTarget())
    }

    @Test
    fun hasNext_dependeDelModoYLaPosicion() {
        assertTrue(engine(3).hasNext)
        assertFalse(engine(3, startIndex = 2).hasNext)

        val all = engine(3, startIndex = 2).apply { setRepeatMode(RepeatMode.ALL) }
        assertTrue(all.hasNext)
    }

    @Test
    fun previousTarget_reiniciaODaLaVuelta() {
        // En la primera y antes del umbral, no hay anterior.
        assertNull(engine(3, startIndex = 0).previousTarget(positionMs = 1_000))

        // En la última y antes del umbral, retrocede.
        assertEquals(1, engine(3, startIndex = 2).previousTarget(positionMs = 1_000))

        // Pasado el umbral, reinicia la actual (devuelve el mismo índice).
        assertEquals(2, engine(3, startIndex = 2).previousTarget(positionMs = 10_000))
    }

    @Test
    fun onTrackEndedTarget_avanzaODaLaVuelta() {
        assertEquals(1, engine(3).onTrackEndedTarget())
        assertNull(engine(3, startIndex = 2).onTrackEndedTarget())

        val all = engine(3, startIndex = 2).apply { setRepeatMode(RepeatMode.ALL) }
        assertEquals(0, all.onTrackEndedTarget())
    }

    @Test
    fun retryTarget_respetaElSentidoDelSalto() {
        // Hacia delante desde la 0 avanza; hacia atrás desde la 1 retrocede.
        assertEquals(1, engine(3, startIndex = 0).retryTarget(candidate = 0, backwards = false))
        assertEquals(0, engine(3, startIndex = 1).retryTarget(candidate = 1, backwards = true))

        // Hacia atrás en la primera no hay destino.
        assertNull(engine(3, startIndex = 0).retryTarget(candidate = 0, backwards = true))
    }

    // --- Ventana ---

    @Test
    fun fillTarget_pideLoQueFaltaHastaWindowAhead() {
        val e = engine(5, startIndex = 0)

        // La ventana solo cubre el actual: falta rellenar [1, 2].
        assertEquals(QueueEngine.FillTarget(1, 2), e.fillTarget(mediaItemCount = 1))
    }

    @Test
    fun fillTarget_ventanaYaCompleta_devuelveNull() {
        val e = engine(5, startIndex = 0)

        // Cubre [0, 1, 2]: ya es actual + windowAhead.
        assertNull(e.fillTarget(mediaItemCount = 3))
    }

    @Test
    fun fillTarget_noSePasaDelFinalDeLaCola() {
        val e = engine(3, startIndex = 0)

        // Ventana [0..0], cola de 3: el objetivo es min(0+2, 2) = 2.
        assertEquals(QueueEngine.FillTarget(1, 2), e.fillTarget(mediaItemCount = 1))
    }

    @Test
    fun fillTarget_colaVaciaOIndiceInvalido_devuelveNull() {
        val e = QueueEngine<Int>()
        assertNull(e.fillTarget(mediaItemCount = 0))
    }

    @Test
    fun trimCount_dejaKeepBehindDetrasDelActual() {
        val e = engine(5, startIndex = 3)
        assertEquals(2, e.trimCount(currentMediaItemIndex = 3))
        assertEquals(0, e.trimCount(currentMediaItemIndex = 1))
    }

    @Test
    fun anchorYShift_muevenElInicioDeLaVentana() {
        val e = engine(5, startIndex = 2)
        e.shiftWindow(1)
        assertEquals(3, e.windowStart)

        e.anchorWindowAt(4)
        assertEquals(4, e.windowStart)
    }

    @Test
    fun invalidateWindow_incrementaLaGeneracionYReiniciaElInicio() {
        val e = engine(5, startIndex = 3)
        val gen = e.generation

        e.invalidateWindow()

        assertEquals(gen + 1, e.generation)
        assertEquals(0, e.windowStart)
    }

    @Test
    fun resetWindow_reiniciaElInicioSinTocarLaGeneracion() {
        val e = engine(5, startIndex = 3)
        val gen = e.generation

        e.resetWindow()

        assertEquals(gen, e.generation)
        assertEquals(0, e.windowStart)
    }
}
