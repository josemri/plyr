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

/**
 * PlayerViewModel - Maneja la reproducción de audio usando ExoPlayer y NewPipe
 * 
 * Esta clase es responsable de:
 * - Gestionar el ciclo de vida del ExoPlayer
 * - Extraer URLs de audio de YouTube usando NewPipe Extractor
 * - Proporcionar una interfaz para reproducir audio desde videos o tracks de Spotify
 * - Manejar estados de reproducción (loading, error, etc.)
 * - Proporcionar funcionalidades como play, pause, seek y control de tiempo
 * 
 * @param application Contexto de la aplicación para acceder a recursos del sistema
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    
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
            _exoPlayer?.apply {
                try {
                    setMediaItem(MediaItem.fromUri(audioUrl))
                    prepare()
                    play()
                    println("PlayerViewModel: ✅ Reproducción iniciada para: ${track.name}")
                    _isLoading.postValue(false)
                    return@withContext true
                } catch (e: Exception) {
                    handleException("Error configurando ExoPlayer", e)
                    return@withContext false
                }
            }
            
            _isLoading.postValue(false)
            return@withContext false
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
        println("PlayerViewModel: ❌ $message: ${exception.message}")
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
}
