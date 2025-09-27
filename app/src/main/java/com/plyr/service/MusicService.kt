package com.plyr.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import android.app.Notification
import android.app.PendingIntent
import androidx.annotation.OptIn
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.plyr.MainActivity
import com.plyr.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MusicService : Service() {
    private var CHANNEL_ID = "playback_channel"
    private val NOTIFICATION_ID = 1
    lateinit var mediaSession: MediaSession
    var playerViewModel: PlayerViewModel? = null
    companion object {
        private const val TAG = "MusicService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MusicService creado")
        // Crear canal de notificación
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("plyr")
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val audioUrl = intent?.getStringExtra("AUDIO_URL")
        return START_NOT_STICKY
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

    fun updateNotification() {
        if (::mediaSession.isInitialized) {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }
}
