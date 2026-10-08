package com.plyr.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowStateTest {

    @Test
    fun estadoInicial_valoresPorDefecto() {
        val state = WindowState.EMPTY
        assertEquals(0, state.startIndex)
        assertEquals(0, state.generation)
    }

    @Test
    fun invalidate_incrementaGeneracionYReseteaAncla() {
        val state = WindowState(startIndex = 5, generation = 2)
        val invalidated = state.invalidate()
        assertEquals(0, invalidated.startIndex)
        assertEquals(3, invalidated.generation)
    }

    @Test
    fun anchorAt_mueveSoloAncla() {
        val state = WindowState(startIndex = 0, generation = 1)
        val anchored = state.anchorAt(7)
        assertEquals(7, anchored.startIndex)
        assertEquals(1, anchored.generation)
    }

    @Test
    fun shiftForward_desplazaAncla() {
        val state = WindowState(startIndex = 2, generation = 0)
        val shifted = state.shiftForward(3)
        assertEquals(5, shifted.startIndex)
        assertEquals(0, shifted.generation)
    }

    @Test
    fun shiftForward_deltaNegativo_noCambiaNada() {
        val state = WindowState(startIndex = 2, generation = 5)
        val shifted = state.shiftForward(-1)
        assertEquals(state, shifted)
    }

    @Test
    fun reset_vuelveAOrigenManteniendoGeneracion() {
        val state = WindowState(startIndex = 10, generation = 3)
        val reset = state.reset()
        assertEquals(0, reset.startIndex)
        assertEquals(3, reset.generation)
    }

    @Test
    fun bumpGeneration_incrementaSoloGeneracion() {
        val state = WindowState(startIndex = 4, generation = 1)
        val bumped = state.bumpGeneration()
        assertEquals(4, bumped.startIndex)
        assertEquals(2, bumped.generation)
    }
}
