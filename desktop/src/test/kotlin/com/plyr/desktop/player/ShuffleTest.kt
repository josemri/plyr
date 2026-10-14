package com.plyr.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Tests del barajado puro de M20.
 *
 * El punto interesante no es que "a veces" cambie el orden, sino que la semilla
 * haga el resultado reproducible y que nunca se pierda ni se duplique una pista.
 */
class ShuffleTest {

    private val items = (1..20).map { "t$it" }

    @Test
    fun randomized_esUnaPermutacion() {
        val shuffled = Shuffle.randomized(items, Random(1))

        assertEquals(items.size, shuffled.size)
        assertEquals(items.sorted(), shuffled.sorted())
    }

    @Test
    fun randomized_conLaMismaSemillaEsReproducible() {
        assertEquals(
            Shuffle.randomized(items, Random(42)),
            Shuffle.randomized(items, Random(42)),
        )
    }

    @Test
    fun randomized_cambiaElOrden() {
        // Con 20 elementos y esta semilla el orden cambia con seguridad.
        assertNotEquals(items, Shuffle.randomized(items, Random(7)))
    }

    @Test
    fun randomized_dejaIntactosVacioYUnico() {
        assertTrue(Shuffle.randomized(emptyList<String>(), Random(1)).isEmpty())
        assertEquals(listOf("solo"), Shuffle.randomized(listOf("solo"), Random(1)))
    }
}
