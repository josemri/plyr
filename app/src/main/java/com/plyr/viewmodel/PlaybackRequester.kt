package com.plyr.viewmodel

import com.plyr.database.TrackEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Resoluciones de reproducción pedidas por la UI.
 *
 * Agrupa el ciclo de vida del job de [playTrack] en el scope que se le da
 * (el del `ViewModel`), de modo que navegar fuera de la pantalla que pidió la
 * canción no la cancele (B60). El estado de "estoy resolviendo" vive en
 * [PlayerViewModel]; aquí solo se gestiona el job.
 */
class PlaybackRequester(
    private val scope: CoroutineScope,
    private val load: suspend (TrackEntity) -> Unit,
) {

    private var job: Job? = null

    /**
     * Resuelve [track] y la reproduce.
     *
     * @param onFinished se invoca cuando la carga termina, **también si se
     *   cancela**, para que la pantalla apague su estado de "iniciando".
     */
    fun play(track: TrackEntity, onFinished: (() -> Unit)? = null) {
        job?.cancel()
        job = scope.launch {
            try {
                load(track)
            } finally {
                onFinished?.invoke()
            }
        }
    }

    /** Cancela una resolución en vuelo sin tocar la cola: es el "stop" de la UI. */
    fun cancel() {
        job?.cancel()
        job = null
    }
}
