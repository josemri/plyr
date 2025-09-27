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

import android.app.Notification
import android.app.PendingIntent
import androidx.annotation.OptIn
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.plyr.MainActivity


class MusicService : Service() {
    private var CHANNEL_ID = "playback_channel"
    private val NOTIFICATION_ID = 1
    private var playlist: List<String> = emptyList()
    private var currentIndex: Int = 0
    lateinit var mediaSession: MediaSession
    companion object {
        private const val TAG = "MusicService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MusicService creado")
        // Crear canal de notificación
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Reproducción")
                .build()
        )
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

    @OptIn(UnstableApi::class)
    private fun createNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Reproduciendo")
            .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession))
            .setOngoing(true)
            .build()

    fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        Log.d(TAG, "MusicService destruido")
        mediaSession.release()
        super.onDestroy()
    }

    fun startMediaSession(player: ExoPlayer){
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        // Iniciar servicio en primer plano
        startForeground(NOTIFICATION_ID, createNotification())
    }
}
