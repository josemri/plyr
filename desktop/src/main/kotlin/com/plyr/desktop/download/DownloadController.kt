package com.plyr.desktop.download

import com.plyr.database.TrackRow
import com.plyr.utils.DownloadPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Desenlace de una descarga de lista (la UI de M17 lo traduce y lo muestra). */
enum class DownloadOutcome {
    /** No había nada que bajar: todas las pistas ya estaban offline. */
    ALREADY_DOWNLOADED,

    /** Se bajaron todas las pistas pendientes. */
    DOWNLOADED,

    /** Terminó con algún fallo (se reintentó, pero no se pudo). */
    PARTIAL,

    /** El usuario canceló. */
    CANCELLED,
}

/** Estado observable de la descarga offline de una lista. */
data class DownloadState(
    val isDownloading: Boolean = false,
    /** Lista a la que pertenece la descarga en curso (o la última terminada). */
    val playlistId: String? = null,
    val playlistTitle: String = "",
    /** Progreso global 0..1 (pistas completadas + fracción de la actual). */
    val progress: Float = 0f,
    val completed: Int = 0,
    val total: Int = 0,
    /** `youtubeVideoId` de la pista que se está bajando ahora mismo (o null). */
    val currentVideoId: String? = null,
    val currentTrackName: String = "",
    /** Progreso 0..1 dentro de la pista actual, para el indicador por fila. */
    val currentFraction: Float = 0f,
    /** Resultado de la última descarga terminada, hasta que se descarte. */
    val outcome: DownloadOutcome? = null,
    /** Sube con cada cambio del almacén (pista bajada/borrada) para que la UI
     *  vuelva a calcular qué pistas están offline. */
    val revision: Int = 0,
)

/**
 * Descarga en segundo plano el audio de las pistas de una lista que falten por
 * bajar, además de gestionar el almacén offline (tamaño y borrado). M16.
 *
 * Es el port a escritorio de `DownloadViewModel`: mismo flujo —planificar qué
 * falta ([DownloadPlan] de `:core`), descargar en serie informando del progreso,
 * permitir cancelar y limpiar— pero sobre `StateFlow` y sin Android. El estado
 * es un único [StateFlow] para que la UI lo observe (M17).
 *
 * La descarga no usa `Service`/`WorkManager`: es una corrutina del [scope]
 * inyectado, igual que hacía Android con `viewModelScope`.
 */
class DownloadController(
    private val scope: CoroutineScope,
    private val store: DownloadedAudioStore,
    private val downloader: AudioDownloader,
) {

    private val _state = MutableStateFlow(DownloadState())
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private var job: Job? = null

    /** ¿Está [videoId] descargado? (la UI filtra las pistas offline con esto). */
    fun isDownloaded(videoId: String): Boolean = store.isDownloaded(videoId)

    /**
     * Subconjunto de [videoIds] que ya está offline. La UI lo recalcula cuando
     * cambia [DownloadState.revision] (p. ej. tras terminar una descarga o borrar
     * un fichero), para pintar el indicador `↓` sin releer cada fila una a una.
     */
    fun downloadedIds(videoIds: Collection<String>): Set<String> =
        videoIds.filterTo(mutableSetOf()) { store.isDownloaded(it) }

    /** Cuántas pistas hay offline y cuánto ocupan (diálogo de almacenamiento, M17). */
    fun summary(): DownloadedAudioStore.Summary = store.summary()

    /**
     * Encola la descarga de las pistas de [tracks] que falten, una detrás de
     * otra, actualizando el progreso. Si ya hay una descarga en curso, no hace
     * nada (como `DownloadViewModel`).
     */
    fun startDownload(playlistId: String, playlistTitle: String, tracks: List<TrackRow>) {
        if (_state.value.isDownloading || job?.isActive == true) return
        _state.update {
            DownloadState(
                isDownloading = true,
                playlistId = playlistId,
                playlistTitle = playlistTitle,
                revision = it.revision,
            )
        }
        job = scope.launch { runDownload(tracks) }
    }

    private suspend fun runDownload(tracks: List<TrackRow>) {
        val pending = DownloadPlan.pending(
            tracks = tracks,
            videoIdOf = { it.youtubeVideoId },
            isDownloaded = { store.isDownloaded(it) },
        )
        val total = pending.size
        if (total == 0) {
            finish(DownloadOutcome.ALREADY_DOWNLOADED, completed = 0, total = 0)
            return
        }

        _state.update { it.copy(total = total) }
        var completed = 0
        var failed = 0
        for ((index, track) in pending.withIndex()) {
            val videoId = track.youtubeVideoId ?: continue
            val name = track.name
            _state.update {
                it.copy(
                    currentVideoId = videoId,
                    currentTrackName = name,
                    currentFraction = 0f,
                )
            }
            val ok = downloader.download(videoId) { fraction ->
                _state.update {
                    it.copy(currentFraction = fraction, progress = (index + fraction) / total)
                }
            }
            if (ok) {
                completed++
                _state.update { it.copy(completed = completed, revision = it.revision + 1) }
            } else {
                failed++
            }
        }
        finish(
            outcome = if (failed == 0) DownloadOutcome.DOWNLOADED else DownloadOutcome.PARTIAL,
            completed = completed,
            total = total,
        )
    }

    private fun finish(outcome: DownloadOutcome, completed: Int, total: Int) {
        _state.update {
            it.copy(
                isDownloading = false,
                progress = 1f,
                completed = completed,
                total = total,
                currentVideoId = null,
                currentTrackName = "",
                currentFraction = 0f,
                outcome = outcome,
            )
        }
    }

    /** Cancela la descarga en curso, si la hay. */
    fun cancel() {
        if (job?.isActive != true) return
        job?.cancel()
        job = null
        _state.update {
            it.copy(
                isDownloading = false,
                currentVideoId = null,
                currentTrackName = "",
                currentFraction = 0f,
                outcome = DownloadOutcome.CANCELLED,
            )
        }
    }

    /** Olvida el resultado de la última descarga (tras mostrarlo en la UI). */
    fun dismissResult() {
        _state.update { it.copy(outcome = null) }
    }

    /** Borra todo el audio offline. */
    fun clearDownloads() {
        scope.launch {
            withContext(Dispatchers.IO) { store.deleteAll() }
            _state.update { it.copy(revision = it.revision + 1) }
        }
    }

    /** Borra el audio offline de un vídeo concreto. */
    fun removeDownload(videoId: String) {
        scope.launch {
            val deleted = withContext(Dispatchers.IO) { store.delete(videoId) }
            if (deleted) {
                _state.update { it.copy(revision = it.revision + 1) }
            }
        }
    }
}
