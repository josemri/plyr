package com.plyr.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
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
import androidx.media3.common.Player

class MusicService : Service() {
    private val CHANNEL_ID = "plyr_playback"
    private val NOTIFICATION_ID = 1
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Reproducción")
                .build()
        )
    }

    inner class MusicBinder : Binder() {
        fun getService() = this@MusicService
    }

    override fun onBind(intent: Intent): IBinder = MusicBinder()

    @OptIn(UnstableApi::class)
    fun setupMediaSession(player: ExoPlayer) {
        if (mediaSession != null) {
            updateNotification(player)
            return
        }

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                updateNotification(player)
            }
        })

        startForeground(NOTIFICATION_ID, createNotification(player))
    }

    @OptIn(UnstableApi::class)
    private fun createNotification(player: ExoPlayer): Notification {
        val item = player.currentMediaItem
        val title = item?.mediaMetadata?.title?.toString() ?: "Plyr"
        val artist = item?.mediaMetadata?.artist?.toString() ?: "Reproduciendo"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(artist)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession!!))
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(player: ExoPlayer) {
        mediaSession?.let { startForeground(NOTIFICATION_ID, createNotification(player)) }
    }

    override fun onDestroy() {
        mediaSession?.release()
        super.onDestroy()
    }
}
