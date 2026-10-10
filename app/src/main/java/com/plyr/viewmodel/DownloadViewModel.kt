package com.plyr.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plyr.database.TrackEntity
import com.plyr.utils.AudioDownloader
import com.plyr.utils.DownloadPlan
import com.plyr.utils.DownloadedAudioStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Descarga en segundo plano el audio de todas las pistas de una lista que
 * falten por bajar.
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

    private var job: Job? = null

    fun startDownload(playlistId: String, playlistTitle: String, tracks: List<TrackEntity>) {
        if (_isDownloading.value) return

        _isDownloading.value = true
        _playlistId.value = playlistId
        _playlistTitle.value = playlistTitle
        _progress.value = 0f
        _message.value = ""
        _resultMessage.value = null

        job = viewModelScope.launch {
            val context = getApplication<Application>()
            val pending = DownloadPlan.pending(tracks) { DownloadedAudioStore.contains(context, it) }
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
                _message.value = "${index + 1}/$total  ${track.name}"
                val ok = AudioDownloader.download(context, videoId) { fraction ->
                    _progress.value = (index + fraction) / total
                }
                if (ok) done++ else failed++
            }

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
        _resultMessage.value = "cancelled"
    }

    fun dismissResult() {
        _resultMessage.value = null
    }
}
