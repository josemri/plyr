package com.plyr.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent

/**
 * MediaButtonReceiver - Maneja los eventos de botones de media desde auriculares inalámbricos
 *
 * Este receiver intercepta las acciones de botones de auriculares como:
 * - Play/Pause (botón central)
 * - Siguiente canción (doble click o botón específico)
 * - Canción anterior (triple click o botón específico)
 * - Stop, Fast Forward, Rewind, etc.
 */
class MediaButtonReceiver(private val onKeyEvent: ((KeyEvent) -> Boolean)? = null) : BroadcastReceiver() {

    companion object {
        private const val TAG = "MediaButtonReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)

            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                Log.d(TAG, "🎧 Media button event received: ${keyEvent.keyCode}")

                // Si hay un callback, usarlo, sino manejar localmente
                val handled = onKeyEvent?.invoke(keyEvent) ?: handleKeyEvent(context, keyEvent)

                if (handled) {
                    // Marcar como manejado para que otros receivers no lo procesen
                    abortBroadcast()
                }
            }
        } else if (intent?.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            Log.d(TAG, "🔇 Audio becoming noisy - pausando reproducción")
            handleAudioBecomingNoisy(context)
        }
    }

    private fun handleKeyEvent(context: Context?, keyEvent: KeyEvent): Boolean {
        // Si no hay callback personalizado, manejar enviando intents al servicio
        context?.let { ctx ->
            val serviceIntent = Intent(ctx, com.plyr.service.MusicService::class.java)

            when (keyEvent.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    serviceIntent.action = "ACTION_PLAY"
                    ctx.startForegroundService(serviceIntent)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    serviceIntent.action = "ACTION_PAUSE"
                    ctx.startForegroundService(serviceIntent)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    // Determinar si debe ser play o pause
                    // Por simplicidad, alternamos - el servicio manejará la lógica
                    serviceIntent.action = "ACTION_PLAY_PAUSE"
                    ctx.startForegroundService(serviceIntent)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                    serviceIntent.action = "ACTION_NEXT"
                    ctx.startForegroundService(serviceIntent)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                    serviceIntent.action = "ACTION_PREV"
                    ctx.startForegroundService(serviceIntent)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    serviceIntent.action = "ACTION_STOP"
                    ctx.startForegroundService(serviceIntent)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    serviceIntent.action = "ACTION_FAST_FORWARD"
                    ctx.startForegroundService(serviceIntent)
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    serviceIntent.action = "ACTION_REWIND"
                    ctx.startForegroundService(serviceIntent)
                    return true
                }
                else -> {
                    Log.d(TAG, "🤷 Botón de media no manejado: ${keyEvent.keyCode}")
                    return false
                }
            }
        }
        return false
    }

    private fun handleAudioBecomingNoisy(context: Context?) {
        // Cuando el audio se vuelve "ruidoso" (ej: auriculares desconectados)
        // pausar automáticamente la reproducción
        context?.let { ctx ->
            val serviceIntent = Intent(ctx, com.plyr.service.MusicService::class.java)
            serviceIntent.action = "ACTION_PAUSE"
            ctx.startForegroundService(serviceIntent)
        }
    }
}
