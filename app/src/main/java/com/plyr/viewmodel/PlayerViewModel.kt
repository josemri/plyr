package com.plyr.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
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
    
    fun initializePlayer() {
        if (_exoPlayer == null) {
            _exoPlayer = ExoPlayer.Builder(getApplication()).build()
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
     */
    fun loadAudioFromTrack(track: TrackEntity) {
        println("PlayerViewModel: Cargando audio para track: ${track.name} - ${track.artists}")
        _isLoading.postValue(true)
        _error.postValue(null)
        _currentTitle.postValue("${track.name} - ${track.artists}")
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
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
                            } catch (e: Exception) {
                                println("PlayerViewModel: ❌ Error configurando ExoPlayer: ${e.message}")
                                _error.postValue("Error al reproducir: ${e.message}")
                            }
                        }
                        
                        _isLoading.postValue(false)
                    } else {
                        println("PlayerViewModel: ❌ No se pudo obtener URL de audio válida")
                        _isLoading.postValue(false)
                        _error.postValue("No se pudo obtener el audio para: ${track.name}")
                    }
                } else {
                    println("PlayerViewModel: ❌ No se encontró YouTube ID para: ${track.name}")
                    _isLoading.postValue(false)
                    _error.postValue("No se encontró el video para: ${track.name}")
                }
            } catch (e: Exception) {
                println("PlayerViewModel: ❌ Error cargando audio desde track: ${e.message}")
                _isLoading.postValue(false)
                _error.postValue("Error al cargar audio: ${e.message}")
            }
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
        return _exoPlayer?.currentPosition ?: 0L
    }
    
    fun getDuration(): Long {
        return _exoPlayer?.duration?.takeIf { it > 0 } ?: 0L
    }
    
    fun isPlaying(): Boolean {
        return _exoPlayer?.isPlaying ?: false
    }
    
    override fun onCleared() {
        super.onCleared()
        mainHandler.post {
            _exoPlayer?.release()
            _exoPlayer = null
        }
    }
}
