package com.plyr.desktop.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Logos ASCII de la Home (M32): deben ser los mismos `ascii_1..50` PNG que usa
 * Android, copiados a los recursos del escritorio. Aquí se comprueba que los 50
 * cargan de verdad (es decir, que la copia a `src/main/resources` no se quedó a
 * medias) y que el índice aleatorio cae dentro del rango.
 */
class AsciiArtTest {

    @Test
    fun cargaLosCincuentaLogosDeAndroid() {
        for (index in 1..AsciiArt.COUNT) {
            assertNotNull("Falta el recurso ${AsciiArt.resourcePath(index)}", AsciiArt.load(index))
        }
    }

    @Test
    fun elIndiceAleatorioCaeDentroDelRango() {
        repeat(100) {
            val index = AsciiArt.randomIndex()
            assertTrue("Índice fuera de rango: $index", index in 1..AsciiArt.COUNT)
        }
    }

    @Test
    fun laRutaApuntaAlMismoNombreQueAndroid() {
        assertEquals("ascii/ascii_7.png", AsciiArt.resourcePath(7))
    }
}
