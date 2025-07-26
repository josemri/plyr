package com.plyr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.plyr.MainActivity
import com.plyr.PlyrApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MusicService : Service() {

    // === PROPIEDADES ===
    private var mediaSession: MediaSession? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private var playlist: List<String> = emptyList()
    private var currentIndex: Int = 0

    // === CONSTANTES ===
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "music_channel"
        private const val CHANNEL_NAME = "Music Playback"
        private const val CHANNEL_DESCRIPTION = "Controls for music playback"
    }

    // === CICLO DE VIDA DEL SERVICIO ===
    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicService::WakeLock")
        wakeLock.acquire()

        createNotificationChannel()
        setupPlayerListener()
        createMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val plyr = (application as PlyrApp).playerViewModel
        when (intent?.action) {
            "ACTION_PLAY" -> {
                Log.d("MusicService", "🎵 ACTION_PLAY recibido")
                plyr.playPlayer()

                // Actualizar notificación después de un breve delay
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(200)
                    updateNotification()
                }
            }
            "ACTION_PAUSE" -> {
                Log.d("MusicService", "⏸️ ACTION_PAUSE recibido")
                plyr.pausePlayer()

                // Actualizar notificación después de un breve delay
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(200)
                    updateNotification()
                }
            }
            "ACTION_NEXT" -> CoroutineScope(Dispatchers.Default).launch {
                if (plyr.hasNext.value) {
                    plyr.navigateToNext()
                    kotlinx.coroutines.delay(300)
                    updateNotification()
                } else {
                    Log.d("MusicService", "No next track available")
                }
            }
            "ACTION_PREV" -> CoroutineScope(Dispatchers.Default).launch {
                if (plyr.hasPrevious.value) {
                    plyr.navigateToPrevious()
                    kotlinx.coroutines.delay(300)
                    updateNotification()
                } else {
                    Log.d("MusicService", "No previous track available")
                }
            }
            else -> {
                val audioUrl = intent?.getStringExtra("AUDIO_URL")
                if (audioUrl != null) playAudio(audioUrl)
            }
        }
        return START_STICKY
    }

    // === CONFIGURACIÓN DE COMPONENTES ===
    private fun createMediaSession() {
        val plyr = (application as PlyrApp).playerViewModel
        val sharedPlayer = plyr.getPlayer()

        if (sharedPlayer != null) {
            mediaSession = MediaSession.Builder(this, sharedPlayer).build()
            Log.d("MusicService", "✅ MediaSession creada correctamente")
        } else {
            Log.e("MusicService", "❌ No se pudo obtener el player de PlayerViewModel")
        }
    }

    private fun setupPlayerListener() {
        val plyr = (application as PlyrApp).playerViewModel
        val sharedPlayer = plyr.getPlayer()

        sharedPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d("MusicService", "🔄 onIsPlayingChanged: isPlaying = $isPlaying")
                handlePlaybackStateChange(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN"
                }
                Log.d("MusicService", "📊 onPlaybackStateChanged: state = $stateName ($playbackState)")

                if (playbackState == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("MusicService", "❌ Player error: ${error.message}", error)
                // Reintentar o manejar el error
                handlePlayerError(error)
            }
        })
    }

    // === MÉTODOS DE REPRODUCCIÓN ===
    fun playAudio(audioUrl: String) {
        Log.d("MusicService", "🎯 playAudio llamado con: $audioUrl")
        val plyr = (application as PlyrApp).playerViewModel

        try {
            plyr.loadAudio(audioUrl, "Audio Track")

            // Esperar un poco antes de mostrar la notificación
            CoroutineScope(Dispatchers.Main).launch {
                kotlinx.coroutines.delay(500) // Esperar 500ms
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            Log.e("MusicService", "❌ Error al reproducir audio: ${e.message}", e)
        }
    }

    fun playPlaylist(urls: List<String>, startIndex: Int = 0) {
        playlist = urls
        currentIndex = startIndex.coerceIn(0, urls.size - 1)

        if (playlist.isNotEmpty()) {
            Log.d("MusicService", "📋 Playing playlist: ${playlist.size} tracks, starting at index $currentIndex")
            playAudio(playlist[currentIndex])
        } else {
            Log.w("MusicService", "⚠️ Attempted to play empty playlist")
        }
    }

    // === MANEJO DE ESTADOS ===
    private fun handlePlaybackStateChange(isPlaying: Boolean) {
        Log.d("MusicService", "🔄 handlePlaybackStateChange: isPlaying = $isPlaying")

        val plyr = (application as PlyrApp).playerViewModel
        val hasMedia = plyr.getPlayer()?.currentMediaItem != null

        if (hasMedia || isPlaying) {
            Log.d("MusicService", "🎵 Actualizando notificación - isPlaying: $isPlaying")
            try {
                // Para la primera vez, usar startForeground
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    updateNotification()
                } else {
                    startForeground(NOTIFICATION_ID, createNotification())
                }
            } catch (e: Exception) {
                Log.e("MusicService", "❌ Error al mostrar notificación: ${e.message}", e)
            }
        } else {
            Log.d("MusicService", "⏸️ Sin contenido, deteniendo servicio en primer plano")
            stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    private fun handleTrackEnded() {
        if (playlist.isNotEmpty() && currentIndex < playlist.size - 1) {
            currentIndex++
            val nextUrl = playlist[currentIndex]
            Log.d("MusicService", "⏭️ Track ended, playing next: $nextUrl")
            playAudio(nextUrl)
        } else {
            Log.d("MusicService", "🏁 Playlist ended")
            stopForeground(STOP_FOREGROUND_DETACH)
        }
    }

    private fun handlePlayerError(error: androidx.media3.common.PlaybackException) {
        Log.e("MusicService", "❌ Manejando error del player: ${error.message}")

        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Limpiar MediaSession
                cleanupResources()

                // Esperar un momento antes de reintentar
                kotlinx.coroutines.delay(1000)

                // Recrear MediaSession con el player existente
                createMediaSession()

                // Reintentar la reproducción si hay un track actual
                if (playlist.isNotEmpty() && currentIndex < playlist.size) {
                    Log.d("MusicService", "🔄 Reintentando reproducción del track actual")
                    playAudio(playlist[currentIndex])
                }

                Log.d("MusicService", "🔄 Recuperación de error completada")
            } catch (e: Exception) {
                Log.e("MusicService", "❌ Error durante la recuperación: ${e.message}", e)

                // Si falla todo, detener el servicio
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    // === CONFIGURACIÓN DE NOTIFICACIONES ===
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // Sin sonido para la notificación
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    private fun createNotification(): Notification {
        val plyr = (application as PlyrApp).playerViewModel
        val player = plyr.getPlayer()
        val currentMediaItem = player?.currentMediaItem
        val title = currentMediaItem?.mediaMetadata?.title?.toString() ?: "Music Player"

        // Obtener el estado correcto del player
        val isCurrentlyPlaying = player?.isPlaying == true

        Log.d("MusicService", "📱 Creando notificación - Playing: $isCurrentlyPlaying, Title: $title")

        val playIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_PLAY" }
        val pauseIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_PAUSE" }
        val nextIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_NEXT" }
        val prevIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_PREV" }

        val playPendingIntent = PendingIntent.getService(this, 0, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val nextPendingIntent = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val prevPendingIntent = PendingIntent.getService(this, 3, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(if (isCurrentlyPlaying) "Reproduciendo..." else "En pausa")
            .setSmallIcon(if (isCurrentlyPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            .setContentIntent(createMainActivityPendingIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)

        val compactActions = mutableListOf<Int>()
        var actionIndex = 0

        // Botón anterior (si hay pista anterior)
        if (plyr.hasPrevious.value) {
            builder.addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            compactActions.add(actionIndex)
            actionIndex++
        }

        // Botón play/pause (siempre presente)
        val playPauseIcon = if (isCurrentlyPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (isCurrentlyPlaying) "Pause" else "Play"
        val playPauseIntent = if (isCurrentlyPlaying) pausePendingIntent else playPendingIntent

        builder.addAction(playPauseIcon, playPauseText, playPauseIntent)
        compactActions.add(actionIndex)
        actionIndex++

        // Botón siguiente (si hay pista siguiente)
        if (plyr.hasNext.value) {
            builder.addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            compactActions.add(actionIndex)
        }

        // MediaStyle simplificado sin MediaSession token
        builder.setStyle(androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(*compactActions.toIntArray())
        )

        return builder.build()
    }

    private fun createMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    /**
     * Actualiza la notificación existente
     */
    private fun updateNotification() {
        try {
            val notification = createNotification()
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d("MusicService", "🔄 Notificación actualizada")
        } catch (e: Exception) {
            Log.e("MusicService", "❌ Error al actualizar notificación: ${e.message}", e)
        }
    }

    // === BINDING ===
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    private val binder = MusicBinder()

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    // === LIMPIEZA DE RECURSOS ===
    override fun onDestroy() {
        Log.d("MusicService", "🗑️ Destruyendo MusicService")
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        cleanupResources()
        super.onDestroy()
    }

    private fun cleanupResources() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
    }
}