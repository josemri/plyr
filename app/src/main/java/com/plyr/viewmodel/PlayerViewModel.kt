package com.plyr.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.plyr.network.YouTubeManager
import com.plyr.utils.Config
import com.plyr.database.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer? get() = _exoPlayer

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _currentTitle = MutableLiveData<String?>()
    val currentTitle: LiveData<String?> = _currentTitle

    private val _currentPlaylist = MutableLiveData<List<TrackEntity>?>()
    val currentPlaylist: LiveData<List<TrackEntity>?> = _currentPlaylist

    private val _currentTrackIndex = MutableLiveData<Int>()
    val currentTrackIndex: LiveData<Int> = _currentTrackIndex

    private val _currentTrack = MutableLiveData<TrackEntity?>()
    val currentTrack: LiveData<TrackEntity?> = _currentTrack

    // Queue management
    private val _queueTracks = MutableLiveData<List<TrackEntity>>(emptyList())

    var onMediaSessionUpdate: ((ExoPlayer) -> Unit)? = null

    private var loadingJobsActive = false
    private var loadingJob: kotlinx.coroutines.Job? = null

    fun initializePlayer() {
        if (_exoPlayer == null) {
            _exoPlayer = buildPlayer()
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildPlayer(): ExoPlayer =
        ExoPlayer.Builder(getApplication())
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setHandleAudioBecomingNoisy(true)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .build().apply {
                repeatMode = when (Config.getRepeatMode(getApplication())) {
                    Config.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
                    Config.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _isLoading.postValue(playbackState == Player.STATE_BUFFERING)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _isLoading.postValue(false)
                        val prefix = com.plyr.utils.Translations.get(getApplication(), "error_prefix")
                        val msg = error.message ?: ""
                        _error.postValue(prefix + msg)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        updateCurrentTrackFromPlayer()
                        onMediaSessionUpdate?.invoke(this@apply)
                    }
                })
            }

    suspend fun loadAudioFromTrack(track: TrackEntity): Boolean = withContext(Dispatchers.Main) {
        try {
            _isLoading.postValue(true)
            _error.postValue(null)
            _currentTitle.postValue("${track.name} - ${track.artists}")

            val audioUrl = withContext(Dispatchers.IO) {
                // Verificar si audioUrl es una ruta de archivo local
                if (track.audioUrl != null && (track.audioUrl.startsWith("/") || track.audioUrl.startsWith("file://"))) {
                    val localFile = java.io.File(track.audioUrl.removePrefix("file://"))

                    if (localFile.exists()) {
                        return@withContext track.audioUrl
                    }
                }

                // Si no es archivo local, obtener de YouTube
                val videoId = track.youtubeVideoId ?: YouTubeManager.searchVideoId("${track.name} ${track.artists}")
                videoId?.let { YouTubeManager.getAudioUrl(it) }
            }
            if (audioUrl == null) {
                _isLoading.postValue(false)
                _error.postValue(com.plyr.utils.Translations.get(getApplication(), "error_obtaining_audio"))
                return@withContext false
            }

            initializePlayer()
            _exoPlayer?.let { player ->
                player.setMediaItem(createMediaItem(track, audioUrl))
                player.prepare()
                player.play()
                _isLoading.postValue(false)
                onMediaSessionUpdate?.invoke(player)

                // Iniciar carga concurrente de las siguientes canciones
                startLoadingRemainingTracks()
                true
            } ?: false
        } catch (e: Exception) {
            _isLoading.postValue(false)
            val prefix = com.plyr.utils.Translations.get(getApplication(), "error_prefix")
            _error.postValue(prefix + (e.message ?: ""))
            false
        }
    }

    private fun createMediaItem(track: TrackEntity, audioUrl: String) =
        MediaItem.Builder()
            .setUri(audioUrl)
            .setMediaId(track.id)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(track.name)
                    .setArtist(track.artists)
                    .build()
            )
            .build()

    private fun startLoadingRemainingTracks() {
        if (loadingJobsActive) return
        loadingJobsActive = true

        val playlist = _currentPlaylist.value ?: return
        val currentIndex = _currentTrackIndex.value ?: return

        loadingJob = viewModelScope.launch(Dispatchers.IO) {
            for (i in currentIndex + 1 until playlist.size) {
                val track = playlist[i]
                try {
                    val videoId = track.youtubeVideoId ?: YouTubeManager.searchVideoId("${track.name} ${track.artists}") ?: continue
                    val audioUrl = YouTubeManager.getAudioUrl(videoId) ?: continue

                    withContext(Dispatchers.Main) {
                        _exoPlayer?.addMediaItem(createMediaItem(track, audioUrl))
                    }
                } catch (_: Exception) {
                    // Continuar con la siguiente canción si hay error
                }
            }
            loadingJobsActive = false
        }
    }

    private fun updateCurrentTrackFromPlayer() {
        val player = _exoPlayer ?: return
        val playlist = _currentPlaylist.value ?: return

        val currentMediaId = player.currentMediaItem?.mediaId ?: return
        val newIndex = playlist.indexOfFirst { it.id == currentMediaId }

        if (newIndex >= 0) {
            _currentTrackIndex.postValue(newIndex)
            _currentTrack.postValue(playlist[newIndex])
            _currentTitle.postValue("${playlist[newIndex].name} - ${playlist[newIndex].artists}")
        }
    }

    fun pausePlayer() = _exoPlayer?.pause()

    fun playPlayer() = _exoPlayer?.play()

    fun setCurrentPlaylist(playlist: List<TrackEntity>, startIndex: Int = 0) {
        _currentPlaylist.postValue(playlist)
        val validIndex = startIndex.coerceIn(0, playlist.size - 1)
        _currentTrackIndex.postValue(validIndex)
        if (playlist.isNotEmpty() && validIndex in playlist.indices) {
            _currentTrack.postValue(playlist[validIndex])
        }
    }

    fun navigateToNext() {
        _exoPlayer?.seekToNextMediaItem()
    }

    fun navigateToPrevious() {
        _exoPlayer?.seekToPreviousMediaItem()
    }

    fun updateRepeatMode() {
        _exoPlayer?.repeatMode = when (Config.getRepeatMode(getApplication())) {
            Config.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ONE
            Config.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // Queue functionality
    fun addToQueue(track: TrackEntity) {
        // Añadir a la lista interna de queue
        val updatedQueue = _queueTracks.value?.toMutableList() ?: mutableListOf()
        updatedQueue.add(track)
        _queueTracks.postValue(updatedQueue)

        // Añadir también a la playlist actual para que se muestre en QueueScreen
        val currentPlaylist = _currentPlaylist.value?.toMutableList() ?: mutableListOf()
        currentPlaylist.add(track)
        _currentPlaylist.postValue(currentPlaylist)

        // Cargar el audio del track en el reproductor de forma asíncrona
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val videoId = track.youtubeVideoId ?: YouTubeManager.searchVideoId("${track.name} ${track.artists}")
                val audioUrl = videoId?.let { YouTubeManager.getAudioUrl(it) }

                if (audioUrl != null) {
                    withContext(Dispatchers.Main) {
                        _exoPlayer?.addMediaItem(createMediaItem(track, audioUrl))
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Limpia completamente el estado del reproductor:
     * - Cancela todas las cargas de canciones en progreso
     * - Para la reproducción actual
     * - Limpia la cola de reproducción
     * - Resetea el estado del reproductor
     */
    fun clearPlayerState() {
        // Cancelar trabajos de precarga
        loadingJob?.cancel()
        loadingJob = null
        loadingJobsActive = false

        // Parar y limpiar el reproductor
        _exoPlayer?.let { player ->
            player.stop()
            player.clearMediaItems()
        }

        // Limpiar estado
        _isLoading.postValue(false)
        _error.postValue(null)
    }

    override fun onCleared() {
        super.onCleared()
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
