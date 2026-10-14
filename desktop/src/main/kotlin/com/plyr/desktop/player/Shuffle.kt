package com.plyr.desktop.player

import kotlin.random.Random

/**
 * Barajado puro de una lista (M20).
 *
 * Vive aparte de [PlayerController] para poder fijar la semilla en los tests: el
 * controlador usa [Random.Default] en producción y las pruebas inyectan un
 * [Random] sembrado, de modo que el orden aleatorio es reproducible. Es una
 * permutación: no añade, quita ni transforma elementos.
 */
object Shuffle {
    fun <T> randomized(items: List<T>, random: Random = Random.Default): List<T> = items.shuffled(random)
}
