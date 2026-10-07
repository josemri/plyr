package com.plyr.viewmodel

import com.plyr.utils.Config

/**
 * Lógica pura de navegación de la cola de reproducción.
 *
 * Aísla por completo las decisiones de "a qué canción voy ahora" del
 * reproductor y de la red, de modo que el comportamiento del salto
 * (incluido el final de la lista y los modos de repetición) se pueda
 * verificar con tests unitarios sin depender de Android ni de ExoPlayer.
 *
 * Todos los métodos devuelven `null` cuando no hay destino válido, lo que
 * el reproductor interpreta como "no hay a dónde saltar".
 */
object QueueIndex {

    /** Umbral (ms) a partir del cual "anterior" reinicia la canción en vez de retroceder. */
    const val PREV_RESTART_THRESHOLD_MS = 3_000L

    /**
     * Índice siguiente para una acción explícita del usuario (botón "siguiente").
     *
     * No aplica el modo "repetir uno": saltar hacia delante con `>>` siempre
     * avanza, y con repetición completa da la vuelta al final de la lista.
     *
     * @return índice destino, o `null` si no hay siguiente.
     */
    fun nextIndex(current: Int, size: Int, repeatMode: String): Int? {
        if (size <= 0 || current !in 0 until size) return null
        return when (repeatMode) {
            Config.REPEAT_MODE_ALL -> (current + 1) % size
            else -> if (current + 1 < size) current + 1 else null
        }
    }

    /**
     * Índice anterior para una acción explícita del usuario (botón "anterior").
     *
     * Igual que en cualquier reproductor: si la canción ya ha empezado, el
     * primer "anterior" la reinicia en lugar de retroceder en la cola.
     *
     * @return índice destino, o `null` si no hay anterior.
     */
    fun previousIndex(
        current: Int,
        size: Int,
        positionMs: Long,
        repeatMode: String
    ): Int? {
        if (size <= 0 || current !in 0 until size) return null
        if (positionMs > PREV_RESTART_THRESHOLD_MS) return current
        return when (repeatMode) {
            Config.REPEAT_MODE_ALL -> if (current == 0) size - 1 else current - 1
            else -> if (current > 0) current - 1 else null
        }
    }

    /**
     * Siguiente índice a probar cuando la canción pedida no se pudo resolver.
     *
     * El reintento tiene que ir **en la dirección del salto**, no siempre hacia
     * delante. Antes solo avanzaba, así que un `<<` a una pista que no se podía
     * resolver acababa sonando una canción distinta hacia delante: el usuario
     * pedía "la anterior" y oía otra cosa, sin ninguna señal de por qué (B49).
     *
     * No aplica el umbral de reinicio de [previousIndex]: aquí no hay decisión
     * de usuario que interpretar, solo un destino alternativo al que caer.
     *
     * @return índice destino, o `null` si no queda a dónde ir en ese sentido.
     */
    fun retryIndexFor(
        failed: Int,
        size: Int,
        repeatMode: String,
        backwards: Boolean
    ): Int? {
        if (size <= 0 || failed !in 0 until size) return null
        return if (backwards) {
            when (repeatMode) {
                Config.REPEAT_MODE_ALL -> if (failed == 0) size - 1 else failed - 1
                else -> if (failed > 0) failed - 1 else null
            }
        } else {
            nextIndex(failed, size, repeatMode)
        }
    }

    /**
     * Índice al que saltar cuando una canción termina de forma natural.
     *
     * Es la transición que antes no existía en la app: sin ella, si el
     * reproductor se quedaba sin canción siguiente preparada, la
     * reproducción se detenía en silencio.
     *
     * @return índice destino, o `null` si la cola ha terminado y hay que parar.
     */
    fun onTrackEnded(current: Int, size: Int, repeatMode: String): Int? {
        if (size <= 0 || current !in 0 until size) return null
        return when (repeatMode) {
            Config.REPEAT_MODE_ONE -> current
            Config.REPEAT_MODE_ALL -> (current + 1) % size
            else -> if (current + 1 < size) current + 1 else null
        }
    }

    /**
     * Longitud del tramo inicial contiguo de [indices] (`n, n+1, n+2, …`).
     *
     * La ventana del reproductor tiene que ser contigua: `resolveItems`
     * descarta los que no se pudieron resolver pero conserva los índices
     * originales, y cargar la lista con huecos haría que
     * `windowStart + mediaItemCount` dejara de corresponder con la cola
     * (B62). Se recorta en el primer hueco; lo que se corte se vuelve a
     * pedir al crecer la ventana.
     *
     * @return longitud del prefijo contiguo; `0` si la lista está vacía.
     */
    fun contiguousPrefixLength(indices: List<Int>): Int {
        if (indices.isEmpty()) return 0
        var expected = indices.first()
        for ((i, idx) in indices.withIndex()) {
            if (idx != expected) return i
            expected++
        }
        return indices.size
    }
}
