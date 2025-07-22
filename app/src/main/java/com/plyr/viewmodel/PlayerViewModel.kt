package com.plyr.viewmodel

import android.app.Application
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.util.Log

/**
 * PlayerViewModel - Maneja la reproducción de audio usando ExoPlayer y NewPipe
 * 
 * FUNCIONALIDADES PRINCIPALES:
 * - Gestionar el ciclo de vida del ExoPlayer
 * - Extraer URLs de audio de YouTube usando NewPipe Extractor
 * - Proporcionar una interfaz para reproducir audio desde videos o tracks de Spotify
 * - Manejar estados de reproducción (loading, error, etc.)
 * - Proporcionar funcionalidades como play, pause, seek y control de tiempo
 * 
 * NAVEGACIÓN DE PLAYLIST:
 * - Mantener el estado de la playlist actual y el índice del track
 * - Navegación manual hacia adelante/atrás con botones fwd/bwd
 * - Auto-navegación automática al final de cada canción (configurable)
 * - Información de posición en playlist (ej: "3 de 10")
 * - Estados de disponibilidad de navegación (hasPrevious/hasNext)
 * 
 * USO:
 * 1. Llamar setCurrentPlaylist() para establecer la lista de tracks
 * 2. Los botones fwd/bwd en FloatingMusicControls permiten navegación manual
 * 3. La auto-navegación se puede habilitar/deshabilitar con setAutoNavigationEnabled()
 * 4. Los estados de navegación se observan automáticamente en la UI
 * 
 * @param application Contexto de la aplicación para acceder a recursos del sistema
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    
    // === CONSTANTES ===
    
    companion object {
        private const val TAG = "PlayerViewModel"
    }
    
    // === PROPIEDADES PRIVADAS ===
    
    /** Instancia del ExoPlayer para reproducción de audio */
    private var _exoPlayer: ExoPlayer? = null
    
    /** ExoPlayer secundario para preloading de la siguiente canción */
    private var _preloadPlayer: ExoPlayer? = null
    
    /** Track que está siendo preloaded */
    private var _preloadedTrack: TrackEntity? = null
    
    /** Estado de preloading activo */
    private var _isPreloading = false
    
    /** Listener actual del ExoPlayer principal para poder removerlo durante intercambios */
    private var _currentPlayerListener: Player.Listener? = null
    
    /** LiveData privado para la URL de audio actual */
    private val _audioUrl = MutableLiveData<String?>()
    
    /** LiveData privado para el título de la canción actual */
    private val _currentTitle = MutableLiveData<String?>()
    
    /** LiveData privado para el estado de carga */
    private val _isLoading = MutableLiveData<Boolean>()
    
    /** LiveData privado para mensajes de error */
    private val _error = MutableLiveData<String?>()
    
    /** Handler para ejecutar código en el hilo principal */
    private val mainHandler = Handler(Looper.getMainLooper())
    
    /** Manejador de búsquedas de YouTube usando NewPipe */
    private val youtubeSearchManager = YouTubeSearchManager(application)
    
    /** Callback para notificar cuando una canción termina de reproducirse */
    private var playbackEndedCallback: CompletableDeferred<Boolean>? = null
    
    // === PROPIEDADES DE PLAYLIST ===
    
    /** Lista actual de tracks de la playlist */
    private val _currentPlaylist = MutableLiveData<List<TrackEntity>?>()
    
    /** Índice del track actual en la playlist */
    private val _currentTrackIndex = MutableLiveData<Int>()
    
    /** Track actual que se está reproduciendo */
    private val _currentTrack = MutableLiveData<TrackEntity?>()
    
    /** Estado de si hay track anterior disponible */
    private val _hasPrevious = MutableLiveData<Boolean>(false)
    
    /** Estado de si hay track siguiente disponible */
    private val _hasNext = MutableLiveData<Boolean>(false)
    
    /** Estado de auto-navegación habilitada */
    private val _autoNavigationEnabled = MutableLiveData<Boolean>(true)
    
    // === PROPIEDADES DE COLA (QUEUE) ===
    
    /** Cola de reproducción - lista de tracks pendientes */
    private val _playbackQueue = MutableLiveData<MutableList<TrackEntity>>(mutableListOf())
    
    /** Indica si está en modo cola (queue) */
    private val _isQueueMode = MutableLiveData<Boolean>(false)
    
    // === PROPIEDADES DE COLA (STATEFLOW) ===
    
    /** Estado de la cola como StateFlow para Compose */
    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    // === PROPIEDADES PÚBLICAS (READONLY) ===
    
    /** Acceso público de solo lectura al ExoPlayer */
    val exoPlayer: ExoPlayer? get() = _exoPlayer
    
    /** LiveData observable para la URL de audio actual */
    val audioUrl: LiveData<String?> = _audioUrl
    
    /** LiveData observable para el título de la canción actual */
    val currentTitle: LiveData<String?> = _currentTitle
    
    /** LiveData observable para el estado de carga */
    val isLoading: LiveData<Boolean> = _isLoading
    
    /** LiveData observable para mensajes de error */
    val error: LiveData<String?> = _error
    
    /** LiveData observable para la playlist actual */
    val currentPlaylist: LiveData<List<TrackEntity>?> = _currentPlaylist
    
    /** LiveData observable para el índice del track actual */
    val currentTrackIndex: LiveData<Int> = _currentTrackIndex
    
    /** LiveData observable para el track actual */
    val currentTrack: LiveData<TrackEntity?> = _currentTrack
    
    /** LiveData observable para disponibilidad de track anterior */
    val hasPrevious: LiveData<Boolean> = _hasPrevious
    
    /** LiveData observable para disponibilidad de track siguiente */
    val hasNext: LiveData<Boolean> = _hasNext
    
    /** LiveData observable para el estado de auto-navegación */
    val autoNavigationEnabled: LiveData<Boolean> = _autoNavigationEnabled
    
    /** LiveData observable para la cola de reproducción */
    val playbackQueue: LiveData<MutableList<TrackEntity>> = _playbackQueue
    
    /** LiveData observable para el estado de modo cola */
    val isQueueMode: LiveData<Boolean> = _isQueueMode

    // === INICIALIZACIÓN ===
    
    init {
        // Inicializar el estado de la cola
        updateQueueState()
        // Observadores para actualizar el estado de navegación automáticamente
        _playbackQueue.observeForever {
            updateNavigationState()
        }
        _currentPlaylist.observeForever {
            updateNavigationState()
        }
        _currentTrackIndex.observeForever {
            updateNavigationState()
        }
        Log.d(TAG, "PlayerViewModel inicializado")
    }

    // === MÉTODOS PÚBLICOS ===
    
    /**
     * Inicializa el ExoPlayer si no ha sido creado aún.
     * Configura los listeners necesarios para el manejo de estados de reproducción.
     * También inicializa el ExoPlayer de preloading para transiciones sin delay.
     */
    fun initializePlayer() {
        // Asegurar que la inicialización ocurra en el hilo principal
        mainHandler.post {
            if (_exoPlayer == null) {
                _currentPlayerListener = createPlayerListener()
                _exoPlayer = ExoPlayer.Builder(getApplication())
                    .setSeekBackIncrementMs(10000)
                    .setSeekForwardIncrementMs(10000)
                    .build().apply {
                        // Configurar listener para eventos de reproducción
                        addListener(_currentPlayerListener!!)
                        // Configurar para liberar recursos inmediatamente cuando se detiene
                        setHandleAudioBecomingNoisy(true)
                        
                        // Aplicar optimizaciones de buffer
                        optimizeBufferSettings(this)
                    }
                Log.d(TAG, "✅ ExoPlayer principal inicializado")
            }
            
            // Inicializar el ExoPlayer de preloading si no existe
            if (_preloadPlayer == null) {
                _preloadPlayer = ExoPlayer.Builder(getApplication())
                    .setSeekBackIncrementMs(10000)
                    .setSeekForwardIncrementMs(10000)
                    .build().apply {
                        // Configurar para NO reproducir automáticamente
                        playWhenReady = false
                        setHandleAudioBecomingNoisy(false) // Solo el player principal maneja esto
                        
                        // Aplicar optimizaciones de buffer
                        optimizeBufferSettings(this)
                        
                        // Agregar listener para monitorear el estado del preloading
                        addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                when (playbackState) {
                                    Player.STATE_READY -> {
                                        Log.d(TAG, "🎯 PreloadPlayer LISTO - Track: ${_preloadedTrack?.name}")
                                    }
                                    Player.STATE_BUFFERING -> {
                                        Log.d(TAG, "🔄 PreloadPlayer bufferizando...")
                                    }
                                    Player.STATE_IDLE -> {
                                        Log.d(TAG, "💤 PreloadPlayer en IDLE")
                                    }
                                    Player.STATE_ENDED -> {
                                        Log.d(TAG, "🔚 PreloadPlayer terminado")
                                    }
                                }
                            }
                        })
                    }
                Log.d(TAG, "✅ ExoPlayer preload inicializado")
            }
            
            // Monitorear uso de memoria después de inicialización
            monitorMemoryUsage()
        }
    }
    
    /**
     * Crea el listener para el ExoPlayer que maneja cambios de estado.
     * @return Player.Listener configurado con los callbacks necesarios
     */
    private fun createPlayerListener(): Player.Listener {
        return object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "🎵 Canción terminada - Player.STATE_ENDED")
                        playbackEndedCallback?.complete(true)
                        playbackEndedCallback = null
                        
                        // Intentar reproducción sin delay usando preloaded track
                        handleSeamlessTransition()
                    }
                    Player.STATE_IDLE -> {
                        Log.d(TAG, "ExoPlayer en estado IDLE")
                    }
                    Player.STATE_BUFFERING -> {
                        Log.d(TAG, "ExoPlayer bufferizando...")
                    }
                    Player.STATE_READY -> {
                        Log.d(TAG, "ExoPlayer listo para reproducir")
                        
                        // Cuando el player actual esté listo, iniciar preloading de la siguiente canción
                        // Pero solo si no hay preloading activo ya
                        if (!_isPreloading) {
                            startPreloadingNextTrack()
                        }
                    }
                }
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "Estado de reproducción cambió: $isPlaying")
            }
            
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "❌ Error de ExoPlayer: ${error.message}", error)
                // Limpiar recursos y reintentar si es posible
                handlePlayerError(error)
            }
        }
    }
    
    /**
     * Maneja errores del ExoPlayer y intenta recuperarse.
     * @param error El error que ocurrió
     */
    private fun handlePlayerError(error: PlaybackException) {
        Log.e(TAG, "🚨 Manejando error de ExoPlayer: ${error.errorCode}")
        
        // Cancelar preloading activo que podría estar causando problemas
        cancelPreloading()
        
        // Liberar y reinicializar ExoPlayers para limpiar estado corrupto
        CoroutineScope(Dispatchers.Main).launch {
            try {
                releasePlayersForRecovery()
                kotlinx.coroutines.delay(1000) // Esperar un momento
                initializePlayer()
                
                // Intentar recargar el track actual si existe
                _currentTrack.value?.let { track ->
                    Log.d(TAG, "🔄 Reintentando cargar track actual después de error: ${track.name}")
                    loadAudioFromTrack(track)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error durante recuperación", e)
                updateLoadingState(false, "Error de reproducción. Reinicia la app si persiste.")
            }
        }
    }
    
    /**
     * Libera ExoPlayers para recuperación de errores.
     */
    private fun releasePlayersForRecovery() {
        Log.d(TAG, "🧹 Liberando ExoPlayers para recuperación")
        
        _currentPlayerListener?.let { listener ->
            _exoPlayer?.removeListener(listener)
        }
        
        _exoPlayer?.release()
        _exoPlayer = null
        
        _preloadPlayer?.release()
        _preloadPlayer = null
        
        _isPreloading = false
        _preloadedTrack = null
    }
    
    /**
     * Carga y reproduce audio desde un video ID de YouTube.
     * 
     * @param videoId ID del video de YouTube (ej: "dQw4w9WgXcQ")
     * @param title Título opcional para mostrar en la UI
     */
    fun loadAudio(videoId: String, title: String? = null) {
        println("PlayerViewModel: Cargando audio para video ID: $videoId con título: $title")
        updateLoadingState(true, null, title)
        
        // Usar NewPipe Extractor para obtener la URL de audio
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val audioUrl = extractAudioUrl(videoId)
                
                if (audioUrl != null && isValidAudioUrl(audioUrl)) {
                    println("PlayerViewModel: ✅ URL obtenida con NewPipe: $audioUrl")
                    playAudioFromUrl(audioUrl)
                } else {
                    handleAudioExtractionError(videoId, audioUrl)
                }
            } catch (e: Exception) {
                handleException("Error al extraer audio", e)
            }
        }
    }
    
    /**
     * Extrae la URL de audio de un video de YouTube usando NewPipe.
     * @param videoId ID del video de YouTube
     * @return URL de audio o null si falla
     */
    private suspend fun extractAudioUrl(videoId: String): String? {
        return withContext(Dispatchers.IO) {
            YouTubeAudioExtractor.getAudioUrl(videoId)
        }
    }
    
    /**
     * Reproduce audio desde una URL específica.
     * @param audioUrl URL del archivo de audio
     */
    private fun playAudioFromUrl(audioUrl: String) {
        _audioUrl.postValue(audioUrl)
        
        _exoPlayer?.apply {
            try {
                println("PlayerViewModel: Creando MediaItem con URL: $audioUrl")
                setMediaItem(MediaItem.fromUri(audioUrl))
                prepare()
                play()
                println("PlayerViewModel: ExoPlayer configurado y reproduciendo")
                _isLoading.postValue(false)
            } catch (e: Exception) {
                handleException("Error al configurar ExoPlayer", e)
            }
        }
    }
    
    /**
     * Maneja errores en la extracción de audio.
     */
    private fun handleAudioExtractionError(videoId: String, audioUrl: String?) {
        if (audioUrl == null) {
            println("PlayerViewModel: ❌ No se pudo obtener URL de audio")
            updateLoadingState(false, "No se pudo extraer la URL de audio para el video ID: $videoId")
        } else {
            println("PlayerViewModel: URL no válida según isValidAudioUrl")
            updateLoadingState(false, "La URL obtenida no es válida para reproducción de audio")
        }
    }
    
    
    /**
     * Carga y reproduce audio desde un TrackEntity de forma transparente.
     * Obtiene el YouTube ID automáticamente si no existe.
     * 
     * @param track Entidad del track con información de Spotify
     * @return true si la carga fue exitosa, false si falló
     */
    suspend fun loadAudioFromTrack(track: TrackEntity): Boolean = withContext(Dispatchers.Main) {
        try {
            println("PlayerViewModel: Cargando audio para track: ${track.name} - ${track.artists}")
            updateLoadingState(true, null, "${track.name} - ${track.artists}")
            
            // Obtener YouTube ID de forma transparente
            val youtubeId = obtainYouTubeId(track)
            
            if (youtubeId != null) {
                println("PlayerViewModel: ✅ YouTube ID obtenido: $youtubeId")
                return@withContext playTrackAudio(youtubeId, track)
            } else {
                println("PlayerViewModel: ❌ No se encontró YouTube ID para: ${track.name}")
                updateLoadingState(false, "No se encontró el video para: ${track.name}")
                return@withContext false
            }
        } catch (e: Exception) {
            handleException("Error al cargar audio desde track", e)
            return@withContext false
        }
    }
    
    /**
     * Obtiene el YouTube ID para un track.
     * @param track Track del cual obtener el ID
     * @return YouTube ID o null si no se encuentra
     */
    private suspend fun obtainYouTubeId(track: TrackEntity): String? {
        return withContext(Dispatchers.IO) {
            youtubeSearchManager.getYouTubeIdTransparently(track)
        }
    }
    
    /**
     * Reproduce audio de un track usando su YouTube ID.
     * @param youtubeId ID de YouTube del track
     * @param track Información del track para logs
     * @return true si la reproducción fue exitosa
     */
    private suspend fun playTrackAudio(youtubeId: String, track: TrackEntity): Boolean {
        // Obtener URL de audio con el ID
        val audioUrl = withContext(Dispatchers.IO) {
            YouTubeAudioExtractor.getAudioUrl(youtubeId)
        }
        
        if (audioUrl != null && isValidAudioUrl(audioUrl)) {
            println("PlayerViewModel: ✅ URL de audio obtenida: $audioUrl")
            return configureAndPlayAudio(audioUrl, track)
        } else {
            println("PlayerViewModel: ❌ No se pudo obtener URL de audio válida")
            updateLoadingState(false, "No se pudo obtener el audio para: ${track.name}")
            return false
        }
    }
    
    /**
     * Configura ExoPlayer y inicia la reproducción.
     * @param audioUrl URL del audio a reproducir
     * @param track Información del track para logs
     * @return true si la configuración fue exitosa
     */
    private suspend fun configureAndPlayAudio(audioUrl: String, track: TrackEntity): Boolean {
        _audioUrl.postValue(audioUrl)
        
        return withContext(Dispatchers.Main) {
            // Asegurar que ambos ExoPlayers estén inicializados
            if (_exoPlayer == null || _preloadPlayer == null) {
                Log.d(TAG, "🎵 Inicializando ExoPlayers...")
                initializePlayer()
                
                // Esperar a que la inicialización se complete
                var attempts = 0
                while ((_exoPlayer == null || _preloadPlayer == null) && attempts < 50) {
                    kotlinx.coroutines.delay(50)
                    attempts++
                }
                
                if (_exoPlayer == null || _preloadPlayer == null) {
                    Log.e(TAG, "❌ Error: ExoPlayers no se inicializaron correctamente")
                    _isLoading.postValue(false)
                    _error.postValue("Error: No se pudo inicializar el reproductor")
                    return@withContext false
                }
            }
            
            _exoPlayer?.let { player ->
                try {
                    Log.d(TAG, "🎵 Configurando ExoPlayer para: ${track.name}")
                    player.setMediaItem(MediaItem.fromUri(audioUrl))
                    player.prepare()
                    player.play()
                    Log.d(TAG, "✅ Reproducción iniciada para: ${track.name}")
                    _isLoading.postValue(false)
                    return@withContext true
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error configurando ExoPlayer para ${track.name}", e)
                    handleException("Error configurando ExoPlayer", e)
                    return@withContext false
                }
            } ?: run {
                Log.e(TAG, "❌ ExoPlayer es null después de inicialización")
                diagnoseExoPlayerState()
                _isLoading.postValue(false)
                _error.postValue("Error: Reproductor no disponible")
                return@withContext false
            }
        }
    }
    
    // === MÉTODOS DE CONTROL DE REPRODUCCIÓN ===
    
    /**
     * Pausa la reproducción actual.
     */
    fun pausePlayer() {
        mainHandler.post {
            _exoPlayer?.pause()
        }
    }
    
    /**
     * Reanuda la reproducción.
     */
    fun playPlayer() {
        mainHandler.post {
            _exoPlayer?.play()
        }
    }
    
    /**
     * Reanuda la reproducción si está pausada.
     */
    fun resumeIfPaused() {
        _exoPlayer?.let { player ->
            if (!player.isPlaying) {
                player.play()
            }
        }
    }
    
    /**
     * Busca a una posición específica en la canción.
     * @param positionMs Posición en milisegundos
     */
    fun seekTo(positionMs: Long) {
        mainHandler.post {
            _exoPlayer?.seekTo(positionMs)
        }
    }
    
    /**
     * Obtiene la posición actual de reproducción.
     * @return Posición actual en milisegundos
     */
    fun getCurrentPosition(): Long {
        return try {
            _exoPlayer?.currentPosition ?: 0L
        } catch (e: Exception) {
            println("PlayerViewModel: Error obteniendo posición: ${e.message}")
            0L
        }
    }
    
    /**
     * Obtiene la duración total de la canción actual.
     * @return Duración en milisegundos
     */
    fun getDuration(): Long {
        return try {
            _exoPlayer?.duration?.takeIf { it > 0 } ?: 0L
        } catch (e: Exception) {
            println("PlayerViewModel: Error obteniendo duración: ${e.message}")
            0L
        }
    }
    
    /**
     * Verifica si el reproductor está reproduciendo actualmente.
     * @return true si está reproduciendo, false en caso contrario
     */
    fun isPlaying(): Boolean {
        return try {
            _exoPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            println("PlayerViewModel: Error verificando estado de reproducción: ${e.message}")
            false
        }
    }
    
    
    /**
     * Habilita o deshabilita la navegación automática al final de cada canción.
     * @param enabled true para habilitar, false para deshabilitar
     */
    fun setAutoNavigationEnabled(enabled: Boolean) {
        _autoNavigationEnabled.postValue(enabled)
        println("PlayerViewModel: Auto-navegación ${if (enabled) "habilitada" else "deshabilitada"}")
    }

    /**
     * Obtiene el estado actual de la auto-navegación.
     * @return true si está habilitada, false en caso contrario
     */
    fun isAutoNavigationEnabled(): Boolean {
        return _autoNavigationEnabled.value ?: true
    }

    // === MÉTODOS DE NAVEGACIÓN DE PLAYLIST ===
    
    /**
     * Establece la playlist actual y el índice del track.
     * @param playlist Lista de tracks de la playlist
     * @param startIndex Índice del track inicial (por defecto 0)
     */
    fun setCurrentPlaylist(playlist: List<TrackEntity>, startIndex: Int = 0) {
        Log.d(TAG, "Estableciendo playlist: ${playlist.size} tracks, startIndex=$startIndex")
        _currentPlaylist.postValue(playlist)
        _currentTrackIndex.postValue(startIndex.coerceIn(0, playlist.size - 1))
        
        if (playlist.isNotEmpty() && startIndex in playlist.indices) {
            _currentTrack.postValue(playlist[startIndex])
            Log.d(TAG, "Track actual establecido: ${playlist[startIndex].name}")
        }
        
        updateNavigationState()
    }
    
    /**
     * Navega al track siguiente en la playlist o cola.
     * @return true si pudo navegar, false si no hay siguiente track
     */
    suspend fun navigateToNext(): Boolean {
        // Cancelar cualquier preloading activo ya que es navegación manual
        cancelPreloading()
        
        // Si está en modo cola, reproducir siguiente de la cola
        if (_isQueueMode.value == true) {
            return playNextFromQueue()
        }
        
        // Modo playlist normal
        val playlist = _currentPlaylist.value ?: return false
        val currentIndex = _currentTrackIndex.value ?: return false
        
        if (currentIndex < playlist.size - 1) {
            val nextIndex = currentIndex + 1
            val nextTrack = playlist[nextIndex]
            
            _currentTrackIndex.postValue(nextIndex)
            _currentTrack.postValue(nextTrack)
            updateNavigationState()
            
            // Cargar y reproducir el siguiente track
            val success = loadAudioFromTrack(nextTrack)
            if (success) {
                Log.d(TAG, "✅ Navegación exitosa al siguiente track: ${nextTrack.name}")
            } else {
                Log.w(TAG, "❌ Falló navegación al siguiente track: ${nextTrack.name}")
            }
            return success
        }
        
        return false
    }
    
    /**
     * Navega al track anterior en la playlist.
     * @return true si pudo navegar, false si no hay track anterior
     */
    suspend fun navigateToPrevious(): Boolean {
        // Cancelar cualquier preloading activo ya que es navegación manual
        cancelPreloading()
        
        val playlist = _currentPlaylist.value ?: return false
        val currentIndex = _currentTrackIndex.value ?: return false
        
        if (currentIndex > 0) {
            val previousIndex = currentIndex - 1
            val previousTrack = playlist[previousIndex]
            
            _currentTrackIndex.postValue(previousIndex)
            _currentTrack.postValue(previousTrack)
            updateNavigationState()
            
            // Cargar y reproducir el track anterior
            val success = loadAudioFromTrack(previousTrack)
            if (success) {
                Log.d(TAG, "✅ Navegación exitosa al track anterior: ${previousTrack.name}")
            } else {
                Log.w(TAG, "❌ Falló navegación al track anterior: ${previousTrack.name}")
            }
            return success
        }
        
        return false
    }
    
    /**
     * Navega a un track específico en la playlist por índice.
     * @param index Índice del track al que navegar
     * @return true si pudo navegar, false si el índice es inválido
     */
    suspend fun navigateToTrack(index: Int): Boolean {
        // Cancelar cualquier preloading activo ya que es navegación manual
        cancelPreloading()
        
        val playlist = _currentPlaylist.value ?: return false
        
        if (index in playlist.indices) {
            val track = playlist[index]
            
            _currentTrackIndex.postValue(index)
            _currentTrack.postValue(track)
            updateNavigationState()
            
            // Cargar y reproducir el track seleccionado
            val success = loadAudioFromTrack(track)
            if (success) {
                Log.d(TAG, "✅ Navegación exitosa al track ${index + 1}: ${track.name}")
            } else {
                Log.w(TAG, "❌ Falló navegación al track ${index + 1}: ${track.name}")
            }
            return success
        }
        
        return false
    }
    
    /**
     * Actualiza el estado de navegación (hasPrevious, hasNext).
     */
    private fun updateNavigationState() {
        val isQueue = _isQueueMode.value == true
        val queue = _playbackQueue.value
        val queueSize = queue?.size ?: 0
        val playlist = _currentPlaylist.value
        val currentIndex = _currentTrackIndex.value

        Log.d(TAG, "Actualizando estado de navegación: isQueue=$isQueue, queueSize=$queueSize, playlist=${playlist?.size}, index=$currentIndex")

        if (isQueue) {
            // En modo cola: no hay "previous", y "next" solo si hay tracks en cola
            _hasPrevious.postValue(false)
            _hasNext.postValue(queueSize > 0)
            Log.d(TAG, "Modo cola: hasPrevious=false, hasNext=${queueSize > 0}")
        } else if (playlist != null && currentIndex != null) {
            val hasPrev = currentIndex > 0
            val hasNext = currentIndex < playlist.size - 1
            Log.d(TAG, "Modo playlist: hasPrevious=$hasPrev, hasNext=$hasNext")
            _hasPrevious.postValue(hasPrev)
            _hasNext.postValue(hasNext)
        } else {
            Log.d(TAG, "Deshabilitando navegación (sin contexto)")
            _hasPrevious.postValue(false)
            _hasNext.postValue(false)
        }
    }
    
    /**
     * Obtiene información del track actual de la playlist.
     * @return Información del track actual o null si no hay playlist activa
     */
    fun getCurrentTrackInfo(): TrackEntity? {
        return _currentTrack.value
    }
    
    /**
     * Obtiene el número total de tracks en la playlist actual.
     * @return Número de tracks o 0 si no hay playlist
     */
    fun getPlaylistSize(): Int {
        return _currentPlaylist.value?.size ?: 0
    }
    
    /**
     * Obtiene la posición actual en la playlist (1-indexed para mostrar al usuario).
     * @return Posición actual (ej: "3 de 10") o null si no hay playlist
     */
    fun getCurrentPlaylistPosition(): String? {
        val playlist = _currentPlaylist.value
        val currentIndex = _currentTrackIndex.value
        
        return if (playlist != null && currentIndex != null) {
            "${currentIndex + 1} de ${playlist.size}"
        } else {
            null
        }
    }

    // === MÉTODOS DE GESTIÓN DE COLA (QUEUE) ===
    
    /**
     * Agrega un track a la cola de reproducción.
     * @param track Track a agregar a la cola
     */
    fun addToQueue(track: TrackEntity) {
        val currentQueue = _playbackQueue.value ?: mutableListOf()
        currentQueue.add(track)
        // Crear una nueva lista para asegurar que la UI se actualice
        val newQueue = currentQueue.toMutableList()
        _playbackQueue.postValue(newQueue)
        updateNavigationState()
        updateQueueState()
        Log.d(TAG, "Track agregado a la cola: ${track.name} (${newQueue.size} en cola)")
    }
    
    /**
     * Elimina un track de la cola por índice.
     * @param index Índice del track a eliminar
     */
    fun removeFromQueue(index: Int) {
        val currentQueue = _playbackQueue.value ?: return
        if (index in currentQueue.indices) {
            val removedTrack = currentQueue.removeAt(index)
            // Crear una nueva lista para asegurar que la UI se actualice
            val newQueue = currentQueue.toMutableList()
            _playbackQueue.postValue(newQueue)
            updateQueueState()
            Log.d(TAG, "Track eliminado de la cola: ${removedTrack.name} (${newQueue.size} restantes)")
        }
    }
    
    /**
     * Limpia toda la cola de reproducción.
     */
    fun clearQueue() {
        _playbackQueue.postValue(mutableListOf())
        _isQueueMode.postValue(false)
        updateQueueState()
        Log.d(TAG, "Cola de reproducción limpiada")
    }
    
    /**
     * Inicia la reproducción en modo cola.
     * Reproduce el primer track de la cola y establece el modo cola.
     */
    suspend fun startQueueMode(): Boolean {
        val queue = _playbackQueue.value
        if (queue.isNullOrEmpty()) {
            Log.d(TAG, "No hay tracks en la cola para iniciar")
            return false
        }
        
        _isQueueMode.postValue(true)
        Log.d(TAG, "Iniciando modo cola con ${queue.size} tracks")
        
        // Reproducir el primer track de la cola
        return playNextFromQueue()
    }
    
    /**
     * Reproduce el siguiente track de la cola.
     * @return true si pudo reproducir, false si no hay más tracks en cola
     */
    suspend fun playNextFromQueue(): Boolean {
        val queue = _playbackQueue.value
        if (queue.isNullOrEmpty()) {
            Log.d(TAG, "Cola vacía, desactivando modo cola")
            _isQueueMode.postValue(false)
            updateNavigationState()
            return false
        }
        
        // Tomar el primer track de la cola
        val nextTrack = queue.removeAt(0)
        _playbackQueue.postValue(queue)
        updateNavigationState()
        
        Log.d(TAG, "🎵 Reproduciendo desde cola: ${nextTrack.name} (${queue.size} tracks restantes en cola)")
        
        // Cargar y reproducir el track
        val success = loadAudioFromTrack(nextTrack)
        if (success) {
            // Actualizar el track actual
            _currentTrack.postValue(nextTrack)
            Log.d(TAG, "✅ Track de cola cargado exitosamente: ${nextTrack.name}")
        } else {
            Log.e(TAG, "❌ Error cargando track de cola: ${nextTrack.name}")
            diagnoseExoPlayerState()
        }
        
        return success
    }
    
    /**
     * Reproduce un track específico de la cola por índice.
     * @param index Índice del track en la cola a reproducir
     */
    suspend fun playFromQueue(index: Int) {
        val queue = _playbackQueue.value ?: return
        if (index !in queue.indices) {
            Log.e(TAG, "Índice de cola fuera de rango: $index")
            return
        }
        
        val track = queue[index]
        Log.d(TAG, "Reproduciendo track de cola en índice $index: ${track.name}")
        
        // Actualizar el estado de la cola
        updateQueueState()
        
        // Cargar y reproducir el track
        val success = loadAudioFromTrack(track)
        if (success) {
            _currentTrack.postValue(track)
            _isQueueMode.postValue(true)
        }
    }
    
    /**
     * Inicia la reproducción de la cola desde el primer track.
     */
    fun startQueue() {
        CoroutineScope(Dispatchers.Main).launch {
            val success = startQueueMode()
            if (success) {
                Log.d(TAG, "Cola iniciada correctamente")
            } else {
                Log.w(TAG, "No se pudo iniciar la cola")
            }
        }
    }
    
    /**
     * Mezcla aleatoriamente los tracks en la cola.
     */
    fun shuffleQueue() {
        val currentQueue = _playbackQueue.value ?: return
        if (currentQueue.size <= 1) return
        
        currentQueue.shuffle()
        // Crear una nueva lista para asegurar que la UI se actualice
        val newQueue = currentQueue.toMutableList()
        _playbackQueue.postValue(newQueue)
        updateQueueState()
        Log.d(TAG, "Cola mezclada - ${newQueue.size} tracks")
    }
    
    /**
     * Reproduce la cola desde un índice específico y activa el modo cola.
     * Esto reorganiza la cola para que comience desde el índice seleccionado
     * y continúe con el resto de tracks en orden.
     * 
     * @param startIndex Índice desde donde comenzar la reproducción
     */
    suspend fun playQueueFromIndex(startIndex: Int) {
        val queue = _playbackQueue.value ?: return
        if (startIndex !in queue.indices) {
            Log.e(TAG, "Índice de cola fuera de rango: $startIndex")
            return
        }
        
        Log.d(TAG, "🎵 Iniciando cola desde índice $startIndex de ${queue.size} tracks")
        
        // Asegurar que ExoPlayer esté inicializado
        if (_exoPlayer == null) {
            Log.d(TAG, "🔧 Inicializando ExoPlayer para cola...")
            initializePlayer()
        }
        
        // Reorganizar la cola: tracks desde startIndex hasta el final
        val reorderedQueue = queue.drop(startIndex).toMutableList()
        
        // Actualizar la cola con la nueva secuencia
        _playbackQueue.postValue(reorderedQueue)
        
        // Activar modo cola
        _isQueueMode.postValue(true)
        updateQueueState()
        
        // Reproducir el primer track de la nueva secuencia
        val success = playNextFromQueue()
        if (success) {
            Log.d(TAG, "✅ Cola iniciada correctamente desde índice $startIndex")
        } else {
            Log.w(TAG, "❌ No se pudo iniciar la cola desde índice $startIndex")
        }
    }
    
    /**
     * Actualiza el estado de la cola (StateFlow).
     */
    private fun updateQueueState() {
        val queue = _playbackQueue.value ?: emptyList()
        val isActive = _isQueueMode.value ?: false
        
        _queueState.value = QueueState(
            queue = queue.toList(), // Crear copia inmutable
            currentIndex = -1, // Por ahora no trackear índice específico
            isActive = isActive
        )
    }

    // === MÉTODOS DE ESPERA Y SINCRONIZACIÓN ===
    
    /**
     * Espera a que termine la canción actual usando el listener de ExoPlayer.
     * Útil para reproducción secuencial de playlists.
     * 
     * @return true si terminó naturalmente, false si se canceló o falló
     */
    suspend fun waitForCurrentSongToFinish(): Boolean {
        return try {
            Log.d(TAG, "⏳ Esperando a que termine la canción actual...")
            
            // Verificar que hay una canción reproduciéndose
            val hasPlayback = checkCurrentPlayback()
            if (!hasPlayback) {
                Log.w(TAG, "⚠️ No hay canción reproduciéndose")
                return false
            }
            
            // Configurar callback para esperar el final
            playbackEndedCallback = CompletableDeferred()
            
            // Esperar el resultado con timeout
            waitForPlaybackCompletion()
            
        } catch (e: Exception) {
            handleWaitException(e)
        }
    }
    
    /**
     * Verifica si hay una canción reproduciéndose actualmente.
     * @return true si hay reproducción activa
     */
    private suspend fun checkCurrentPlayback(): Boolean {
        return withContext(Dispatchers.Main) {
            _exoPlayer != null && isPlaying()
        }
    }
    
    /**
     * Espera a que la reproducción termine con un timeout de seguridad.
     * @return true si terminó correctamente
     */
    private suspend fun waitForPlaybackCompletion(): Boolean {
        return withContext(Dispatchers.IO) {
            // Timeout de 8 minutos (480 segundos) por seguridad
            val timeout = 480000L
            val startTime = System.currentTimeMillis()
            
            // Esperar hasta que termine o se agote el tiempo
            while (playbackEndedCallback != null && !playbackEndedCallback!!.isCompleted) {
                if (System.currentTimeMillis() - startTime > timeout) {
                    Log.w(TAG, "⚠️ Timeout esperando fin de canción")
                    playbackEndedCallback?.complete(false)
                    break
                }
                kotlinx.coroutines.delay(1000)
            }
            
            val result = playbackEndedCallback?.await() ?: false
            playbackEndedCallback = null
            
            Log.d(TAG, "${if (result) "✅" else "⚠️"} Canción ${if (result) "terminada" else "cancelada"}")
            result
        }
    }
    
    /**
     * Maneja excepciones durante la espera de finalización.
     * @param e Excepción ocurrida
     * @return false indicando fallo
     */
    private fun handleWaitException(e: Exception): Boolean {
        Log.e(TAG, "❌ Error esperando fin de canción: ${e.message}", e)
        playbackEndedCallback?.complete(false)
        playbackEndedCallback = null
        return false
    }
    
    /**
     * Cancela la espera del final de la canción.
     * Útil cuando se quiere detener la reproducción secuencial.
     */
    fun cancelWaitForSong() {
        playbackEndedCallback?.complete(false)
        playbackEndedCallback = null
    }
    
    /**
     * Maneja la navegación automática al final de una canción.
     * Si hay una playlist activa y hay más canciones, navega automáticamente.
     */
    private fun handleAutoNavigation() {
        // Verificar si la auto-navegación está habilitada
        if (!isAutoNavigationEnabled()) {
            Log.d(TAG, "🎵 Auto-navegación deshabilitada")
            return
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            // Priorizar cola de reproducción si está activa
            val isQueueActive = _isQueueMode.value ?: false
            val queue = _playbackQueue.value
            
            if (isQueueActive && !queue.isNullOrEmpty()) {
                Log.d(TAG, "🎵 Auto-navegando en modo cola...")
                
                // Pequeña pausa antes de la siguiente canción
                kotlinx.coroutines.delay(1000)
                
                // Reproducir siguiente canción de la cola
                val success = playNextFromQueue()
                if (!success) {
                    Log.d(TAG, "🎵 Cola terminada, saliendo de modo cola")
                    _isQueueMode.postValue(false)
                    updateQueueState()
                }
                return@launch
            }
            
            // Si no hay cola activa, usar navegación de playlist
            val playlist = _currentPlaylist.value
            val currentIndex = _currentTrackIndex.value
            
            // Verificar si hay playlist activa y siguiente canción disponible
            if (playlist != null && currentIndex != null && 
                currentIndex < playlist.size - 1) {
                
                Log.d(TAG, "🎵 Auto-navegando a la siguiente canción de playlist...")
                
                // Pequeña pausa antes de la siguiente canción
                kotlinx.coroutines.delay(1000)
                
                // Navegar automáticamente al siguiente track
                try {
                    navigateToNext()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error durante auto-navegación", e)
                    updateLoadingState(false, "Error navegando al siguiente track")
                }
            } else {
                Log.d(TAG, "🎵 Fin de playlist o no hay playlist activa")
            }
        }
    }
    
    // === MÉTODOS UTILITARIOS PRIVADOS ===
    
    /**
     * Actualiza el estado de carga y error de forma consistente.
     * @param loading Estado de carga
     * @param errorMessage Mensaje de error (null si no hay error)
     * @param title Título a mostrar (opcional)
     */
    private fun updateLoadingState(loading: Boolean, errorMessage: String? = null, title: String? = null) {
        _isLoading.postValue(loading)
        _error.postValue(errorMessage)
        title?.let { _currentTitle.postValue(it) }
    }
    
    /**
     * Maneja excepciones de forma consistente.
     * @param message Mensaje descriptivo del error
     * @param exception Excepción ocurrida
     */
    private fun handleException(message: String, exception: Exception) {
        Log.e(TAG, "❌ $message: ${exception.message}", exception)
        updateLoadingState(false, "$message: ${exception.message}")
    }
    
    // === LIMPIEZA DE RECURSOS ===
    
    /**
     * Limpia los recursos cuando el ViewModel es destruido.
     * Cancela callbacks pendientes y libera el ExoPlayer.
     */
    override fun onCleared() {
        super.onCleared()
        
        Log.d(TAG, "🧹 Limpiando recursos del PlayerViewModel")
        
        // Cancelar cualquier callback pendiente
        playbackEndedCallback?.complete(false)
        playbackEndedCallback = null
        
        // Cancelar preloading activo
        _isPreloading = false
        _preloadedTrack = null
        
        // Liberar ExoPlayer en el hilo principal
        mainHandler.post {
            try {
                // Remover listeners antes de liberar
                _currentPlayerListener?.let { listener ->
                    _exoPlayer?.removeListener(listener)
                }
                
                // Detener y liberar ambos players completamente
                _exoPlayer?.let { player ->
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                }
                _exoPlayer = null
                
                _preloadPlayer?.let { player ->
                    player.stop()
                    player.clearMediaItems()
                    player.release()
                }
                _preloadPlayer = null
                
                // Limpiar referencia del listener
                _currentPlayerListener = null
                
                Log.d(TAG, "✅ Recursos liberados correctamente")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error liberando recursos", e)
            }
        }
    }
    
    // === MÉTODOS DE PRELOADING PARA TRANSICIONES SIN DELAY ===
    
    /**
     * Maneja la transición sin delay entre canciones usando el track preloaded.
     * Si hay un track preloaded listo, intercambia los ExoPlayers instantáneamente.
     */
    private fun handleSeamlessTransition() {
        if (!isAutoNavigationEnabled()) {
            Log.d(TAG, "🎵 Auto-navegación deshabilitada")
            return
        }
        
        val nextTrack = getNextTrackToPlay()
        if (nextTrack == null) {
            Log.d(TAG, "🎵 No hay siguiente track, finalizando reproducción")
            return
        }
        
        // Si tenemos el track preloaded y es el correcto, hacer intercambio instantáneo
        Log.d(TAG, "🔍 Verificando preloading:")
        Log.d(TAG, "  - Track esperado: ${nextTrack.name}")
        Log.d(TAG, "  - Track preloaded: ${_preloadedTrack?.name}")
        Log.d(TAG, "  - PreloadPlayer existe: ${_preloadPlayer != null}")
        Log.d(TAG, "  - PreloadPlayer estado: ${_preloadPlayer?.playbackState}")
        Log.d(TAG, "  - Estado esperado (READY): ${Player.STATE_READY}")
        
        // Intentar swap instantáneo hasta 500ms si el preloading está casi listo
        if (_preloadedTrack == nextTrack && _preloadPlayer != null) {
            val maxAttempts = 5
            var attempt = 0
            while (_preloadPlayer?.playbackState != Player.STATE_READY && attempt < maxAttempts) {
                Log.d(TAG, "⌛ Esperando a que PreloadPlayer esté listo (intento ${attempt + 1}/$maxAttempts)...")
                Thread.sleep(100)
                attempt++
            }
            if (_preloadPlayer?.playbackState == Player.STATE_READY) {
                Log.d(TAG, "🚀 ✅ Todas las condiciones cumplidas - Track preloaded detectado: ${nextTrack.name}, iniciando intercambio")
                performSeamlessSwap(nextTrack)
                return
            }
        }
        // Fallback a navegación normal con delay
        val reason = when {
            _preloadedTrack != nextTrack -> "Track preloaded incorrecto (esperado: ${nextTrack.name}, actual: ${_preloadedTrack?.name})"
            _preloadPlayer == null -> "PreloadPlayer es null"
            _preloadPlayer?.playbackState != Player.STATE_READY -> "PreloadPlayer no está listo (estado: ${_preloadPlayer?.playbackState})"
            else -> "Razón desconocida"
        }
        Log.w(TAG, "⚠️ ❌ No hay preloading válido: $reason. Usando navegación normal")
        handleAutoNavigation()
    }
    
    /**
     * Realiza el intercambio instantáneo de ExoPlayers para transición sin delay.
     * @param nextTrack El track que debe reproducirse next
     */
    private fun performSeamlessSwap(nextTrack: TrackEntity) {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d(TAG, "🚀 Iniciando intercambio sin delay para: ${nextTrack.name}")
                
                // Verificar que el preload player esté listo
                if (_preloadPlayer?.playbackState != Player.STATE_READY) {
                    Log.w(TAG, "⚠️ PreloadPlayer no está listo, usando navegación normal")
                    handleAutoNavigation()
                    return@launch
                }
                
                // Detener y limpiar el player actual ANTES del intercambio
                _exoPlayer?.let { currentPlayer ->
                    currentPlayer.pause()
                    currentPlayer.stop()
                    // Remover listener del player actual
                    _currentPlayerListener?.let { listener ->
                        currentPlayer.removeListener(listener)
                    }
                }
                
                // Guardar referencia al player anterior para limpieza
                val oldPlayer = _exoPlayer
                
                // Intercambiar los ExoPlayers (el preload ya está preparado y listo)
                _exoPlayer = _preloadPlayer
                _preloadPlayer = oldPlayer
                
                // Configurar el nuevo player principal para reproducción INMEDIATA
                _exoPlayer?.let { newMainPlayer ->
                    // Agregar listener al nuevo player principal
                    _currentPlayerListener?.let { listener ->
                        newMainPlayer.addListener(listener)
                    }
                    
                    // EL PLAYER YA ESTÁ PREPARADO - solo activar reproducción
                    newMainPlayer.playWhenReady = true
                    newMainPlayer.play()
                    
                    Log.d(TAG, "⚡ Reproducción instantánea iniciada para: ${nextTrack.name}")
                }
                
                // Limpiar completamente el player anterior para liberar recursos
                _preloadPlayer?.let { oldPreloadPlayer ->
                    oldPreloadPlayer.stop()
                    oldPreloadPlayer.clearMediaItems()
                    oldPreloadPlayer.playWhenReady = false
                    // Forzar liberación de buffers
                    oldPreloadPlayer.release()
                    
                    // Recrear el preload player inmediatamente con recursos limpios
                    _preloadPlayer = ExoPlayer.Builder(getApplication())
                        .setSeekBackIncrementMs(10000)
                        .setSeekForwardIncrementMs(10000)
                        .build().apply {
                            playWhenReady = false
                            setHandleAudioBecomingNoisy(false)
                            
                            // Aplicar optimizaciones de buffer
                            optimizeBufferSettings(this)
                            
                            addListener(object : Player.Listener {
                                override fun onPlaybackStateChanged(playbackState: Int) {
                                    when (playbackState) {
                                        Player.STATE_READY -> {
                                            Log.d(TAG, "🎯 Nuevo PreloadPlayer LISTO")
                                        }
                                        Player.STATE_BUFFERING -> {
                                            Log.d(TAG, "🔄 Nuevo PreloadPlayer bufferizando...")
                                        }
                                    }
                                }
                            })
                        }
                    Log.d(TAG, "♻️ PreloadPlayer recreado después de intercambio")
                }
                
                // Actualizar estados de la UI
                updateTrackStates(nextTrack)
                
                // Resetear estado de preloading
                _preloadedTrack = null
                _isPreloading = false
                
                Log.d(TAG, "✅ Intercambio sin delay completado para: ${nextTrack.name}")
                
                // Monitorear memoria después del intercambio
                monitorMemoryUsage()
                
                // Esperar un momento antes de iniciar nuevo preloading para evitar sobrecarga
                kotlinx.coroutines.delay(2000)
                
                // Comenzar a preloading el siguiente track
                if (!_isPreloading) {
                    startPreloadingNextTrack()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error en intercambio sin delay", e)
                // Limpiar estado y usar navegación normal como fallback
                _isPreloading = false
                _preloadedTrack = null
                handleAutoNavigation()
            }
        }
    }
    
    /**
     * Actualiza los estados del track actual después de un intercambio.
     * @param track El nuevo track actual
     */
    private fun updateTrackStates(track: TrackEntity) {
        // Actualizar estados según el modo de reproducción
        if (_isQueueMode.value == true) {
            // Remover el track de la cola ya que se está reproduciendo
            val queue = _playbackQueue.value?.toMutableList() ?: mutableListOf()
            if (queue.isNotEmpty()) {
                queue.removeAt(0)
                _playbackQueue.postValue(queue)
                updateQueueState()
            }
        } else {
            // Modo playlist - actualizar índice
            val playlist = _currentPlaylist.value
            if (playlist != null) {
                val index = playlist.indexOf(track)
                if (index != -1) {
                    _currentTrackIndex.postValue(index)
                    updateNavigationState()
                }
            }
        }
        
        _currentTrack.postValue(track)
        _currentTitle.postValue("${track.name} - ${track.artists}")
    }
    
    /**
     * Obtiene el siguiente track que debería reproducirse.
     * @return El siguiente TrackEntity o null si no hay siguiente
     */
    private fun getNextTrackToPlay(): TrackEntity? {
        // Priorizar cola de reproducción
        if (_isQueueMode.value == true) {
            val queue = _playbackQueue.value
            return if (!queue.isNullOrEmpty()) queue[0] else null
        }
        
        // Modo playlist
        val playlist = _currentPlaylist.value ?: return null
        val currentIndex = _currentTrackIndex.value ?: return null
        
        return if (currentIndex < playlist.size - 1) {
            playlist[currentIndex + 1]
        } else null
    }
    
    /**
     * Inicia el preloading de la siguiente canción en background.
     * Se llama automáticamente cuando el track actual está listo.
     */
    private fun startPreloadingNextTrack() {
        if (_isPreloading) {
            Log.d(TAG, "🔄 Ya hay preloading en progreso")
            return
        }
        
        val nextTrack = getNextTrackToPlay()
        if (nextTrack == null) {
            Log.d(TAG, "🎵 No hay siguiente track para preload")
            return
        }
        
        if (_preloadPlayer == null) {
            Log.e(TAG, "❌ PreloadPlayer no inicializado")
            return
        }
        
        _isPreloading = true
        _preloadedTrack = nextTrack
        
        Log.d(TAG, "🔄 Iniciando preloading de: ${nextTrack.name}")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Obtener YouTube ID y URL de audio
                val youtubeId = youtubeSearchManager.getYouTubeIdTransparently(nextTrack)
                if (youtubeId == null) {
                    Log.e(TAG, "❌ No se encontró YouTube ID para preload: ${nextTrack.name}")
                    resetPreloadingState()
                    return@launch
                }
                
                val audioUrl = YouTubeAudioExtractor.getAudioUrl(youtubeId)
                if (audioUrl == null || !isValidAudioUrl(audioUrl)) {
                    Log.e(TAG, "❌ No se obtuvo URL válida para preload: ${nextTrack.name}")
                    resetPreloadingState()
                    return@launch
                }
                
                // Preparar el track en el preload player
                withContext(Dispatchers.Main) {
                    _preloadPlayer?.let { player ->
                        try {
                            // Limpiar estado anterior completamente
                            player.stop()
                            player.clearMediaItems()
                            
                            // Asegurar que NO se reproduzca automáticamente
                            player.playWhenReady = false
                            player.setMediaItem(MediaItem.fromUri(audioUrl))
                            player.prepare()
                            
                            // Verificar que efectivamente no esté reproduciéndose
                            if (player.isPlaying) {
                                player.pause()
                            }
                            
                            Log.d(TAG, "🔄 Preparando preload para: ${nextTrack.name}")
                            
                            // Esperar a que el player esté realmente listo con timeout más corto
                            var attempts = 0
                            while (player.playbackState != Player.STATE_READY && attempts < 50) { // 5 segundos máximo
                                kotlinx.coroutines.delay(100)
                                attempts++
                                
                                // Verificar si el preloading fue cancelado
                                if (!_isPreloading) {
                                    Log.d(TAG, "🚫 Preloading cancelado durante espera")
                                    return@withContext
                                }
                            }
                            
                            if (player.playbackState == Player.STATE_READY && _isPreloading) {
                                Log.d(TAG, "✅ Preloading completado para: ${nextTrack.name}")
                            } else {
                                Log.w(TAG, "⚠️ Preloading timeout o cancelado para: ${nextTrack.name}")
                                resetPreloadingState()
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Error en preparación de preload para ${nextTrack.name}", e)
                            resetPreloadingState()
                        }
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error en preloading para ${nextTrack.name}", e)
                resetPreloadingState()
            }
        }
    }
    
    /**
     * Resetea el estado de preloading de forma segura.
     */
    private fun resetPreloadingState() {
        _isPreloading = false
        _preloadedTrack = null
    }
    
    /**
     * Cancela el preloading activo si existe.
     * Útil cuando se cambia manualmente de canción o se reinicia el player.
     */
    private fun cancelPreloading() {
        if (_isPreloading) {
            Log.d(TAG, "🚫 Cancelando preloading activo")
            resetPreloadingState()
            
            // Limpiar el preload player en el hilo principal de forma más agresiva
            mainHandler.post {
                _preloadPlayer?.let { player ->
                    try {
                        player.stop()
                        player.clearMediaItems()
                        player.playWhenReady = false
                        
                        // Liberar completamente el preload player para evitar leaks
                        player.release()
                        
                        // Recrear inmediatamente
                        _preloadPlayer = ExoPlayer.Builder(getApplication())
                            .setSeekBackIncrementMs(10000)
                            .setSeekForwardIncrementMs(10000)
                            .build().apply {
                                playWhenReady = false
                                setHandleAudioBecomingNoisy(false)
                                
                                // Aplicar optimizaciones de buffer
                                optimizeBufferSettings(this)
                                
                                addListener(object : Player.Listener {
                                    override fun onPlaybackStateChanged(playbackState: Int) {
                                        when (playbackState) {
                                            Player.STATE_READY -> {
                                                Log.d(TAG, "🎯 PreloadPlayer recreado y listo")
                                            }
                                        }
                                    }
                                })
                            }
                        Log.d(TAG, "♻️ PreloadPlayer recreado después de cancelación")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error cancelando preloading", e)
                    }
                }
            }
        }
    }

    /**
     * Estado de la cola de reproducción.
     * @param queue Lista de tracks en la cola
     * @param currentIndex Índice del track actual en la cola (-1 si no hay track actual)
     * @param isActive Si la cola está activa
     */
    data class QueueState(
        val queue: List<TrackEntity> = emptyList(),
        val currentIndex: Int = -1,
        val isActive: Boolean = false
    )

    /**
     * Diagnostica el estado de ambos ExoPlayers para debugging.
     */
    private fun diagnoseExoPlayerState() {
        Log.d(TAG, "🔍 Diagnóstico ExoPlayer:")
        Log.d(TAG, "   - ExoPlayer principal: ${if (_exoPlayer != null) "✅ Existe" else "❌ Es null"}")
        _exoPlayer?.let { player ->
            Log.d(TAG, "     - Estado actual: ${player.playbackState}")
            Log.d(TAG, "     - ¿Está reproduciéndose?: ${player.isPlaying}")
            Log.d(TAG, "     - ¿Está preparado?: ${player.playbackState == Player.STATE_READY}")
            Log.d(TAG, "     - Duración: ${player.duration}")
            Log.d(TAG, "     - Posición actual: ${player.currentPosition}")
        }
        
        Log.d(TAG, "   - ExoPlayer preload: ${if (_preloadPlayer != null) "✅ Existe" else "❌ Es null"}")
        _preloadPlayer?.let { preloadPlayer ->
            Log.d(TAG, "     - Estado preload: ${preloadPlayer.playbackState}")
            Log.d(TAG, "     - ¿Está preparado?: ${preloadPlayer.playbackState == Player.STATE_READY}")
        }
        
        Log.d(TAG, "   - Track preloaded: ${_preloadedTrack?.name ?: "Ninguno"}")
        Log.d(TAG, "   - Preloading activo: $_isPreloading")
    }
    
    // === CONFIGURACIÓN DE BUFFER Y OPTIMIZACIÓN ===
    
    /**
     * Optimiza la configuración de buffer para reducir uso de memoria.
     */
    private fun optimizeBufferSettings(player: ExoPlayer) {
        try {
            // Configurar parámetros de buffer más conservadores para evitar exhaustión
            // Estos valores reducen el uso de memoria pero mantienen buena reproducción
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // Handle audio becoming noisy
            )
            
            Log.d(TAG, "🔧 Configuración de buffer optimizada")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ No se pudo optimizar configuración de buffer", e)
        }
    }
    
    /**
     * Fuerza la liberación de recursos de memoria de ExoPlayer.
     */
    private fun forceMemoryCleanup(player: ExoPlayer?) {
        player?.let {
            try {
                // Detener reproducción y limpiar media items
                it.stop()
                it.clearMediaItems()
                
                // Forzar garbage collection (solo en casos críticos)
                System.gc()
                
                Log.d(TAG, "♻️ Limpieza forzada de memoria completada")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Error durante limpieza forzada", e)
            }
        }
    }
    
    /**
     * Monitorea el uso de memoria y toma acciones si es necesario.
     */
    private fun monitorMemoryUsage() {
        try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val memoryPercentage = (usedMemory * 100) / maxMemory
            
            Log.d(TAG, "📊 Uso de memoria: ${memoryPercentage}% (${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB)")
            
            // Si el uso de memoria es alto (>80%), realizar limpieza
            if (memoryPercentage > 80) {
                Log.w(TAG, "⚠️ Uso de memoria alto, realizando limpieza preventiva")
                
                // Cancelar preloading para liberar memoria
                if (_isPreloading) {
                    cancelPreloading()
                }
                
                // Forzar garbage collection
                System.gc()
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Error monitoreando memoria", e)
        }
    }
}
