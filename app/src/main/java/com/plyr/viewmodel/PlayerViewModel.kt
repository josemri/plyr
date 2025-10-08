package com.plyr.viewmodel

import android.app.Application
import android.content.BroadcastReceiver
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.media.AudioDeviceInfo

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private var _exoPlayer: ExoPlayer? = null

    private var _currentPlayerListener: Player.Listener? = null

    private var audioManager: AudioManager? = null

    private var audioOutputReceiver: BroadcastReceiver? = null

    private var wasHeadsetConnected = false

    private val _audioUrl = MutableLiveData<String?>()

    private val _currentTitle = MutableLiveData<String?>()

    private val _isLoading = MutableLiveData<Boolean>()

    private val _error = MutableLiveData<String?>()

    private val mainHandler = Handler(Looper.getMainLooper())

    private val youtubeSearchManager = YouTubeSearchManager(application)

    private var playbackEndedCallback: CompletableDeferred<Boolean>? = null

    private val _currentPlaylist = MutableLiveData<List<TrackEntity>?>()

    private val _currentTrackIndex = MutableLiveData<Int>()

    private val _currentTrack = MutableLiveData<TrackEntity?>()

    private val _hasPrevious = MutableLiveData(false)

    private val _hasNext = MutableLiveData(false)

    private val _playbackQueue = MutableLiveData<MutableList<TrackEntity>>(mutableListOf())

    private val _isQueueMode = MutableLiveData(false)

    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    val exoPlayer: ExoPlayer? get() = _exoPlayer

    val audioUrl: LiveData<String?> = _audioUrl

    val currentTitle: LiveData<String?> = _currentTitle

    val isLoading: LiveData<Boolean> = _isLoading

    val error: LiveData<String?> = _error

    val currentPlaylist: LiveData<List<TrackEntity>?> = _currentPlaylist

    val currentTrackIndex: LiveData<Int> = _currentTrackIndex

    val currentTrack: LiveData<TrackEntity?> = _currentTrack

    val hasPrevious: LiveData<Boolean> = _hasPrevious

    val hasNext: LiveData<Boolean> = _hasNext

    val playbackQueue: LiveData<MutableList<TrackEntity>> = _playbackQueue

    val isQueueMode: LiveData<Boolean> = _isQueueMode

    private var waitForSongJob: Job? = null

    // Variables para precarga de la siguiente canción
    private var preloadedNextAudioUrl: String? = null
    private var preloadedNextVideoId: String? = null
    var onStartMediaSession: ((ExoPlayer) -> Unit)? = null
    var onNextTrackReady: ((String) -> Unit)? = null


    init {
        updateQueueState()
        _playbackQueue.observeForever {
            updateNavigationState()
        }
        _currentPlaylist.observeForever {
            updateNavigationState()
        }
        _currentTrackIndex.observeForever {
            updateNavigationState()
        }
        initializeAudioOutputDetection()
    }


    fun initializePlayer() {
        CoroutineScope(Dispatchers.Main).launch {
            if (_exoPlayer == null) {
                _exoPlayer = buildPlayer().also { setupPlayer(it) }
            }
            monitorMemoryUsage()
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildPlayer(): ExoPlayer =
        ExoPlayer.Builder(getApplication())
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()

    private fun setupPlayer(player: ExoPlayer) {
        _currentPlayerListener = createPlayerListener()
        _currentPlayerListener?.let { player.addListener(it) }
        player.setHandleAudioBecomingNoisy(true)
        optimizeBufferSettings(player)

        // Ahora, al cambiar de pista, navega igual que el botón "siguiente"
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ){
                    CoroutineScope(Dispatchers.Main).launch {
                        navigateToNext()
                    }
                }
            }
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK &&
                    oldPosition.mediaItemIndex == newPosition.mediaItemIndex &&
                    newPosition.positionMs == 0L &&
                    oldPosition.positionMs > 1000L // estaba avanzando en la pista
                ) {
                    // 👇 interpretamos este caso como PREV
                    CoroutineScope(Dispatchers.Main).launch {
                        navigateToPrevious()
                    }
                }
            }

        })
    }


    private fun createPlayerListener(): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        // Show loading indicator when buffering
                        _isLoading.postValue(true)
                        Log.d(TAG, "Player is buffering")
                    }
                    Player.STATE_READY -> {
                        // Hide loading indicator when ready
                        _isLoading.postValue(false)
                        Log.d(TAG, "Player is ready")
                    }
                    Player.STATE_ENDED -> {
                        // Hide loading indicator and handle repeat logic
                        _isLoading.postValue(false)
                        playbackEndedCallback?.complete(true)
                        playbackEndedCallback = null
                        handleRepeatModeTransition()
                        Log.d(TAG, "Playback ended")
                    }
                    Player.STATE_IDLE -> {
                        // Hide loading indicator and log idle state
                        _isLoading.postValue(false)
                        Log.d(TAG, "Player is idle")
                    }
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                _isLoading.postValue(false)
                _error.postValue("Error de reproducción: ${error.message}")
                Log.e(TAG, "Playback error: ${error.message}", error)
            }
        }
    }

    fun loadAudio(videoId: String, title: String? = null) {
        _isLoading.postValue(true)
        _error.postValue(null)
        _currentTitle.postValue(title)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Si la URL está precargada y corresponde al videoId solicitado, usarla
                if (preloadedNextAudioUrl != null && preloadedNextVideoId == videoId && isValidAudioUrl(preloadedNextAudioUrl!!)) {
                    // Limpiar precarga tras usarla
                    preloadedNextAudioUrl = null
                    preloadedNextVideoId = null
                } else {
                    val audioUrl = extractAudioUrl(videoId)
                    if (audioUrl != null && isValidAudioUrl(audioUrl)) {
                        playAudioFromUrl(audioUrl, title, null)
                    } else {
                        _isLoading.postValue(false)
                        _error.postValue("No se pudo extraer la URL de audio para el video ID: $videoId")
                    }
                }
                // Precargar la siguiente canción si existe
                precacheNextTrack()
            } catch (e: Exception) {
                _isLoading.postValue(false)
                _error.postValue("Error al extraer audio: ${e.message}")
            }
        }
    }

    // Precarga la siguiente canción si existe en la playlist
    @OptIn(UnstableApi::class)
    fun precacheNextTrack() {
        val playlist = _currentPlaylist.value
        val index = _currentTrackIndex.value ?: -1
        if (playlist != null && index >= 0 && index + 1 < playlist.size) {
            val nextTrack = playlist[index + 1]
            CoroutineScope(Dispatchers.IO).launch {
                val nextVideoId = obtainYouTubeId(nextTrack)
                if (nextVideoId != null) {
                    val nextAudioUrl = YouTubeAudioExtractor.getAudioUrl(nextVideoId)
                    if (nextAudioUrl != null && isValidAudioUrl(nextAudioUrl)) {
                        preloadedNextAudioUrl = nextAudioUrl
                        preloadedNextVideoId = nextVideoId
                        withContext(Dispatchers.Main) {
                            val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(nextTrack.name)
                                .setArtist(nextTrack.artists)
                                .build()
                            val mediaItem = MediaItem.Builder()
                                .setUri(nextAudioUrl)
                                .setMediaMetadata(mediaMetadata)
                                .build()
                            _exoPlayer?.addMediaItem(mediaItem)
                            // Llamamos al callback, el Service lo recibe
                            onNextTrackReady?.invoke(nextAudioUrl)
                        }
                    } else {
                        preloadedNextAudioUrl = null
                        preloadedNextVideoId = null
                    }
                } else {
                    preloadedNextAudioUrl = null
                    preloadedNextVideoId = null
                }
            }
        } else {
            preloadedNextAudioUrl = null
            preloadedNextVideoId = null
        }
    }

    private suspend fun extractAudioUrl(videoId: String): String? {
        return withContext(Dispatchers.IO) {
            YouTubeAudioExtractor.getAudioUrl(videoId)
        }
    }

    private fun playAudioFromUrl(audioUrl: String, title: String? = null, artist: String? = null) {
        _audioUrl.postValue(audioUrl)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (_exoPlayer == null) {
                    initializePlayer()
                    var attempts = 0
                    while (_exoPlayer == null && attempts < 50) {
                        delay(50)
                        attempts++
                    }
                    if (_exoPlayer == null) {
                        _isLoading.postValue(false)
                        _error.postValue("Error: No se pudo inicializar el reproductor")
                        return@launch
                    }
                }
                _exoPlayer?.let { player ->
                    val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(title ?: "Unknown Title")
                        .setArtist(artist ?: "Unknown Artist")
                        .build()
                    val mediaItem = MediaItem.Builder()
                        .setUri(audioUrl)
                        .setMediaMetadata(mediaMetadata)
                        .build()
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                    _isLoading.postValue(false)
                } ?: run {
                    _isLoading.postValue(false)
                    _error.postValue("Error: Reproductor no disponible")
                }
            } catch (e: Exception) {
                _isLoading.postValue(false)
                _error.postValue("Error configurando ExoPlayer: ${e.message}")
            }
        }
    }

    suspend fun loadAudioFromTrack(track: TrackEntity): Boolean = withContext(Dispatchers.Main) {
        try {
            _isLoading.postValue(true)
            _error.postValue(null)
            _currentTitle.postValue("${track.name} - ${track.artists}")
            val youtubeId = obtainYouTubeId(track)
            if (youtubeId != null) {
                if (preloadedNextAudioUrl != null && preloadedNextVideoId == youtubeId && isValidAudioUrl(preloadedNextAudioUrl!!)) {
                    preloadedNextAudioUrl = null
                    preloadedNextVideoId = null
                    precacheNextTrack()
                    // Call MediaSession setup after playback starts
                    _exoPlayer?.let { onStartMediaSession?.invoke(it) }
                    return@withContext true
                } else {
                    val result = playTrackAudio(youtubeId, track)
                    precacheNextTrack()
                    // Call MediaSession setup after playback starts
                    _exoPlayer?.let { onStartMediaSession?.invoke(it) }
                    return@withContext result
                }
            } else {
                _isLoading.postValue(false)
                _error.postValue("No se encontró el video para: ${track.name}")
                return@withContext false
            }
        } catch (e: Exception) {
            _isLoading.postValue(false)
            _error.postValue("Error al cargar audio desde track: ${e.message}")
            return@withContext false
        }
    }

    suspend fun obtainYouTubeId(track: TrackEntity): String? {
        return withContext(Dispatchers.IO) {
            youtubeSearchManager.getYouTubeIdTransparently(track)
        }
    }

    private suspend fun playTrackAudio(youtubeId: String, track: TrackEntity): Boolean {
        val audioUrl = withContext(Dispatchers.IO) {
            YouTubeAudioExtractor.getAudioUrl(youtubeId)
        }
        if (audioUrl != null && isValidAudioUrl(audioUrl)) {
            return configureAndPlayAudio(audioUrl, track)
        } else {
            _isLoading.postValue(false)
            _error.postValue("No se pudo obtener el audio para: ${track.name}")
            return false
        }
    }

    private suspend fun configureAndPlayAudio(audioUrl: String, track: TrackEntity): Boolean {
        _audioUrl.postValue(audioUrl)
        return withContext(Dispatchers.Main) {
            if (_exoPlayer == null) {
                initializePlayer()
                var attempts = 0
                while (_exoPlayer == null && attempts < 50) {
                    delay(50)
                    attempts++
                }
                if (_exoPlayer == null) {
                    _isLoading.postValue(false)
                    _error.postValue("Error: No se pudo inicializar el reproductor")
                    return@withContext false
                }
            }
            val player = _exoPlayer
            if (player != null) {
                try {
                    val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(track.name)
                        .setArtist(track.artists)
                        .build()
                    val mediaItem = MediaItem.Builder()
                        .setUri(audioUrl)
                        .setMediaMetadata(mediaMetadata)
                        .build()
                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                    _isLoading.postValue(false)
                    true
                } catch (e: Exception) {
                    _isLoading.postValue(false)
                    _error.postValue("Error configurando ExoPlayer: ${e.message}")
                    false
                }
            } else {
                _isLoading.postValue(false)
                _error.postValue("Error: Reproductor no disponible")
                false
            }
        }
    }

    fun pausePlayer() {
        _exoPlayer?.pause()
    }

    fun playPlayer() {
        _exoPlayer?.play()
    }

    private fun updateNavigationState() {
        val isQueue = _isQueueMode.value == true
        val queue = _playbackQueue.value
        val queueSize = queue?.size ?: 0
        val playlist = _currentPlaylist.value
        val currentIndex = _currentTrackIndex.value

        if (isQueue) {
            _hasPrevious.postValue(false)
            _hasNext.postValue(queueSize > 0)
        } else if (playlist != null && currentIndex != null) {
            val hasPrev = currentIndex > 0
            val hasNext = currentIndex < playlist.size - 1
            _hasPrevious.postValue(hasPrev)
            _hasNext.postValue(hasNext)
        } else {
            _hasPrevious.postValue(false)
            _hasNext.postValue(false)
        }
    }

    fun setCurrentPlaylist(playlist: List<TrackEntity>, startIndex: Int = 0) {
        _currentPlaylist.postValue(playlist)
        _currentTrackIndex.postValue(startIndex.coerceIn(0, playlist.size - 1))

        if (playlist.isNotEmpty() && startIndex in playlist.indices) {
            _currentTrack.postValue(playlist[startIndex])
        }

        updateNavigationState()
    }

    suspend fun playNextFromQueue(): Boolean {
        val queue = _playbackQueue.value
        if (queue.isNullOrEmpty()) {
            _isQueueMode.postValue(false)
            updateNavigationState()
            return false
        }
        val nextTrack = queue.removeAt(0)
        _playbackQueue.postValue(queue)
        updateNavigationState()
        val success = loadAudioFromTrack(nextTrack)
        if (success) {
            _currentTrack.postValue(nextTrack)
        }
        return success
    }

    suspend fun navigateToNext(): Boolean {
        if (_isQueueMode.value == true) {
            return playNextFromQueue()
        }
        val playlist = _currentPlaylist.value ?: return false
        val currentIndex = _currentTrackIndex.value ?: return false
        if (currentIndex < playlist.size - 1) {
            val nextIndex = currentIndex + 1
            val nextTrack = playlist[nextIndex]
            _currentTrackIndex.postValue(nextIndex)
            _currentTrack.postValue(nextTrack)
            updateNavigationState()
            val success = loadAudioFromTrack(nextTrack)
            return success
        }
        return false
    }

    suspend fun navigateToPrevious(): Boolean {
        val playlist = _currentPlaylist.value ?: return false
        val currentIndex = _currentTrackIndex.value ?: return false
        if (currentIndex > 0) {
            val previousIndex = currentIndex - 1
            val previousTrack = playlist[previousIndex]
            _currentTrackIndex.postValue(previousIndex)
            _currentTrack.postValue(previousTrack)
            updateNavigationState()
            val success = loadAudioFromTrack(previousTrack)
            return success
        }
        return false
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

    private fun monitorMemoryUsage() {
        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryPercentage = (usedMemory * 100) / maxMemory

            if (memoryPercentage > 80) {
                Log.w(TAG, "⚠️ Uso de memoria alto, realizando limpieza preventiva")

                System.gc()
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error monitoreando memoria", e)
        }
    }

    data class QueueState(
        val queue: List<TrackEntity> = emptyList(),
        val currentIndex: Int = -1,
        val isActive: Boolean = false
    )

    private fun optimizeBufferSettings(player: ExoPlayer) {
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ No se pudo optimizar configuración de buffer", e)
        }
    }

    // === MÉTODOS DE MODO DE REPETICIÓN ===

    /**
     * Maneja la transición basada en el modo de repetición configurado.
     * Se llama cuando una canción termina para determinar qué hacer siguiente.
     */
    private fun handleRepeatModeTransition() {
        val context = getApplication<Application>()
        val repeatMode = Config.getRepeatMode(context)

        when (repeatMode) {
            Config.REPEAT_MODE_OFF -> {
                CoroutineScope(Dispatchers.Main).launch {
                    navigateToNext()
                }
            }

            Config.REPEAT_MODE_ONE -> {
                repeatCurrentTrack()
            }

            Config.REPEAT_MODE_ALL -> {
                handleInfiniteRepeat()
            }

            else -> {
                Log.w(TAG, "⚠️ Modo de repetición desconocido: $repeatMode - usando comportamiento normal")
                CoroutineScope(Dispatchers.Main).launch {
                    navigateToNext()
                }
            }
        }
    }

    /**
     * Repite la canción actual (modo "repeat one").
     */
    private fun repeatCurrentTrack() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val currentTrack = _currentTrack.value

                if (currentTrack != null) {
                    delay(500)

                    val success = loadAudioFromTrack(currentTrack)
                    if (!success) {
                        CoroutineScope(Dispatchers.Main).launch {
                            navigateToNext()
                        }
                    }
                } else {
                    CoroutineScope(Dispatchers.Main).launch {
                        navigateToNext()
                    }
                }
            } catch (_: Exception) {
                CoroutineScope(Dispatchers.Main).launch {
                    navigateToNext()
                }
            }
        }
    }

    /**
     * Maneja la repetición infinita de playlist (modo "repeat all").
     * Al final de la playlist, vuelve al inicio automáticamente.
     */
    private fun handleInfiniteRepeat() {
        CoroutineScope(Dispatchers.Main).launch {
            val playlist = _currentPlaylist.value
            val currentIndex = _currentTrackIndex.value
            if (playlist != null && currentIndex != null && currentIndex < playlist.size - 1) {
                navigateToNext()
            } else {
                restartPlaylistFromBeginning()
            }
        }
    }

    /**
     * Reinicia la playlist desde el primer track (para repetición infinita).
     */
    private suspend fun restartPlaylistFromBeginning() {
        // Priorizar cola si está activa
        if (_isQueueMode.value == true) {
            _isQueueMode.postValue(false)
            updateQueueState()
            return
        }

        // Reiniciar playlist desde el primer track
        val playlist = _currentPlaylist.value
        if (playlist != null && playlist.isNotEmpty()) {
            val firstTrack = playlist[0]
            _currentTrackIndex.postValue(0)
            _currentTrack.postValue(firstTrack)
            updateNavigationState()

            // Pequeña pausa antes de reiniciar
            delay(1000)

            // Cargar y reproducir el primer track
            val success = loadAudioFromTrack(firstTrack)
            if (success) {
                Log.d(TAG, "✅ Playlist reiniciada desde el inicio")
            } else {
                Log.e(TAG, "❌ Error reiniciando playlist")
            }
        } else {
            Log.w(TAG, "⚠️ No hay playlist para reiniciar")
        }
    }

    // === MÉTODOS DE DETECCIÓN DE CAMBIOS EN SALIDA DE AUDIO ===

    /**
     * Inicializa la detección de cambios en la salida de audio.
     * Configura el AudioManager y el BroadcastReceiver para detectar conexión/desconexión de auriculares.
     */
    private fun initializeAudioOutputDetection() {
        try {
            // Inicializar AudioManager
            audioManager = getApplication<Application>().getSystemService(Context.AUDIO_SERVICE) as? AudioManager

            // Detectar estado inicial de auriculares
            wasHeadsetConnected = isHeadsetConnected()

            // Configurar BroadcastReceiver para detectar cambios
            setupAudioOutputReceiver()

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error inicializando detección de salida de audio: ${e.message}", e)
        }
    }

    /**
     * Configura el BroadcastReceiver para detectar cambios en la salida de audio.
     */
    private fun setupAudioOutputReceiver() {
        audioOutputReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_HEADSET_PLUG -> {
                        handleHeadsetPlugEvent(intent)
                    }
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        handleAudioBecomingNoisy()
                    }
                    AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                        handleBluetoothScoStateChange(intent)
                    }
                    "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED" -> {
                        handleBluetoothA2dpStateChange(intent)
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getApplication<Application>().registerReceiver(audioOutputReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                getApplication<Application>().registerReceiver(audioOutputReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error registrando AudioOutputReceiver: ${e.message}", e)
        }
    }

    /**
     * Maneja el evento de conexión/desconexión de auriculares cableados.
     * @param intent Intent con información del evento
     */
    private fun handleHeadsetPlugEvent(intent: Intent) {
        val state = intent.getIntExtra("state", -1)
        intent.getStringExtra("name") ?: "Unknown"
        intent.getIntExtra("microphone", -1)

        val isConnected = state == 1
        handleAudioOutputChange(isConnected)
    }

    /**
     * Maneja el evento de audio becoming noisy (desconexión abrupta).
     */
    private fun handleAudioBecomingNoisy() {
        mainHandler.post {
            try {
                _exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error pausando por audio becoming noisy: ${e.message}", e)
            }
        }
    }

    /**
     * Maneja cambios en el estado de Bluetooth SCO.
     * @param intent Intent con información del estado
     */
    private fun handleBluetoothScoStateChange(intent: Intent) {
        val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
        when (state) {
            AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                handleAudioOutputChange(true)
            }
            AudioManager.SCO_AUDIO_STATE_DISCONNECTED -> {
                handleAudioOutputChange(false)
            }
        }
    }

    /**
     * Maneja cambios en el estado de Bluetooth A2DP.
     * @param intent Intent con información del estado
     */
    private fun handleBluetoothA2dpStateChange(intent: Intent) {
        val state = intent.getIntExtra("android.bluetooth.profile.extra.STATE", -1)
        when (state) {
            2 -> {
                handleAudioOutputChange(true)
            }
            0 -> {
                handleAudioOutputChange(false)
            }
        }
    }

    /**
     * Maneja cualquier cambio en la salida de audio.
     * @param isConnected Si el dispositivo está conectado o desconectado
     */
    private fun handleAudioOutputChange(isConnected: Boolean) {
        if (isConnected != wasHeadsetConnected) {
            wasHeadsetConnected = isConnected

            if (!isConnected) {
                switchToSpeakers()
            } else {
                switchToHeadphones()
            }
        }
    }

    /**
     * Cambia la salida de audio a los altavoces reconfigurando ExoPlayer.
     */
    private fun switchToSpeakers() {
        try {
            val wasPlaying = _exoPlayer?.isPlaying ?: false
            val currentPosition = _exoPlayer?.currentPosition ?: 0L

            _exoPlayer?.let { player ->
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()

                player.setAudioAttributes(audioAttributes, false)

                if (wasPlaying) {
                    player.pause()

                    mainHandler.postDelayed({
                        try {
                            player.seekTo(currentPosition)
                            player.play()
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error reanudando en altavoces: ${e.message}", e)
                        }
                    }, 100)
                }
            }

            audioManager?.let { am ->
                am.mode = AudioManager.MODE_NORMAL
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cambiando a altavoces: ${e.message}", e)
        }
    }

    /**
     * Cambia la salida de audio a los auriculares reconfigurando ExoPlayer.
     */
    private fun switchToHeadphones() {
        try {
            val wasPlaying = _exoPlayer?.isPlaying ?: false
            val currentPosition = _exoPlayer?.currentPosition ?: 0L

            _exoPlayer?.let { player ->
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()

                player.setAudioAttributes(audioAttributes, false)

                if (wasPlaying) {
                    player.pause()

                    mainHandler.postDelayed({
                        try {
                            player.seekTo(currentPosition)
                            player.play()
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error reanudando en auriculares: ${e.message}", e)
                        }
                    }, 100)
                }
            }

            audioManager?.let { am ->
                am.mode = AudioManager.MODE_NORMAL
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cambiando a auriculares: ${e.message}", e)
        }
    }

    /**
     * Verifica si hay auriculares conectados actualmente.
     * @return true si hay auriculares conectados, false en caso contrario
     */
    private fun isHeadsetConnected(): Boolean {
        return try {
            audioManager?.let { am ->
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val isWiredHeadsetOn = devices.any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                }
                val isBluetoothA2dpOn = devices.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
                val isBluetoothScoOn = devices.any {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                val hasHeadphones = isWiredHeadsetOn || isBluetoothA2dpOn || isBluetoothScoOn
                hasHeadphones
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verificando estado de auriculares: ${e.message}", e)
            false
        }
    }

    fun cancelWaitForSong() {
        waitForSongJob?.cancel()
        waitForSongJob = null
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

    fun removeFromQueue(index: Int) {
        val queue = _playbackQueue.value ?: return
        if (index in queue.indices) {
            queue.removeAt(index)
            _playbackQueue.postValue(queue)
            updateQueueState()
        }
    }
}
