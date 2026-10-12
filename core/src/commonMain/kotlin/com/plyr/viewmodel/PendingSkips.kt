package com.plyr.viewmodel

/**
 * Peticiones de salto que llegan mientras sigue en vuelo una transición (B59).
 *
 * La `MediaSession` devuelve `RESULT_INFO_SKIPPED` a todo salto que la app
 * acepta atender (`SessionSkipCommand.handle`), y con eso media3 **no** lo
 * ejecuta en el reproductor: si la app lo descarta, no lo hace nadie. Pues
 * bien, `playIndex` lo descartaba con un `if (transitionInFlight) return` mudo
 * mientras resolvía por red —hasta `MAX_RESOLUTION_SKIPS` intentos de 30 s—, y
 * ahí acababa el salto: ni salto, ni error, ni feedback.
 *
 * Aquí se guardan, en el orden en que llegaron, para aplicarlas cuando la
 * transición termine. Con un tope: cada pendiente encadena otra transición con
 * su propia resolución de red, así que una racha de pulsaciones no puede
 * acabar en una cadena interminable.
 */
class PendingSkips(private val limit: Int = MAX_PENDING) {

    /** Sentido de una petición pendiente. */
    enum class Direction { FORWARDS, BACKWARDS }

    private val waiting = ArrayDeque<Direction>()

    /** Peticiones esperando su turno. */
    val size: Int get() = waiting.size

    /**
     * Apunta [direction]. Si la cola ya tiene [limit], la nueva se descarta: es
     * el único punto en el que se pierde una petición, y es a propósito.
     */
    fun request(direction: Direction) {
        if (waiting.size >= limit) return
        waiting.addLast(direction)
    }

    /** La petición más antigua, o `null` si no queda ninguna. */
    fun poll(): Direction? = if (waiting.isEmpty()) null else waiting.removeFirst()

    /** Se olvida de todo: lo que venga después manda. */
    fun clear() = waiting.clear()

    companion object {
        /** Peticiones que se admiten en cola antes de empezar a ignorar. */
        const val MAX_PENDING = 5
    }
}
