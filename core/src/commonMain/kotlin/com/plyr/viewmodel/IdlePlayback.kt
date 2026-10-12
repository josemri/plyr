package com.plyr.viewmodel

/**
 * Qué debe hacer el botón play cuando el reproductor se ha quedado quieto
 * tras `stopAtQueueEnd` (fin de cola, o rendición tras varios fallos) — B63.
 *
 * Allí el player queda en **IDLE**: `stop()` + `clearMediaItems()` no dejan
 * nada preparado, y `player.play()` sobre un player así no hace nada (ExoPlayer
 * "never moves out of IDLE automatically" y, sin items, no hay nada que
 * reproducir). Si después se rellena la ventana (`addToQueue` →
 * `growWindow`), los items entran, pero nadie llama a `prepare()`, así que
 * sigue en IDLE: cargados y mudos.
 *
 * La segunda mitad del problema es el **ancla de la ventana**: al vaciarse, el
 * próximo relleno parte de `windowStart`. Si quedara en 0 mientras
 * `currentIndex` sigue apuntando a la última canción, la ventana nueva
 * empezaría por delante de lo que la UI enseña y, en cuanto sonara, la UI y el
 * reproductor mostrarían canciones distintas.
 *
 * Es lógica pura a propósito: se testea sin Android.
 */
object IdlePlayback {

    /** Decisión para `playPlayer()`. */
    sealed interface Action {
        /** El player no está en IDLE: `play()` basta. */
        data object Resume : Action

        /**
         * IDLE con items ya cargados (ventana rellenada tras el final):
         * basta `prepare()` para que suenen, con la ventana alineada.
         */
        data object PrepareAndPlay : Action

        /** IDLE sin items y con cola: hay que resolver y arrancar [index]. */
        data class RestartAt(val index: Int) : Action

        /** IDLE sin nada que reproducir. */
        data object Nothing : Action
    }

    /**
     * @param isIdle `player.playbackState == Player.STATE_IDLE`
     * @param mediaItemCount items que ya tiene la ventana del reproductor
     * @param queueSize tamaño de la cola, la fuente de verdad
     * @param currentIndex índice de cola que la UI está enseñando
     */
    fun decide(
        isIdle: Boolean,
        mediaItemCount: Int,
        queueSize: Int,
        currentIndex: Int,
    ): Action = when {
        !isIdle -> Action.Resume
        mediaItemCount > 0 -> Action.PrepareAndPlay
        queueSize <= 0 -> Action.Nothing
        else -> Action.RestartAt(windowAnchor(currentIndex, queueSize))
    }

    /**
     * Ancla de la ventana vacía: índice de cola del primer item que cargará
     * el próximo relleno (`growWindow`).
     *
     * Es `currentIndex` — así lo que suena empieza por la canción que la UI
     * enseña — o 0 si la posición no es válida (B63).
     */
    fun windowAnchor(currentIndex: Int, queueSize: Int): Int =
        if (currentIndex in 0 until queueSize) currentIndex else 0
}
