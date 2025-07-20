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
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.plyr.MainActivity
import com.plyr.R

/**
 * MusicService - Servicio de música que maneja la reproducción en segundo plano
 * 
 * Este servicio es responsable de:
 * - Crear y gestionar una MediaSession para integración con el sistema
 * - Mostrar notificaciones de reproducción
 * - Mantener la reproducción activa cuando la app está en segundo plano
 * - Proporcionar controles de medios desde la barra de notificación
 * 
 * Nota: Actualmente funciona como un servicio auxiliar. El PlayerViewModel
 * maneja la lógica principal de reproducción usando NewPipe.
 */
class MusicService : Service() {
    
    // === PROPIEDADES ===
    
    /** MediaSession para integración con el sistema de medios de Android */
    private var mediaSession: MediaSession? = null
    
    /** ExoPlayer dedicado para este servicio */
    private lateinit var player: ExoPlayer
    
    // === CONSTANTES ===
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "music_channel"
        private const val CHANNEL_NAME = "Music Playback"
        private const val CHANNEL_DESCRIPTION = "Controls for music playback"
    }
    
    // === CICLO DE VIDA DEL SERVICIO ===
    
    /**
     * Inicializa el servicio, ExoPlayer y MediaSession.
     * Configura el canal de notificación y listeners.
     */
    override fun onCreate() {
        super.onCreate()
        
        initializePlayer()
        createMediaSession()
        createNotificationChannel()
        setupPlayerListener()
    }
    
    /**
     * Inicializa el ExoPlayer para este servicio.
     */
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
    }
    
    /**
     * Crea la MediaSession para integración con el sistema.
     */
    private fun createMediaSession() {
        mediaSession = MediaSession.Builder(this, player).build()
    }
    
    /**
     * Configura el listener para responder a cambios de estado del player.
     */
    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handlePlaybackStateChange(isPlaying)
            }
        })
    }
    
    /**
     * Maneja cambios en el estado de reproducción.
     * @param isPlaying true si está reproduciendo, false si está pausado
     */
    private fun handlePlaybackStateChange(isPlaying: Boolean) {
        if (isPlaying) {
            startForeground(NOTIFICATION_ID, createNotification())
        } else {
            stopForeground(false)
        }
    }
    
    // === CONFIGURACIÓN DE NOTIFICACIONES ===
    
    /**
     * Crea el canal de notificación requerido para Android O+.
     */
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
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Crea la notificación para el servicio en primer plano.
     * @return Notificación configurada para reproducción de música
     */
    private fun createNotification(): Notification {
        val pendingIntent = createMainActivityPendingIntent()
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Music Player")
            .setContentText("Reproduciendo música")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Crea un PendingIntent para abrir la MainActivity.
     * @return PendingIntent configurado
     */
    private fun createMainActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    
    // === MÉTODOS PÚBLICOS ===
    
    /**
     * Reproduce audio desde una URL específica.
     * Nota: Este método existe para compatibilidad, pero la lógica principal
     * de reproducción se maneja en PlayerViewModel.
     * 
     * @param audioUrl URL del archivo de audio a reproducir
     */
    fun playAudio(audioUrl: String) {
        try {
            val mediaItem = MediaItem.fromUri(audioUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        } catch (e: Exception) {
            println("MusicService: Error reproduciendo audio: ${e.message}")
        }
    }
    
    // === BINDING ===
    
    /**
     * Binder para conectar con la Activity principal.
     * Permite que la MainActivity acceda a los métodos del servicio.
     */
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }
    
    /** Instancia del binder para conexiones */
    private val binder = MusicBinder()
    
    /**
     * Retorna el binder cuando otra componente se conecta al servicio.
     */
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    // === LIMPIEZA DE RECURSOS ===
    
    /**
     * Limpia recursos cuando el servicio es destruido.
     * Libera el ExoPlayer y la MediaSession.
     */
    override fun onDestroy() {
        cleanupResources()
        super.onDestroy()
    }
    
    /**
     * Limpia todos los recursos del servicio.
     */
    private fun cleanupResources() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
    }
}
