package com.plyr.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
        Log.d(TAG, "PlayerViewModel inicializado")
    }

    // === MÉTODOS PÚBLICOS ===
    
    /**
     * Inicializa el ExoPlayer si no ha sido creado aún.
     * Configura los listeners necesarios para el manejo de estados de reproducción.
     */
    fun initializePlayer() {
        if (_exoPlayer == null) {
            _exoPlayer = ExoPlayer.Builder(getApplication()).build().apply {
                // Configurar listener para eventos de reproducción
                addListener(createPlayerListener())
            }
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
                        println("PlayerViewModel: 🎵 Canción terminada - Player.STATE_ENDED")
                        playbackEndedCallback?.complete(true)
                        playbackEndedCallback = null
                        
                        // Auto-navegación a la siguiente canción si hay playlist activa
                        handleAutoNavigation()
                    }
                    Player.STATE_IDLE -> {
                        println("PlayerViewModel: ExoPlayer en estado IDLE")
                    }
                    Player.STATE_BUFFERING -> {
                        println("PlayerViewModel: ExoPlayer bufferizando...")
                    }
                    Player.STATE_READY -> {
                        println("PlayerViewModel: ExoPlayer listo para reproducir")
                    }
                }
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                println("PlayerViewModel: Estado de reproducción cambió: $isPlaying")
            }
        }
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
            // Verificar que ExoPlayer esté inicializado
            if (_exoPlayer == null) {
                Log.e(TAG, "❌ ExoPlayer es null, intentando inicializar...")
                initializePlayer()
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
                Log.e(TAG, "❌ ExoPlayer sigue siendo null después de inicialización")
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
                println("PlayerViewModel: ✅ Navegación exitosa al siguiente track: ${nextTrack.name}")
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
                println("PlayerViewModel: ✅ Navegación exitosa al track anterior: ${previousTrack.name}")
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
        val playlist = _currentPlaylist.value ?: return false
        
        if (index in playlist.indices) {
            val track = playlist[index]
            
            _currentTrackIndex.postValue(index)
            _currentTrack.postValue(track)
            updateNavigationState()
            
            // Cargar y reproducir el track seleccionado
            val success = loadAudioFromTrack(track)
            if (success) {
                println("PlayerViewModel: ✅ Navegación exitosa al track ${index + 1}: ${track.name}")
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
        val queueSize = _playbackQueue.value?.size ?: 0
        val playlist = _currentPlaylist.value
        val currentIndex = _currentTrackIndex.value
        
        Log.d(TAG, "Actualizando estado de navegación: isQueue=$isQueue, queueSize=$queueSize, playlist=${playlist?.size}, index=$currentIndex")
        
        if (isQueue) {
            // En modo cola: no hay "previous", pero sí "next" si hay tracks en cola
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
            println("PlayerViewModel: ⏳ Esperando a que termine la canción actual...")
            
            // Verificar que hay una canción reproduciéndose
            val hasPlayback = checkCurrentPlayback()
            if (!hasPlayback) {
                println("PlayerViewModel: ⚠️ No hay canción reproduciéndose")
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
                    println("PlayerViewModel: ⚠️ Timeout esperando fin de canción")
                    playbackEndedCallback?.complete(false)
                    break
                }
                kotlinx.coroutines.delay(1000)
            }
            
            val result = playbackEndedCallback?.await() ?: false
            playbackEndedCallback = null
            
            println("PlayerViewModel: ${if (result) "✅" else "⚠️"} Canción ${if (result) "terminada" else "cancelada"}")
            result
        }
    }
    
    /**
     * Maneja excepciones durante la espera de finalización.
     * @param e Excepción ocurrida
     * @return false indicando fallo
     */
    private fun handleWaitException(e: Exception): Boolean {
        println("PlayerViewModel: ❌ Error esperando fin de canción: ${e.message}")
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
            println("PlayerViewModel: 🎵 Auto-navegación deshabilitada")
            return
        }
        
        CoroutineScope(Dispatchers.Main).launch {
            // Priorizar cola de reproducción si está activa
            val isQueueActive = _isQueueMode.value ?: false
            val queue = _playbackQueue.value
            
            if (isQueueActive && !queue.isNullOrEmpty()) {
                println("PlayerViewModel: 🎵 Auto-navegando en modo cola...")
                
                // Pequeña pausa antes de la siguiente canción
                kotlinx.coroutines.delay(1000)
                
                // Reproducir siguiente canción de la cola
                val success = playNextFromQueue()
                if (!success) {
                    println("PlayerViewModel: 🎵 Cola terminada, saliendo de modo cola")
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
                
                println("PlayerViewModel: 🎵 Auto-navegando a la siguiente canción de playlist...")
                
                // Pequeña pausa antes de la siguiente canción
                kotlinx.coroutines.delay(1000)
                
                // Navegar automáticamente al siguiente track
                navigateToNext()
            } else {
                println("PlayerViewModel: 🎵 Fin de playlist o no hay playlist activa")
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
        
        // Cancelar cualquier callback pendiente
        playbackEndedCallback?.complete(false)
        playbackEndedCallback = null
        
        // Liberar ExoPlayer en el hilo principal
        mainHandler.post {
            _exoPlayer?.release()
            _exoPlayer = null
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
     * Diagnostica el estado del ExoPlayer para debugging.
     */
    private fun diagnoseExoPlayerState() {
        Log.d(TAG, "🔍 Diagnóstico ExoPlayer:")
        Log.d(TAG, "   - ExoPlayer instancia: ${if (_exoPlayer != null) "✅ Existe" else "❌ Es null"}")
        _exoPlayer?.let { player ->
            Log.d(TAG, "   - Estado actual: ${player.playbackState}")
            Log.d(TAG, "   - ¿Está reproduciéndose?: ${player.isPlaying}")
            Log.d(TAG, "   - ¿Está preparado?: ${player.playbackState == Player.STATE_READY}")
            Log.d(TAG, "   - Duración: ${player.duration}")
            Log.d(TAG, "   - Posición actual: ${player.currentPosition}")
        }
    }
}
