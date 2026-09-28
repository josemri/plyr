package com.plyr.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import com.plyr.PlyrApp

/**
 * MediaButtonReceiver - Atiende los botones de media de auriculares
 * inalámbricos, del coche y del teclado (play/pause, siguiente, anterior,
 * avanzar y retroceder).
 *
 * Se declara en el manifiesto, así que el framework debe poder instanciarlo sin
 * argumentos: por eso no tiene parámetros de constructor.
 *
 * Ejecuta las acciones sobre el [com.plyr.viewmodel.PlayerViewModel] de la
 * aplicación, que es quien tiene el reproductor. No arranca el
 * [com.plyr.service.MusicService] a propósito: un receiver del manifiesto
 * puede ejecutarse en segundo plano y arrancar un servicio allí lanzaría
 * `ForegroundServiceStartNotAllowedException` en Android 12+.
 *
 * `ACTION_AUDIO_BECOMING_NOISY` no se gestiona aquí a propósito: ExoPlayer ya
 * lo hace con `setHandleAudioBecomingNoisy(true)`, y declararlo otra vez en el
 * manifiesto provocaría que la pausa se aplicase dos veces.
 */
class MediaButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        if (intent?.action != Intent.ACTION_MEDIA_BUTTON) return

        val keyEvent = IntentCompat.getParcelableExtra(
            intent,
            Intent.EXTRA_KEY_EVENT,
            KeyEvent::class.java
        ) ?: return

        // Solo se actúa al pulsar: los "key up" repetirían la acción.
        if (keyEvent.action != KeyEvent.ACTION_DOWN) return
        if (keyEvent.repeatCount > 0) return

        val command = MediaButtonCommand.fromKeyCode(keyEvent.keyCode)
        if (command == MediaCommand.NONE) {
            Log.d(TAG, "Botón de media no gestionado: ${keyEvent.keyCode}")
            return
        }

        if (execute(context, command)) {
            Log.d(TAG, "Acción de media aplicada: $command")
        }
    }

    /**
     * Aplica [command] al reproductor.
     *
     * @return `true` si hubo un reproductor sobre el que actuar.
     */
    private fun execute(context: Context, command: MediaCommand): Boolean {
        val application = context.applicationContext as? PlyrApp ?: return false
        val viewModel = application.playerViewModel

        when (command) {
            MediaCommand.PLAY -> viewModel.playPlayer()
            MediaCommand.PAUSE -> viewModel.pausePlayer()
            MediaCommand.PLAY_PAUSE -> {
                val player = viewModel.exoPlayer ?: return false
                if (player.isPlaying) viewModel.pausePlayer() else viewModel.playPlayer()
            }
            MediaCommand.NEXT -> viewModel.navigateToNext()
            MediaCommand.PREVIOUS -> viewModel.navigateToPrevious()
            // Respetan los incrementos de 10 s configurados al crear el reproductor.
            MediaCommand.FAST_FORWARD -> viewModel.exoPlayer?.seekForward() ?: return false
            MediaCommand.REWIND -> viewModel.exoPlayer?.seekBack() ?: return false
            MediaCommand.NONE -> return false
        }
        return true
    }

    private companion object {
        const val TAG = "MediaButtonReceiver"
    }
}
