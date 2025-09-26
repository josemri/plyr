package com.plyr.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.plyr.PlyrApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MusicService : Service() {
    private var playlist: List<String> = emptyList()
    private var currentIndex: Int = 0

    companion object {
        private const val TAG = "MusicService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MusicService creado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val audioUrl = intent?.getStringExtra("AUDIO_URL")
        if (audioUrl != null) playAudio(audioUrl)
        return START_NOT_STICKY
    }

    fun playAudio(audioUrl: String) {
        Log.d(TAG, "playAudio llamado con: $audioUrl")
        val plyr = (application as PlyrApp).playerViewModel
        try {
            plyr.loadAudio(audioUrl, "Audio Track")
            CoroutineScope(Dispatchers.Main).launch {
                val player = plyr.getPlayer()
                player?.playWhenReady = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir audio: ${e.message}", e)
        }
    }

    fun playPlaylist(urls: List<String>, startIndex: Int = 0) {
        playlist = urls
        currentIndex = startIndex.coerceIn(0, urls.size - 1)
        if (playlist.isNotEmpty()) {
            Log.d(TAG, "Playing playlist: ${playlist.size} tracks, starting at index $currentIndex")
            playAudio(playlist[currentIndex])
        } else {
            Log.w(TAG, "Attempted to play empty playlist")
        }
    }

    private fun handleTrackEnded() {
        if (playlist.isNotEmpty() && currentIndex < playlist.size - 1) {
            currentIndex++
            val nextUrl = playlist[currentIndex]
            Log.d(TAG, "Track ended, playing next: $nextUrl")
            playAudio(nextUrl)
        } else {
            Log.d(TAG, "Playlist ended")
        }
    }

    private fun handlePlayerError(error: Exception) {
        Log.e(TAG, "Error del player: ${error.message}")
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
    private val binder = MusicBinder()
    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        Log.d(TAG, "MusicService destruido")
        super.onDestroy()
    }
}
