package com.plyr.viewmodel

import com.plyr.domain.RepeatMode

/**
 * Lógica pura de la cola de reproducción y su ventana deslizante.
 *
 * Reúne lo que antes vivía suelto en `PlayerViewModel` —la cola, el índice
 * actual, el modo de repetición y el [WindowState]— y las decisiones puras de
 * navegación ([QueueIndex]) y de ventana (qué tramo rellenar y qué recortar).
 *
 * No conoce coroutines, red, Android ni el reproductor: todo eso se queda en el
 * `PlayerViewModel`, que aplica aquí las decisiones. Al ser pura se testea en
 * JVM y es reutilizable, que es justo lo que desriesga el port.
 *
 * La cola es `List<T>` porque el contenido es opaco para esta clase: así no
 * depende de `TrackEntity`, que vive en `:app`.
 */
class QueueEngine<T>(
    /**
     * Canciones resueltas y preparadas por delante de la actual.
     *
     * Acota el tramo que se pide de golpe: cambiar a "repetir todo" estando en
     * la última canción no debe pedir resolver la cola entera.
     */
    val windowAhead: Int = 2,

    /**
     * Items que se dejan preparados detrás de la canción actual.
     *
     * Con uno basta para que `<<` sea un salto inmediato y sin cortes; sin
     * ninguno, cada `<<` caía en el camino asíncrono de resolución (B49).
     */
    val keepBehind: Int = 1,
) {

    var queue: List<T> = emptyList()
        private set

    var currentIndex: Int = -1
        private set

    var repeatMode: String = RepeatMode.OFF
        private set

    var windowState: WindowState = WindowState.EMPTY
        private set

    /** Índice de cola del primer item de la ventana que tiene el reproductor. */
    val windowStart: Int get() = windowState.startIndex

    /** Se incrementa en cada cambio de cola; invalida las resoluciones en vuelo. */
    val generation: Int get() = windowState.generation

    val size: Int get() = queue.size
    val isEmpty: Boolean get() = queue.isEmpty()
    val indices: IntRange get() = queue.indices

    fun trackAt(index: Int): T? = queue.getOrNull(index)

    val currentTrack: T? get() = queue.getOrNull(currentIndex)

    /**
     * Define la cola y ancla la ventana al inicio (o la reinicia si está vacía).
     *
     * No publica nada: reflejar el cambio en la UI es cosa del ViewModel.
     */
    fun setQueue(playlist: List<T>, startIndex: Int = 0) {
        queue = playlist
        if (playlist.isEmpty()) {
            currentIndex = -1
            windowState = windowState.reset()
            return
        }
        currentIndex = startIndex.coerceIn(0, playlist.size - 1)
        windowState = windowState.anchorAt(currentIndex)
    }

    fun appendTrack(track: T) {
        queue = queue + track
    }

    /**
     * Reemplaza la cola sin tocar el índice ni la ventana.
     *
     * Lo usa el encolado cuando la cola estaba parada: la lista mostrada manda
     * y no hay reproducción a la que anclar nada.
     */
    fun replaceQueue(playlist: List<T>) {
        queue = playlist
    }

    fun moveTo(index: Int) {
        currentIndex = index
    }

    fun setRepeatMode(mode: String) {
        repeatMode = mode
    }

    // --- Ventana ---

    fun anchorWindowAt(index: Int) {
        windowState = windowState.anchorAt(index)
    }

    fun resetWindow() {
        windowState = windowState.reset()
    }

    fun shiftWindow(removable: Int) {
        windowState = windowState.shiftForward(removable)
    }

    fun invalidateWindow() {
        windowState = windowState.invalidate()
    }

    // --- Navegación (pura, delegando en QueueIndex) ---

    fun nextTarget(): Int? = QueueIndex.nextIndex(currentIndex, queue.size, repeatMode)

    fun previousTarget(positionMs: Long): Int? =
        QueueIndex.previousIndex(currentIndex, queue.size, positionMs, repeatMode)

    fun onTrackEndedTarget(): Int? =
        QueueIndex.onTrackEnded(currentIndex, queue.size, repeatMode)

    fun retryTarget(candidate: Int, backwards: Boolean): Int? =
        QueueIndex.retryIndexFor(candidate, queue.size, repeatMode, backwards)

    /** Si la cola tiene una canción después de la actual (con "repetir todo", la tiene). */
    val hasNext: Boolean get() = nextTarget() != null

    // --- Cálculo de la ventana ---

    /**
     * Tramo de cola que falta por preparar, o `null` si la ventana ya cubre lo
     * que se pide (la cola entera, o la actual más [windowAhead]).
     */
    fun fillTarget(mediaItemCount: Int): FillTarget? {
        if (queue.isEmpty() || currentIndex !in queue.indices) return null

        val lastCovered = windowStart + mediaItemCount - 1
        val wanted = minOf(currentIndex + windowAhead, queue.size - 1)
        if (wanted - lastCovered <= 0) return null

        val from = lastCovered + 1
        val lastNew = minOf(wanted, from + windowAhead - 1)
        return if (from > lastNew) null else FillTarget(from, lastNew)
    }

    /**
     * Cuántos items por delante del actual se pueden descartar para que la
     * ventana no crezca sin límite. Nunca incluye el item en reproducción ni
     * los [keepBehind] que se dejan detrás.
     */
    fun trimCount(currentMediaItemIndex: Int): Int =
        currentMediaItemIndex - keepBehind

    /** Tramo de la cola que falta por preparar en la ventana. */
    data class FillTarget(val from: Int, val lastNew: Int)
}
