package com.plyr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.common.util.UnstableApi
import com.plyr.MainActivity
import com.plyr.PlyrApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@UnstableApi
class MusicService : Service() {

    // === PROPIEDADES ===
    private var mediaSession: MediaSession? = null
    private var mediaSessionCompat: MediaSessionCompat? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private var playlist: List<String> = emptyList()
    private var currentIndex: Int = 0
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    private var mediaButtonReceiver: BroadcastReceiver? = null

    // === CONSTANTES ===
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "music_channel"
        private const val CHANNEL_NAME = "Music Playback"
        private const val CHANNEL_DESCRIPTION = "Controls for music playback"
        private const val TAG = "MusicService"
    }

    // === CICLO DE VIDA DEL SERVICIO ===
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🎵 MusicService onCreate")

        // Configurar WakeLock
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicService::WakeLock")
        wakeLock.acquire(10 * 60 * 1000L /* 10 minutos */)

        // Configurar AudioManager para control de focus
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        // Crear canal de notificación
        createNotificationChannel()

        // ORDEN CORREGIDO: Primero listener, luego sesiones, luego receiver
        setupPlayerListener()

        // Esperar un poco a que el player esté disponible y crear sesiones
        CoroutineScope(Dispatchers.Main).launch {
            kotlinx.coroutines.delay(500) // Dar tiempo al player
            createMediaSessions()
            setupMediaButtonReceiver()

            // Verificar estado después de la configuración
            Log.d(TAG, "🎧 Estado FINAL del servicio:")
            Log.d(TAG, "🎧 - mediaSession: $mediaSession")
            Log.d(TAG, "🎧 - mediaSessionCompat: $mediaSessionCompat")
            Log.d(TAG, "🎧 - mediaSessionCompat.isActive: ${mediaSessionCompat?.isActive}")
        }

        // Iniciar foreground service
        startForeground(NOTIFICATION_ID, createNotification())

        Log.d(TAG, "✅ MusicService completamente inicializado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val plyr = (application as PlyrApp).playerViewModel
        when (intent?.action) {
            "ENSURE_MEDIA_SESSIONS" -> {
                Log.d(TAG, "🎧 ENSURE_MEDIA_SESSIONS recibido desde PlayerViewModel")
                ensureMediaSessionsCreated()
            }
            "ACTION_PLAY" -> {
                Log.d(TAG, "🎵 ACTION_PLAY recibido")
                plyr.playPlayer()
                updateMediaSession()
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(200)
                    updateNotification()
                }
            }
            "ACTION_PAUSE" -> {
                Log.d(TAG, "⏸️ ACTION_PAUSE recibido")
                plyr.pausePlayer()
                updateMediaSession()
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(200)
                    updateNotification()
                }
            }
            "ACTION_PLAY_PAUSE" -> {
                Log.d(TAG, "⏯️ ACTION_PLAY_PAUSE recibido")
                if (plyr.getPlayer()?.isPlaying == true) {
                    plyr.pausePlayer()
                } else {
                    plyr.playPlayer()
                }
                updateMediaSession()
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(200)
                    updateNotification()
                }
            }
            "ACTION_NEXT" -> {
                Log.d(TAG, "⏭️ ACTION_NEXT recibido")
                if (plyr.hasNext.value == true) {
                    CoroutineScope(Dispatchers.Main).launch {
                        plyr.navigateToNext()
                        updateMediaSession()
                        updateNotification()
                    }
                }
            }
            "ACTION_PREV" -> {
                Log.d(TAG, "⏮️ ACTION_PREV recibido")
                if (plyr.hasPrevious.value == true) {
                    CoroutineScope(Dispatchers.Main).launch {
                        plyr.navigateToPrevious()
                        updateMediaSession()
                        updateNotification()
                    }
                }
            }
            "ACTION_FAST_FORWARD" -> {
                Log.d(TAG, "⏩ ACTION_FAST_FORWARD recibido")
                plyr.getPlayer()?.seekForward()
                updateMediaSession()
            }
            "ACTION_REWIND" -> {
                Log.d(TAG, "⏪ ACTION_REWIND recibido")
                plyr.getPlayer()?.seekBack()
                updateMediaSession()
            }
            "ACTION_STOP" -> {
                Log.d(TAG, "🛑 ACTION_STOP recibido")
                plyr.pausePlayer()
                abandonAudioFocus()
                stopForegroundService()
                return START_NOT_STICKY
            }
            else -> {
                val audioUrl = intent?.getStringExtra("AUDIO_URL")
                if (audioUrl != null) playAudio(audioUrl)
            }
        }
        return START_NOT_STICKY
    }

    // === CONFIGURACIÓN DE AUDIO FOCUS ===
    private fun requestAudioFocus(): Boolean {
        audioManager?.let { manager ->
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build()

                manager.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }

            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            Log.d(TAG, "Audio focus request result: $hasAudioFocus")
            return hasAudioFocus
        }
        return false
    }

    private fun abandonAudioFocus() {
        audioManager?.let { manager ->
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
                manager.abandonAudioFocusRequest(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(audioFocusChangeListener)
            }
            hasAudioFocus = false
            Log.d(TAG, "Audio focus abandoned: $result")
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d(TAG, "🔊 Audio focus gained")
                hasAudioFocus = true
                val plyr = (application as PlyrApp).playerViewModel
                plyr.getPlayer()?.volume = 1.0f
                // Si estaba pausado por pérdida de focus, reanudar
                if (plyr.getPlayer()?.playWhenReady == true) {
                    plyr.getPlayer()?.play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d(TAG, "🔇 Audio focus lost permanently")
                hasAudioFocus = false
                val plyr = (application as PlyrApp).playerViewModel
                plyr.pausePlayer()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d(TAG, "⏸️ Audio focus lost temporarily")
                hasAudioFocus = false
                val plyr = (application as PlyrApp).playerViewModel
                plyr.pausePlayer()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d(TAG, "🔉 Audio focus lost - ducking")
                hasAudioFocus = false
                val plyr = (application as PlyrApp).playerViewModel
                plyr.getPlayer()?.volume = 0.3f // Reducir volumen
            }
        }
    }

    // === CONFIGURACIÓN DE MEDIA BUTTON RECEIVER ===
    private fun setupMediaButtonReceiver() {
        Log.d(TAG, "🎧 Configurando MediaButtonReceiver...")

        // Primer intento: BroadcastReceiver programático simple
        mediaButtonReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "🎯🎧 BROADCAST RECIBIDO!")
                Log.d(TAG, "🎯🎧 Action: ${intent?.action}")
                Log.d(TAG, "🎯🎧 Intent completo: $intent")
                Log.d(TAG, "🎯🎧 Extras: ${intent?.extras}")

                when (intent?.action) {
                    Intent.ACTION_MEDIA_BUTTON -> {
                        Log.d(TAG, "🎯🎧 ACTION_MEDIA_BUTTON detectado!")
                        val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        }

                        if (keyEvent != null) {
                            Log.d(TAG, "🎯🎧 KeyEvent obtenido: $keyEvent")
                            val result = handleMediaButtonEvent(keyEvent)
                            Log.d(TAG, "🎯🎧 Resultado del manejo: $result")
                            if (result) {
                                abortBroadcast()
                            }
                        } else {
                            Log.w(TAG, "🎯🎧 KeyEvent es null!")
                        }
                    }
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        Log.d(TAG, "🔇🎧 Audio becoming noisy - pausando")
                        val plyr = (application as PlyrApp).playerViewModel
                        plyr.pausePlayer()
                        updateMediaSession()
                        CoroutineScope(Dispatchers.Main).launch {
                            updateNotification()
                        }
                    }
                    else -> {
                        Log.d(TAG, "🤷🎧 Acción no reconocida: ${intent?.action}")
                    }
                }
            }
        }

        // Registrar con múltiples estrategias
        try {
            Log.d(TAG, "🎧 Intentando registrar MediaButtonReceiver...")

            // Estrategia 1: IntentFilter básico
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_BUTTON)
                addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mediaButtonReceiver, filter, RECEIVER_EXPORTED)
                Log.d(TAG, "🎧 MediaButtonReceiver registrado con RECEIVER_EXPORTED (Android 13+)")
            } else {
                registerReceiver(mediaButtonReceiver, filter)
                Log.d(TAG, "🎧 MediaButtonReceiver registrado (versión anterior)")
            }

            // Estrategia 2: Registrar en AudioManager (método legacy pero a veces funciona mejor)
            audioManager?.let { am ->
                try {
                    // Crear ComponentName para nuestro servicio
                    val componentName = android.content.ComponentName(this, MusicService::class.java)

                    @Suppress("DEPRECATION")
                    am.registerMediaButtonEventReceiver(componentName)
                    Log.d(TAG, "🎧 MediaButton registrado en AudioManager (legacy)")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error registrando en AudioManager: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error registrando MediaButtonReceiver: ${e.message}", e)
        }

        // Estrategia 3: Logging adicional para verificar estado
        Log.d(TAG, "🎧 Estado del servicio:")
        Log.d(TAG, "🎧 - hasAudioFocus: $hasAudioFocus")
        Log.d(TAG, "🎧 - mediaSession: $mediaSession")
        Log.d(TAG, "🎧 - mediaSessionCompat: $mediaSessionCompat")
        Log.d(TAG, "🎧 - mediaSessionCompat.isActive: ${mediaSessionCompat?.isActive}")
    }

    private fun handleMediaButtonEvent(keyEvent: android.view.KeyEvent): Boolean {
        Log.d(TAG, "🎯🎧 handleMediaButtonEvent llamado")
        Log.d(TAG, "🎯🎧 KeyEvent action: ${keyEvent.action} (DOWN=${android.view.KeyEvent.ACTION_DOWN}, UP=${android.view.KeyEvent.ACTION_UP})")

        // Solo procesar eventos ACTION_DOWN para evitar duplicados
        if (keyEvent.action != android.view.KeyEvent.ACTION_DOWN) {
            Log.d(TAG, "🎯🎧 Ignorando evento que no es ACTION_DOWN")
            return false
        }

        val plyr = (application as PlyrApp).playerViewModel

        return when (keyEvent.keyCode) {
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY -> {
                Log.d(TAG, "🎵🎧 Media button: PLAY detectado")
                // Removido: if (!hasAudioFocus) requestAudioFocus()
                plyr.playPlayer()
                updateMediaSession()
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(200)
                    updateNotification()
                }
                Log.d(TAG, "🎵🎧 PLAY ejecutado correctamente")
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                Log.d(TAG, "⏸️🎧 Media button: PAUSE detectado")
                plyr.pausePlayer()
                updateMediaSession()
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(200)
                    updateNotification()
                }
                Log.d(TAG, "⏸️🎧 PAUSE ejecutado correctamente")
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                Log.d(TAG, "⏯️🎧 Media button: PLAY_PAUSE detectado")
                val isCurrentlyPlaying = plyr.getPlayer()?.isPlaying == true
                Log.d(TAG, "🎧 Estado actual del reproductor: isPlaying = $isCurrentlyPlaying")

                if (isCurrentlyPlaying) {
                    Log.d(TAG, "🎧 Pausando reproductor...")
                    plyr.pausePlayer()
                } else {
                    Log.d(TAG, "🎧 Reproduciendo...")
                    plyr.playPlayer()
                }
                updateMediaSession()
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(200)
                    updateNotification()
                }
                Log.d(TAG, "⏯️🎧 PLAY_PAUSE ejecutado correctamente")
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_NEXT -> {
                Log.d(TAG, "⏭️🎧 Media button: NEXT detectado")
                val hasNext = plyr.hasNext.value == true
                Log.d(TAG, "🎧 ¿Tiene siguiente?: $hasNext")

                if (hasNext) {
                    Log.d(TAG, "🎧 Navegando a siguiente pista...")
                    CoroutineScope(Dispatchers.Main).launch {
                        plyr.navigateToNext()
                        updateMediaSession()
                        updateNotification()
                    }
                    Log.d(TAG, "⏭️🎧 NEXT ejecutado correctamente")
                } else {
                    Log.d(TAG, "⏭️🎧 No hay siguiente pista disponible")
                }
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                Log.d(TAG, "⏮️🎧 Media button: PREVIOUS detectado")
                val hasPrevious = plyr.hasPrevious.value == true
                Log.d(TAG, "🎧 ¿Tiene anterior?: $hasPrevious")

                if (hasPrevious) {
                    Log.d(TAG, "🎧 Navegando a pista anterior...")
                    CoroutineScope(Dispatchers.Main).launch {
                        plyr.navigateToPrevious()
                        updateMediaSession()
                        updateNotification()
                    }
                    Log.d(TAG, "⏮️🎧 PREVIOUS ejecutado correctamente")
                } else {
                    Log.d(TAG, "⏮️🎧 No hay pista anterior disponible")
                }
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_STOP -> {
                Log.d(TAG, "🛑🎧 Media button: STOP detectado")
                plyr.pausePlayer()
                abandonAudioFocus()
                stopForegroundService()
                Log.d(TAG, "🛑🎧 STOP ejecutado correctamente")
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                Log.d(TAG, "⏩🎧 Media button: FAST_FORWARD detectado")
                plyr.getPlayer()?.seekForward()
                updateMediaSession()
                Log.d(TAG, "⏩🎧 FAST_FORWARD ejecutado correctamente")
                true
            }
            android.view.KeyEvent.KEYCODE_MEDIA_REWIND -> {
                Log.d(TAG, "⏪🎧 Media button: REWIND detectado")
                plyr.getPlayer()?.seekBack()
                updateMediaSession()
                Log.d(TAG, "⏪🎧 REWIND ejecutado correctamente")
                true
            }
            else -> {
                Log.d(TAG, "🤷🎧 Media button no reconocido: ${keyEvent.keyCode}")
                Log.d(TAG, "🎧 Códigos conocidos:")
                Log.d(TAG, "🎧 - PLAY: ${android.view.KeyEvent.KEYCODE_MEDIA_PLAY}")
                Log.d(TAG, "🎧 - PAUSE: ${android.view.KeyEvent.KEYCODE_MEDIA_PAUSE}")
                Log.d(TAG, "🎧 - PLAY_PAUSE: ${android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE}")
                Log.d(TAG, "🎧 - NEXT: ${android.view.KeyEvent.KEYCODE_MEDIA_NEXT}")
                Log.d(TAG, "🎧 - PREVIOUS: ${android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS}")
                false
            }
        }
    }

    // === CONFIGURACIÓN DE MEDIA SESSIONS ===
    private fun createMediaSessions() {
        val plyr = (application as PlyrApp).playerViewModel
        val sharedPlayer = plyr.getPlayer()

        if (sharedPlayer != null) {
            Log.d(TAG, "✅ Player disponible, creando MediaSessions...")

            // Crear MediaSession moderna (Media3) con ID único
            mediaSession = MediaSession.Builder(this, sharedPlayer)
                .setId("PlyrMusicSession_${System.currentTimeMillis()}")
                .setCallback(createMediaSessionCallback())
                .build()

            // Crear MediaSessionCompat para compatibilidad con auriculares antiguos
            mediaSessionCompat = MediaSessionCompat(this, "PlyrMediaSessionCompat").apply {
                @Suppress("DEPRECATION")
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                setCallback(createMediaSessionCompatCallback())
                isActive = true
            }

            updateMediaSession()
            Log.d(TAG, "✅ MediaSessions creadas correctamente")
            Log.d(TAG, "🎧 MediaSession: $mediaSession")
            Log.d(TAG, "🎧 MediaSessionCompat: $mediaSessionCompat")
            Log.d(TAG, "🎧 MediaSessionCompat.isActive: ${mediaSessionCompat?.isActive}")
        } else {
            Log.w(TAG, "⚠️ Player no disponible aún, reintentando en 1s...")
            // Reintentar después de 1 segundo
            CoroutineScope(Dispatchers.Main).launch {
                kotlinx.coroutines.delay(1000)
                createMediaSessions()
            }
        }
    }

    // Método para asegurar que las MediaSessions estén creadas cuando el player esté listo
    fun ensureMediaSessionsCreated() {
        if (mediaSession == null || mediaSessionCompat == null) {
            Log.d(TAG, "🎧 Asegurando que MediaSessions estén creadas...")
            createMediaSessions()
        }
    }

    private fun createMediaSessionCallback(): MediaSession.Callback {
        return object : MediaSession.Callback {
            // Media3 MediaSession.Callback doesn't use these override methods
            // The session automatically handles basic playback commands through the player
            // Custom handling can be done through MediaSession.setCustomLayout or other mechanisms
        }
    }

    private fun createMediaSessionCompatCallback(): MediaSessionCompat.Callback {
        return object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                Log.d(TAG, "🎵🎧 MediaSessionCompat: onPlay - DETECTADO!")
                val plyr = (application as PlyrApp).playerViewModel
                plyr.playPlayer()
                updateMediaSession()
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(200)
                    updateNotification()
                }
            }

            override fun onPause() {
                Log.d(TAG, "⏸️🎧 MediaSessionCompat: onPause - DETECTADO!")
                val plyr = (application as PlyrApp).playerViewModel
                plyr.pausePlayer()
                updateMediaSession()
                CoroutineScope(Dispatchers.Main).launch {
                    kotlinx.coroutines.delay(200)
                    updateNotification()
                }
            }

            override fun onSkipToNext() {
                Log.d(TAG, "⏭️🎧 MediaSessionCompat: onSkipToNext - DETECTADO!")
                val plyr = (application as PlyrApp).playerViewModel
                if (plyr.hasNext.value == true) {
                    CoroutineScope(Dispatchers.Main).launch {
                        plyr.navigateToNext()
                        updateMediaSession()
                        updateNotification()
                    }
                }
            }

            override fun onSkipToPrevious() {
                Log.d(TAG, "⏮️🎧 MediaSessionCompat: onSkipToPrevious - DETECTADO!")
                val plyr = (application as PlyrApp).playerViewModel
                if (plyr.hasPrevious.value == true) {
                    CoroutineScope(Dispatchers.Main).launch {
                        plyr.navigateToPrevious()
                        updateMediaSession()
                        updateNotification()
                    }
                }
            }

            override fun onFastForward() {
                Log.d(TAG, "⏩🎧 MediaSessionCompat: onFastForward - DETECTADO!")
                val plyr = (application as PlyrApp).playerViewModel
                val player = plyr.getPlayer()
                if (player?.playbackState == Player.STATE_READY) {
                    player.seekForward()
                    updateMediaSession()
                    Log.d(TAG, "⏩🎧 FAST_FORWARD ejecutado desde MediaSession")
                }
            }

            override fun onRewind() {
                Log.d(TAG, "⏪🎧 MediaSessionCompat: onRewind - DETECTADO!")
                val plyr = (application as PlyrApp).playerViewModel
                val player = plyr.getPlayer()
                if (player?.playbackState == Player.STATE_READY) {
                    player.seekBack()
                    updateMediaSession()
                    Log.d(TAG, "⏪🎧 REWIND ejecutado desde MediaSession")
                }
            }

            override fun onStop() {
                Log.d(TAG, "🛑🎧 MediaSessionCompat: onStop - DETECTADO!")
                val plyr = (application as PlyrApp).playerViewModel
                plyr.pausePlayer()
                abandonAudioFocus()
                stopForegroundService()
            }

            override fun onSeekTo(pos: Long) {
                Log.d(TAG, "⏭🎧 MediaSessionCompat: onSeekTo $pos - DETECTADO!")
                val plyr = (application as PlyrApp).playerViewModel
                plyr.getPlayer()?.seekTo(pos)
                updateMediaSession()
            }

            override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                Log.d(TAG, "🎯🎧 MediaSessionCompat: onMediaButtonEvent - DETECTADO!")
                Log.d(TAG, "🎯🎧 Intent: $mediaButtonEvent")

                val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }

                if (keyEvent != null) {
                    Log.d(TAG, "🎯🎧 KeyEvent desde MediaSession: $keyEvent")
                    return handleMediaButtonEvent(keyEvent)
                }

                return super.onMediaButtonEvent(mediaButtonEvent)
            }
        }
    }

    private fun updateMediaSession() {
        val plyr = (application as PlyrApp).playerViewModel
        val player = plyr.getPlayer()
        val currentTrack = plyr.currentTrack.value

        // Actualizar MediaSessionCompat
        mediaSessionCompat?.let { session ->
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO or
                    PlaybackStateCompat.ACTION_FAST_FORWARD or
                    PlaybackStateCompat.ACTION_REWIND
                )
                .setState(
                    if (player?.isPlaying == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                    player?.currentPosition ?: 0L,
                    1.0f
                )

            session.setPlaybackState(stateBuilder.build())

            // Actualizar metadata
            currentTrack?.let { track ->
                val metadataBuilder = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.name)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artists)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player?.duration ?: 0L)

                session.setMetadata(metadataBuilder.build())
            }
        }
    }

    // === CONFIGURACIÓN DE PLAYER LISTENER ===
    private fun setupPlayerListener() {
        val plyr = (application as PlyrApp).playerViewModel
        val sharedPlayer = plyr.getPlayer()

        sharedPlayer?.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(TAG, "🔄 onIsPlayingChanged: isPlaying = $isPlaying")
                updateMediaSession()
                CoroutineScope(Dispatchers.Main).launch {
                    handlePlaybackStateChange(isPlaying)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_IDLE -> {
                        Log.d(TAG, "🔵 Player state: IDLE")
                        updateMediaSession()
                    }
                    Player.STATE_BUFFERING -> {
                        Log.d(TAG, "🔄 Player state: BUFFERING")
                        updateMediaSession()
                    }
                    Player.STATE_READY -> {
                        Log.d(TAG, "✅ Player state: READY")
                        CoroutineScope(Dispatchers.Main).launch {
                            updateNotification()
                        }
                    }
                    Player.STATE_ENDED -> {
                        Log.d(TAG, "🏁 Player state: ENDED")
                        CoroutineScope(Dispatchers.Main).launch {
                            handleTrackEnded()
                        }
                    }
                }
                updateMediaSession()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e(TAG, "❌ Player error: ${error.message}", error)
                CoroutineScope(Dispatchers.Main).launch {
                    handlePlayerError(error)
                }
            }
        })
    }

    // === MÉTODOS DE REPRODUCCIÓN ===
    fun playAudio(audioUrl: String) {
        Log.d(TAG, "🎯 playAudio llamado con: $audioUrl")
        val plyr = (application as PlyrApp).playerViewModel

        try {
            plyr.loadAudio(audioUrl, "Audio Track")

            CoroutineScope(Dispatchers.Main).launch {
                val player = plyr.getPlayer()
                player?.playWhenReady = true
                kotlinx.coroutines.delay(500)
                updateNotification()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al reproducir audio: ${e.message}", e)
        }
    }

    fun playPlaylist(urls: List<String>, startIndex: Int = 0) {
        playlist = urls
        currentIndex = startIndex.coerceIn(0, urls.size - 1)

        if (playlist.isNotEmpty()) {
            Log.d(TAG, "📋 Playing playlist: ${playlist.size} tracks, starting at index $currentIndex")
            playAudio(playlist[currentIndex])
        } else {
            Log.w(TAG, "⚠️ Attempted to play empty playlist")
        }
    }

    // === MANEJO DE ESTADOS ===
    private fun handlePlaybackStateChange(isPlaying: Boolean) {
        val plyr = (application as PlyrApp).playerViewModel
        val hasMedia = plyr.getPlayer()?.currentMediaItem != null

        if (hasMedia) {
            try {
                updateNotification()
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error al mostrar notificación: ${e.message}", e)
            }
        }
    }

    private fun handleTrackEnded() {
        if (playlist.isNotEmpty() && currentIndex < playlist.size - 1) {
            currentIndex++
            val nextUrl = playlist[currentIndex]
            Log.d(TAG, "⏭️ Track ended, playing next: $nextUrl")
            playAudio(nextUrl)
        } else {
            Log.d(TAG, "🏁 Playlist ended")
        }
    }

    private fun handlePlayerError(error: androidx.media3.common.PlaybackException) {
        Log.e(TAG, "❌ Manejando error del player: ${error.message}")
        // Implementar lógica de recuperación si es necesario
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
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val plyr = (application as PlyrApp).playerViewModel
        val player = plyr.getPlayer()
        val currentTrack = plyr.currentTrack.value
        val title = currentTrack?.name ?: "Music Player"
        val artist = currentTrack?.artists ?: "Unknown Artist"
        val isCurrentlyPlaying = player?.isPlaying == true
        val isBuffering = player?.playbackState == Player.STATE_BUFFERING
        val isReady = player?.playbackState == Player.STATE_READY
        val playWhenReady = player?.playWhenReady == true

        // Determinar estado real de reproducción
        val actuallyPlaying = isCurrentlyPlaying && isReady
        val shouldShowAsPlaying = playWhenReady && (actuallyPlaying || isBuffering)

        Log.d(TAG, "📱 Creando notificación - Playing: $actuallyPlaying, Buffering: $isBuffering, PlayWhenReady: $playWhenReady, Title: $title")

        val playIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_PLAY" }
        val pauseIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_PAUSE" }
        val nextIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_NEXT" }
        val prevIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_PREV" }
        val fwdIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_FAST_FORWARD" }
        val rwdIntent = Intent(this, MusicService::class.java).apply { action = "ACTION_REWIND" }

        val playPendingIntent = PendingIntent.getService(this, 0, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val nextPendingIntent = PendingIntent.getService(this, 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val prevPendingIntent = PendingIntent.getService(this, 3, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val fwdPendingIntent = PendingIntent.getService(this, 4, fwdIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val rwdPendingIntent = PendingIntent.getService(this, 5, rwdIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        // Determinar subtexto basado en estado
        val subText = when {
            isBuffering -> "Cargando..."
            actuallyPlaying -> "Reproduciendo..."
            playWhenReady && !isReady -> "Preparando..."
            else -> "En pausa"
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(subText)
            .setSmallIcon(if (shouldShowAsPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            .setContentIntent(createMainActivityPendingIntent())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)

        val compactActions = mutableListOf<Int>()
        var actionIndex = 0

        // Botón rewind (si hay contenido)
        if (currentTrack != null && isReady) {
            builder.addAction(android.R.drawable.ic_media_rew, "Rewind", rwdPendingIntent)
            compactActions.add(actionIndex)
            actionIndex++
        }

        // Botón anterior (si hay pista anterior)
        if (plyr.hasPrevious.value == true) {
            builder.addAction(android.R.drawable.ic_media_previous, "Previous", prevPendingIntent)
            if (compactActions.size < 2) compactActions.add(actionIndex)
            actionIndex++
        }

        // Botón play/pause (siempre presente pero habilitado solo cuando esté listo o cargando)
        val playPauseIcon = if (shouldShowAsPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val playPauseText = if (shouldShowAsPlaying) "Pause" else "Play"
        val playPauseIntent = if (shouldShowAsPlaying) pausePendingIntent else playPendingIntent

        builder.addAction(playPauseIcon, playPauseText, playPauseIntent)
        compactActions.add(actionIndex)
        actionIndex++

        // Botón siguiente (si hay pista siguiente)
        if (plyr.hasNext.value == true) {
            builder.addAction(android.R.drawable.ic_media_next, "Next", nextPendingIntent)
            if (compactActions.size < 3) compactActions.add(actionIndex)
            actionIndex++
        }

        // Botón fast forward (si hay contenido)
        if (currentTrack != null && isReady) {
            builder.addAction(android.R.drawable.ic_media_ff, "Fast Forward", fwdPendingIntent)
            // No agregar a compact actions ya que ya tenemos 3
        }

        // MediaStyle con MediaSession token
        builder.setStyle(androidx.media.app.NotificationCompat.MediaStyle()
            .setShowActionsInCompactView(*compactActions.toIntArray())
            .setMediaSession(mediaSessionCompat?.sessionToken)
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

    private fun updateNotification() {
        try {
            val notification = createNotification()
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error al actualizar notificación: ${e.message}", e)
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
    fun stopForegroundService() {
        Log.d(TAG, "🛑 Deteniendo servicio foreground")
        val plyr = (application as PlyrApp).playerViewModel
        plyr.pausePlayer()
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        cleanupResources()
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "🗑️ Destruyendo MusicService")

        // Desregistrar MediaButtonReceiver
        try {
            mediaButtonReceiver?.let { receiver ->
                unregisterReceiver(receiver)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering media button receiver", e)
        }

        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        abandonAudioFocus()
        cleanupResources()
        super.onDestroy()
    }

    private fun cleanupResources() {
        mediaSession?.run {
            release()
            mediaSession = null
        }
        mediaSessionCompat?.run {
            isActive = false
            release()
            mediaSessionCompat = null
        }
    }
}
