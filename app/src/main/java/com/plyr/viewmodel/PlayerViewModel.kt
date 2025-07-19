package com.plyr.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.plyr.network.AudioRepository
import com.plyr.utils.isValidAudioUrl
import android.os.Handler
import android.os.Looper

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
        
        AudioRepository.requestAudioUrl(videoId) { result ->
            // Ejecutar en el hilo principal para poder usar ExoPlayer
            mainHandler.post {
                _isLoading.postValue(false)
                println("PlayerViewModel: Resultado del repositorio: $result")
                
                if (result != null) {
                    println("PlayerViewModel: Validando URL: $result")
                    if (isValidAudioUrl(result)) {
                        println("PlayerViewModel: URL válida, configurando ExoPlayer")
                        _audioUrl.postValue(result)
                        _exoPlayer?.apply {
                            try {
                                println("PlayerViewModel: Creando MediaItem con URL: $result")
                                setMediaItem(MediaItem.fromUri(result))
                                prepare()
                                play()
                                println("PlayerViewModel: ExoPlayer configurado y reproduciendo")
                            } catch (e: Exception) {
                                println("PlayerViewModel: Error al configurar ExoPlayer: ${e.message}")
                                _error.postValue("Error al reproducir: ${e.message}")
                            }
                        }
                    } else {
                        println("PlayerViewModel: URL no válida según isValidAudioUrl")
                        _error.postValue("La URL recibida no es válida para reproducción de audio: $result")
                    }
                } else {
                    println("PlayerViewModel: No se recibió URL del servidor")
                    _error.postValue("No se pudo obtener una URL de audio para el video ID: $videoId")
                }
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
