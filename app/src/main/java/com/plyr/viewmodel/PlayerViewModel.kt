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
import com.plyr.network.YouTubeAudioExtractor
import com.plyr.utils.isValidAudioUrl
import com.plyr.utils.Config
import com.plyr.service.YouTubeSearchManager
import com.plyr.database.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "PlayerViewModel"
        private const val PLAYER_INIT_MAX_ATTEMPTS = 50
        private const val PLAYER_INIT_DELAY_MS = 50L
    }

    // ExoPlayer
    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer? get() = _exoPlayer

    // Estado de reproducción
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _audioUrl = MutableLiveData<String?>()
    val audioUrl: LiveData<String?> = _audioUrl

    private val _currentTitle = MutableLiveData<String?>()
    val currentTitle: LiveData<String?> = _currentTitle

    // Playlist
    private val _currentPlaylist = MutableLiveData<List<TrackEntity>?>()
    val currentPlaylist: LiveData<List<TrackEntity>?> = _currentPlaylist

    private val _currentTrackIndex = MutableLiveData<Int>()
    val currentTrackIndex: LiveData<Int> = _currentTrackIndex

    private val _currentTrack = MutableLiveData<TrackEntity?>()
    val currentTrack: LiveData<TrackEntity?> = _currentTrack

    // Cola de reproducción
    private val _playbackQueue = MutableLiveData<MutableList<TrackEntity>>(mutableListOf())
    val playbackQueue: LiveData<MutableList<TrackEntity>> = _playbackQueue

    private val _isQueueMode = MutableLiveData(false)
    val isQueueMode: LiveData<Boolean> = _isQueueMode

    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    // Precarga
    private var preloadedNextAudioUrl: String? = null
    private var preloadedNextVideoId: String? = null

    // Callbacks
    var onStartMediaSession: ((ExoPlayer) -> Unit)? = null
    var onNextTrackReady: ((String) -> Unit)? = null

    private val youtubeSearchManager = YouTubeSearchManager(application)

    data class QueueState(
        val queue: List<TrackEntity> = emptyList(),
        val currentIndex: Int = -1,
        val isActive: Boolean = false
    )

    init {
        updateQueueState()
    }

    // === INICIALIZACIÓN DEL REPRODUCTOR ===

    fun initializePlayer() {
        viewModelScope.launch {
            if (_exoPlayer == null) {
                _exoPlayer = buildPlayer().also { setupPlayer(it) }
            }
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
            .build()

    private fun setupPlayer(player: ExoPlayer) {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _isLoading.postValue(playbackState == Player.STATE_BUFFERING)
                if (playbackState == Player.STATE_ENDED) {
                    handleRepeatModeTransition()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _isLoading.postValue(false)
                _error.postValue("Error de reproducción: ${error.message}")
                Log.e(TAG, "Playback error: ${error.message}", error)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    handleRepeatModeTransition()
                }
            }
        })
    }

    private suspend fun ensurePlayerInitialized(): Boolean {
        if (_exoPlayer != null) return true

        initializePlayer()
        var attempts = 0
        while (_exoPlayer == null && attempts < PLAYER_INIT_MAX_ATTEMPTS) {
            delay(PLAYER_INIT_DELAY_MS)
            attempts++
        }

        if (_exoPlayer == null) {
            _isLoading.postValue(false)
            _error.postValue("Error: No se pudo inicializar el reproductor")
            return false
        }
        return true
    }

    // === REPRODUCCIÓN DE AUDIO ===

    suspend fun loadAudioFromTrack(track: TrackEntity): Boolean = withContext(Dispatchers.Main) {
        try {
            _isLoading.postValue(true)
            _error.postValue(null)
            _currentTitle.postValue("${track.name} - ${track.artists}")

            val youtubeId = track.youtubeVideoId ?: obtainYouTubeId(track)
            if (youtubeId == null) {
                setError("No se encontró el video para: ${track.name}")
                return@withContext false
            }

            val audioUrl = getAudioUrl(youtubeId) ?: run {
                setError("No se pudo obtener el audio para: ${track.name}")
                return@withContext false
            }

            val success = playMedia(audioUrl, track.name, track.artists)

            if (success) {
                precacheNextTrack()
                _exoPlayer?.let { onStartMediaSession?.invoke(it) }
            }

            return@withContext success
        } catch (e: Exception) {
            setError("Error al cargar audio: ${e.message}")
            return@withContext false
        }
    }

    private suspend fun getAudioUrl(youtubeId: String): String? {
        // Verificar si está precargada
        if (preloadedNextAudioUrl != null &&
            preloadedNextVideoId == youtubeId &&
            isValidAudioUrl(preloadedNextAudioUrl!!)) {
            val url = preloadedNextAudioUrl!!
            preloadedNextAudioUrl = null
            preloadedNextVideoId = null
            return url
        }

        // Extraer URL de audio
        return withContext(Dispatchers.IO) {
            YouTubeAudioExtractor.getAudioUrl(youtubeId)
        }?.takeIf { isValidAudioUrl(it) }
    }

    private suspend fun playMedia(audioUrl: String, title: String, artist: String): Boolean {
        _audioUrl.postValue(audioUrl)

        return withContext(Dispatchers.Main) {
            if (!ensurePlayerInitialized()) return@withContext false

            _exoPlayer?.let { player ->
                try {
                    val mediaItem = MediaItem.Builder()
                        .setUri(audioUrl)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(title)
                                .setArtist(artist)
                                .build()
                        )
                        .build()

                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                    _isLoading.postValue(false)
                    true
                } catch (e: Exception) {
                    setError("Error configurando ExoPlayer: ${e.message}")
                    false
                }
            } ?: run {
                setError("Error: Reproductor no disponible")
                false
            }
        }
    }

    suspend fun obtainYouTubeId(track: TrackEntity): String? =
        withContext(Dispatchers.IO) {
            youtubeSearchManager.getYouTubeIdTransparently(track)
        }

    // === PRECARGA ===

    private fun precacheNextTrack() {
        val playlist = _currentPlaylist.value
        val index = _currentTrackIndex.value ?: -1

        if (playlist == null || index < 0 || index + 1 >= playlist.size) {
            preloadedNextAudioUrl = null
            preloadedNextVideoId = null
            return
        }

        val nextTrack = playlist[index + 1]
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nextVideoId = obtainYouTubeId(nextTrack) ?: return@launch
                val nextAudioUrl = YouTubeAudioExtractor.getAudioUrl(nextVideoId)

                if (nextAudioUrl != null && isValidAudioUrl(nextAudioUrl)) {
                    preloadedNextAudioUrl = nextAudioUrl
                    preloadedNextVideoId = nextVideoId
                    withContext(Dispatchers.Main) {
                        onNextTrackReady?.invoke(nextAudioUrl)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error precargando siguiente canción", e)
            }
        }
    }

    // === CONTROLES DE REPRODUCCIÓN ===

    fun pausePlayer() {
        _exoPlayer?.pause()
    }

    fun playPlayer() {
        _exoPlayer?.play()
    }

    // === NAVEGACIÓN ===

    fun setCurrentPlaylist(playlist: List<TrackEntity>, startIndex: Int = 0) {
        _currentPlaylist.postValue(playlist)
        val validIndex = startIndex.coerceIn(0, playlist.size - 1)
        _currentTrackIndex.postValue(validIndex)
        if (playlist.isNotEmpty() && validIndex in playlist.indices) {
            _currentTrack.postValue(playlist[validIndex])
        }
    }

    suspend fun navigateToNext(): Boolean {
        if (_isQueueMode.value == true) {
            return playNextFromQueue()
        }

        val playlist = _currentPlaylist.value ?: return false
        val currentIndex = _currentTrackIndex.value ?: return false

        if (currentIndex >= playlist.size - 1) return false

        val nextIndex = currentIndex + 1
        val nextTrack = playlist[nextIndex]
        _currentTrackIndex.postValue(nextIndex)
        _currentTrack.postValue(nextTrack)
        return loadAudioFromTrack(nextTrack)
    }

    suspend fun navigateToPrevious(): Boolean {
        val playlist = _currentPlaylist.value ?: return false
        val currentIndex = _currentTrackIndex.value ?: return false

        if (currentIndex <= 0) return false

        val previousIndex = currentIndex - 1
        val previousTrack = playlist[previousIndex]
        _currentTrackIndex.postValue(previousIndex)
        _currentTrack.postValue(previousTrack)
        return loadAudioFromTrack(previousTrack)
    }

    // === COLA DE REPRODUCCIÓN ===

    private suspend fun playNextFromQueue(): Boolean {
        val queue = _playbackQueue.value
        if (queue.isNullOrEmpty()) {
            _isQueueMode.postValue(false)
            return false
        }

        val nextTrack = queue.removeAt(0)
        _playbackQueue.postValue(queue)
        val success = loadAudioFromTrack(nextTrack)
        if (success) {
            _currentTrack.postValue(nextTrack)
        }
        return success
    }

    fun clearQueue() {
        _playbackQueue.postValue(mutableListOf())
        _isQueueMode.postValue(false)
        updateQueueState()
    }

    suspend fun playQueueFromIndex(startIndex: Int) {
        val queue = _playbackQueue.value ?: return
        if (startIndex !in queue.indices) return

        val reorderedQueue = queue.drop(startIndex).toMutableList()
        _playbackQueue.postValue(reorderedQueue)
        _isQueueMode.postValue(true)
        updateQueueState()
        playNextFromQueue()
    }

    private fun updateQueueState() {
        val queue = _playbackQueue.value ?: emptyList()
        val isActive = _isQueueMode.value ?: false
        _queueState.value = QueueState(
            queue = queue.toList(),
            currentIndex = -1,
            isActive = isActive
        )
    }

    // === MODOS DE REPETICIÓN ===

    fun handleRepeatModeTransition() {
        val repeatMode = Config.getRepeatMode(getApplication())

        viewModelScope.launch {
            when (repeatMode) {
                Config.REPEAT_MODE_ONE -> repeatCurrentTrack()
                Config.REPEAT_MODE_ALL -> handleInfiniteRepeat()
                else -> navigateToNext()
            }
        }
    }

    private suspend fun repeatCurrentTrack() {
        val currentTrack = _currentTrack.value ?: run {
            navigateToNext()
            return
        }

        delay(500)
        if (!loadAudioFromTrack(currentTrack)) {
            navigateToNext()
        }
    }

    private suspend fun handleInfiniteRepeat() {
        val playlist = _currentPlaylist.value
        val currentIndex = _currentTrackIndex.value

        if (playlist != null && currentIndex != null && currentIndex < playlist.size - 1) {
            navigateToNext()
        } else {
            restartPlaylistFromBeginning()
        }
    }

    private suspend fun restartPlaylistFromBeginning() {
        if (_isQueueMode.value == true) {
            _isQueueMode.postValue(false)
            updateQueueState()
            return
        }

        val playlist = _currentPlaylist.value
        if (playlist.isNullOrEmpty()) return

        val firstTrack = playlist[0]
        _currentTrackIndex.postValue(0)
        _currentTrack.postValue(firstTrack)
        delay(1000)
        loadAudioFromTrack(firstTrack)
    }

    // === UTILIDADES ===

    private fun setError(message: String) {
        _isLoading.postValue(false)
        _error.postValue(message)
    }

    override fun onCleared() {
        super.onCleared()
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
