package com.plyr.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plyr.database.TrackEntity
import com.plyr.utils.AudioDownloader
import com.plyr.utils.DownloadPlan
import com.plyr.utils.DownloadedAudioStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Descarga en segundo plano el audio de todas las pistas de una lista que
 * falten por bajar, además de gestionar el almacén offline (tamaño y borrado).
 *
 * Sigue el mismo patrón que [ImportViewModel]: vive en [com.plyr.PlyrApp]
 * (scope de Application), así que **sobrevive a apagar la pantalla y a salir de
 * la playlist** mientras el proceso siga vivo. No usa Service/WorkManager ni
 * notificación: es una corrutina de `viewModelScope`.
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    /** Lista a la que pertenece la descarga en curso (o la última terminada). */
    private val _playlistId = MutableStateFlow<String?>(null)
    val playlistId: StateFlow<String?> = _playlistId.asStateFlow()

    private val _playlistTitle = MutableStateFlow("")
    val playlistTitle: StateFlow<String> = _playlistTitle.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _resultMessage = MutableStateFlow<String?>(null)
    val resultMessage: StateFlow<String?> = _resultMessage.asStateFlow()

    /** `youtubeVideoId` de la pista que se está bajando ahora mismo (o null). */
    private val _currentVideoId = MutableStateFlow<String?>(null)
    val currentVideoId: StateFlow<String?> = _currentVideoId.asStateFlow()

    /** Progreso 0..1 dentro de la pista actual, para el indicador por fila. */
    private val _currentFraction = MutableStateFlow(0f)
    val currentFraction: StateFlow<Float> = _currentFraction.asStateFlow()

    /** Sube con cada cambio del almacén (pista bajada/borrada) para que la UI
     *  vuelva a calcular qué pistas están offline. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private var job: Job? = null

    fun startDownload(playlistId: String, playlistTitle: String, tracks: List<TrackEntity>) {
        if (_isDownloading.value) return

        _isDownloading.value = true
        _playlistId.value = playlistId
        _playlistTitle.value = playlistTitle
        _progress.value = 0f
        _message.value = ""
        _resultMessage.value = null
        _currentVideoId.value = null
        _currentFraction.value = 0f

        job = viewModelScope.launch {
            val context = getApplication<Application>()
            val pending = DownloadPlan.pending(tracks) { DownloadedAudioStore.localUri(context, it) != null }
            val total = pending.size
            if (total == 0) {
                _isDownloading.value = false
                _resultMessage.value = "already downloaded"
                return@launch
            }

            var done = 0
            var failed = 0
            for ((index, track) in pending.withIndex()) {
                val videoId = track.youtubeVideoId ?: continue
                _currentVideoId.value = videoId
                _currentFraction.value = 0f
                _message.value = "${index + 1}/$total  ${track.name}"
                val ok = AudioDownloader.download(context, videoId) { fraction ->
                    _currentFraction.value = fraction
                    _progress.value = (index + fraction) / total
                }
                if (ok) {
                    done++
                    _revision.value++
                } else {
                    failed++
                }
            }

            _currentVideoId.value = null
            _currentFraction.value = 0f
            _progress.value = 1f
            _isDownloading.value = false
            _resultMessage.value = when {
                failed == 0 -> "downloaded $done/$total"
                else -> "downloaded $done/$total, $failed failed"
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _isDownloading.value = false
        _currentVideoId.value = null
        _currentFraction.value = 0f
        _resultMessage.value = "cancelled"
    }

    fun dismissResult() {
        _resultMessage.value = null
    }

    /** Borra todo el audio offline. */
    fun clearDownloads() {
        viewModelScope.launch {
            val context = getApplication<Application>()
            withContext(Dispatchers.IO) { DownloadedAudioStore.deleteAll(context) }
            _revision.value++
        }
    }

    /** Borra el audio offline de un vídeo concreto. */
    fun removeDownload(videoId: String) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            withContext(Dispatchers.IO) { DownloadedAudioStore.delete(context, videoId) }
            _revision.value++
        }
    }
}
