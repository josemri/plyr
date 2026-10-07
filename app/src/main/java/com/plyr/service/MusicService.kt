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
import com.plyr.R
import androidx.media3.common.Player

class MusicService : Service() {
    private val CHANNEL_ID = "plyr_playback"
    private val NOTIFICATION_ID = 1
    private var mediaSession: MediaSession? = null

    /**
     * Último estado visible pintado, para no reconstruir la notificación cuando
     * el evento no cambia nada de lo que se ve. La visibilidad de los botones de
     * la `MediaSession` **no** se guarda aquí: la decide el `PlaybackState` a
     * partir de los comandos del reproductor, que [QueueAwarePlayer] mide con la
     * cola entera (ver `NotificationRefreshPolicy`).
     */
    private var lastNotification: PlaybackNotificationState? = null

    /**
     * Salto de canción pedido desde fuera de la app (notificación, botón del
     * sistema, auriculares, Android Auto).
     *
     * El reproductor solo conoce la ventana de pistas que tiene cargada, así
     * que su "anterior" es el anterior *de la ventana*, que no es el anterior de
     * la cola: al recortar por delante, el botón no tenía nada a lo que ir y no
     * hacía nada. Al delegar aquí, la decisión la toma [QueueIndex] sobre la
     * cola entera, que es la fuente de verdad (B49).
     */
    var onSkipRequest: ((backwards: Boolean) -> Unit)? = null

    companion object {
        const val ACTION_STOP = "io.github.josemri.plyr.action.STOP"
    }

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        // startForegroundService exige promoverse a foreground en los primeros 5s.
        if (mediaSession == null) {
            startForeground(NOTIFICATION_ID, createStartupNotification())
        }
        return START_NOT_STICKY
    }

    private fun createStartupNotification(): Notification {
        // La provisional la impone `startForegroundService` (sin ella el servicio
        // no puede quedarse en foreground), pero no debe afirmar que hay música
        // sonando: con el estado "idle" es descartable y sin controles falsos
        // (B56).
        return buildNotification(PlaybackNotificationState.idle(appName()))
    }

    private fun appName(): String = getString(R.string.app_name)

    @OptIn(UnstableApi::class)
    fun setupMediaSession(player: ExoPlayer, hasNextInQueue: () -> Boolean) {
        if (mediaSession != null) {
            refreshNotification(player, NotificationEvent.ITEM_TRANSITION)
            return
        }

        // El `>>` lo pinta el sistema a partir de los comandos del reproductor,
        // y la ventana de ExoPlayer solo ve un trozo de la cola: con ella sola
        // el botón se escondía en cuanto la canción actual quedaba al final de
        // la ventana (B57). La sesión envuelve al reproductor para decidir ese
        // comando con la cola entera, que es la fuente de verdad (B49).
        val session = MediaSession.Builder(this, QueueAwarePlayer(player, hasNextInQueue))
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setCallback(object : MediaSession.Callback {
                override fun onPlayerCommandRequest(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    playerCommand: Int,
                ): Int = SessionSkipCommand.handle(playerCommand) { direction ->
                    // La cola completa vive en la app; el reproductor solo ve su
                    // ventana, y por eso su "anterior" no es el anterior (B49).
                    onSkipRequest?.invoke(direction == SessionSkipCommand.Direction.BACKWARDS)
                }
            })
            .build()
        mediaSession = session

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                refreshNotification(player, NotificationEvent.ITEM_TRANSITION)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                // El recorte y el relleno de la ventana (`trimWindow` /
                // `growWindow`) solo disparan esto. Sin escucharlos, la
                // notificación se quedaba con el estado de antes del
                // `addMediaItems` (B50). El `>>` de los controles del sistema
                // no depende de aquí, sino del `PlaybackState` (B57).
                refreshNotification(player, NotificationEvent.TIMELINE_CHANGED)
            }
        })

        lastNotification = notificationState(player)
        startForeground(NOTIFICATION_ID, buildNotification(lastNotification!!))
    }

    /**
     * Estado visible de la notificación a partir del reproductor.
     *
     * Sin item en curso devuelve el estado "idle" en vez de un item fantasma:
     * antes caía en los valores por defecto ("Plyr" / "Reproduciendo") y, con
     * `setOngoing(true)`, se quedaba puesta para siempre (B55, B56).
     */
    private fun notificationState(player: ExoPlayer): PlaybackNotificationState {
        val item = player.currentMediaItem
        return PlaybackNotificationState.of(
            appName = appName(),
            itemTitle = item?.mediaMetadata?.title?.toString(),
            itemArtist = item?.mediaMetadata?.artist?.toString(),
        )
    }

    @OptIn(UnstableApi::class)
    private fun buildNotification(state: PlaybackNotificationState): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(state.title)
            .setContentText(state.text)
            .setOngoing(state.ongoing)
            // Con B50 la notificación se repinta también en cada cambio de
            // ventana, así que se repinta varias veces por canción. Sin esto el
            // dispositivo vibraría en cada salto, no solo al empezar a sonar.
            .setOnlyAlertOnce(true)

        // El `MediaStyle` solo se monta si hay una sesión con items: sin ellos el
        // sistema no pintaría botones, pero el estilo vacío era lo que dejaba la
        // notificación fantasma.
        val session = mediaSession
        if (state.showMediaStyle && session != null) {
            builder.setStyle(MediaStyleNotificationHelper.MediaStyle(session))
        }
        return builder.build()
    }

    private fun refreshNotification(player: ExoPlayer, event: NotificationEvent) {
        val next = notificationState(player)
        when (NotificationRefreshPolicy.decide(event, lastNotification, next)) {
            NotificationAction.REBUILD -> {
                lastNotification = next
                startForeground(NOTIFICATION_ID, buildNotification(next))
            }

            NotificationAction.REMOVE -> {
                // No hay nada sonando: mejor no dejar nada colgado. La sesión
                // sigue viva para que el próximo item repinte la notificación.
                lastNotification = next
                stopForeground(STOP_FOREGROUND_REMOVE)
            }

            NotificationAction.SKIP -> Unit
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        super.onDestroy()
    }
}
