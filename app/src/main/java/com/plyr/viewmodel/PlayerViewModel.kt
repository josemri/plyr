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

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    
    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer? get() = _exoPlayer
    
    private val _audioUrl = MutableLiveData<String?>()
    val audioUrl: LiveData<String?> = _audioUrl
    
    private val _currentTitle = MutableLiveData<String?>()
    val currentTitle: LiveData<String?> = _currentTitle
    
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading
    
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    
    // Handler para ejecutar código en el hilo principal
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Agregar YouTubeSearchManager para búsqueda transparente
    private val youtubeSearchManager = YouTubeSearchManager(application)
    
    // Para notificar cuando una canción termina
    private var playbackEndedCallback: CompletableDeferred<Boolean>? = null
    
    fun initializePlayer() {
        if (_exoPlayer == null) {
            _exoPlayer = ExoPlayer.Builder(getApplication()).build().apply {
                // Agregar listener para detectar cuando termina la reproducción
                addListener(object : Player.Listener {
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
                })
            }
        }
    }
    
    fun loadAudio(videoId: String, title: String? = null) {
        println("PlayerViewModel: Cargando audio para video ID: $videoId con título: $title")
        _isLoading.postValue(true)
        _error.postValue(null)
        _currentTitle.postValue(title)
        
        // Usar NewPipe Extractor en lugar del backend
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val audioUrl = withContext(Dispatchers.IO) {
                    YouTubeAudioExtractor.getAudioUrl(videoId)
                }
                
                if (audioUrl != null) {
                    println("PlayerViewModel: ✅ URL obtenida con NewPipe: $audioUrl")
                    
                    if (isValidAudioUrl(audioUrl)) {
                        println("PlayerViewModel: URL válida, configurando ExoPlayer")
                        _audioUrl.postValue(audioUrl)
                        
                        _exoPlayer?.apply {
                            try {
                                println("PlayerViewModel: Creando MediaItem con URL: $audioUrl")
                                setMediaItem(MediaItem.fromUri(audioUrl))
                                prepare()
                                play()
                                println("PlayerViewModel: ExoPlayer configurado y reproduciendo")
                            } catch (e: Exception) {
                                println("PlayerViewModel: Error al configurar ExoPlayer: ${e.message}")
                                _error.postValue("Error al reproducir: ${e.message}")
                            }
                        }
                        
                        _isLoading.postValue(false)
                    } else {
                        println("PlayerViewModel: URL no válida según isValidAudioUrl")
                        _isLoading.postValue(false)
                        _error.postValue("La URL obtenida no es válida para reproducción de audio")
                    }
                } else {
                    println("PlayerViewModel: ❌ No se pudo obtener URL de audio")
                    _isLoading.postValue(false)
                    _error.postValue("No se pudo extraer la URL de audio para el video ID: $videoId")
                }
            } catch (e: Exception) {
                println("PlayerViewModel: ❌ Error al extraer audio: ${e.message}")
                _isLoading.postValue(false)
                _error.postValue("Error al extraer audio: ${e.message}")
            }
        }
    }
    
    /**
     * Cargar audio desde un TrackEntity de forma transparente
     * Obtiene el YouTube ID automáticamente si no existe
     * @return true si la carga fue exitosa, false si falló
     */
    suspend fun loadAudioFromTrack(track: TrackEntity): Boolean = withContext(Dispatchers.Main) {
        try {
            println("PlayerViewModel: Cargando audio para track: ${track.name} - ${track.artists}")
            _isLoading.postValue(true)
            _error.postValue(null)
            _currentTitle.postValue("${track.name} - ${track.artists}")
            
            // Obtener YouTube ID de forma transparente
            val youtubeId = withContext(Dispatchers.IO) {
                youtubeSearchManager.getYouTubeIdTransparently(track)
            }
            
            if (youtubeId != null) {
                println("PlayerViewModel: ✅ YouTube ID obtenido: $youtubeId")
                
                // Obtener URL de audio con el ID
                val audioUrl = withContext(Dispatchers.IO) {
                    YouTubeAudioExtractor.getAudioUrl(youtubeId)
                }
                
                if (audioUrl != null && isValidAudioUrl(audioUrl)) {
                    println("PlayerViewModel: ✅ URL de audio obtenida: $audioUrl")
                    _audioUrl.postValue(audioUrl)
                    
                    _exoPlayer?.apply {
                        try {
                            setMediaItem(MediaItem.fromUri(audioUrl))
                            prepare()
                            play()
                            println("PlayerViewModel: ✅ Reproducción iniciada para: ${track.name}")
                            _isLoading.postValue(false)
                            return@withContext true
                        } catch (e: Exception) {
                            println("PlayerViewModel: ❌ Error configurando ExoPlayer: ${e.message}")
                            _error.postValue("Error al reproducir: ${e.message}")
                            _isLoading.postValue(false)
                            return@withContext false
                        }
                    }
                    
                    _isLoading.postValue(false)
                    return@withContext false
                } else {
                    println("PlayerViewModel: ❌ No se pudo obtener URL de audio válida")
                    _isLoading.postValue(false)
                    _error.postValue("No se pudo obtener el audio para: ${track.name}")
                    return@withContext false
                }
            } else {
                println("PlayerViewModel: ❌ No se encontró YouTube ID para: ${track.name}")
                _isLoading.postValue(false)
                _error.postValue("No se encontró el video para: ${track.name}")
                return@withContext false
            }
        } catch (e: Exception) {
            println("PlayerViewModel: ❌ Error cargando audio desde track: ${e.message}")
            _isLoading.postValue(false)
            _error.postValue("Error al cargar audio: ${e.message}")
            return@withContext false
        }
    }
    
    fun pausePlayer() {
        mainHandler.post {
            _exoPlayer?.pause()
        }
    }
    
    fun playPlayer() {
        mainHandler.post {
            _exoPlayer?.play()
        }
    }
    
    fun seekTo(positionMs: Long) {
        mainHandler.post {
            _exoPlayer?.seekTo(positionMs)
        }
    }
    
    fun getCurrentPosition(): Long {
        return try {
            _exoPlayer?.currentPosition ?: 0L
        } catch (e: Exception) {
            println("PlayerViewModel: Error obteniendo posición: ${e.message}")
            0L
        }
    }
    
    fun getDuration(): Long {
        return try {
            _exoPlayer?.duration?.takeIf { it > 0 } ?: 0L
        } catch (e: Exception) {
            println("PlayerViewModel: Error obteniendo duración: ${e.message}")
            0L
        }
    }
    
    fun isPlaying(): Boolean {
        return try {
            _exoPlayer?.isPlaying ?: false
        } catch (e: Exception) {
            println("PlayerViewModel: Error verificando estado de reproducción: ${e.message}")
            false
        }
    }
    
    /**
     * Espera a que termine la canción actual usando el listener de ExoPlayer
     * Retorna true si terminó naturalmente, false si se canceló
     */
    suspend fun waitForCurrentSongToFinish(): Boolean {
        return try {
            println("PlayerViewModel: ⏳ Esperando a que termine la canción actual...")
            
            // Verificar desde el hilo principal que hay una canción reproduciéndose
            val hasPlayback = withContext(Dispatchers.Main) {
                _exoPlayer != null && isPlaying()
            }
            
            if (!hasPlayback) {
                println("PlayerViewModel: ⚠️ No hay canción reproduciéndose")
                return false
            }
            
            // Crear un CompletableDeferred para esperar el final
            playbackEndedCallback = CompletableDeferred()
            
            // Esperar en hilo IO para no bloquear UI
            withContext(Dispatchers.IO) {
                // Esperar máximo 8 minutos (480 segundos) por si algo falla
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
            
        } catch (e: Exception) {
            println("PlayerViewModel: ❌ Error esperando fin de canción: ${e.message}")
            playbackEndedCallback?.complete(false)
            playbackEndedCallback = null
            false
        }
    }
    
    /**
     * Cancela la espera del final de la canción
     */
    fun cancelWaitForSong() {
        playbackEndedCallback?.complete(false)
        playbackEndedCallback = null
    }
    
    override fun onCleared() {
        super.onCleared()
        playbackEndedCallback?.complete(false)
        playbackEndedCallback = null
        mainHandler.post {
            _exoPlayer?.release()
            _exoPlayer = null
        }
    }
}
