package com.plyr.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * ShakeDetector - Detecta cuando el usuario agita el teléfono
 *
 * Acciones disponibles:
 * - OFF: Deshabilitado
 * - NEXT: Pasar a la siguiente canción
 * - PREVIOUS: Volver a la canción anterior
 * - PLAY_PAUSE: Reproducir/Pausar
 * - ASSISTANT: Activar el asistente de voz
 */
class ShakeDetector(
    private val context: Context,
    private val onShakeDetected: (String) -> Unit
) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isListening = false

    private var lastShakeTime: Long = 0
    private var lastX: Float = 0f
    private var lastY: Float = 0f
    private var lastZ: Float = 0f
    private var lastUpdate: Long = 0

    companion object {
        // Umbral de sensibilidad para detectar el shake
        private const val SHAKE_THRESHOLD = 7000
        // Tiempo mínimo entre shakes para evitar múltiples detecciones
        private const val SHAKE_COOLDOWN_MS = 1000L
        // Intervalo mínimo entre actualizaciones del sensor (aumentado para reducir carga)
        private const val UPDATE_INTERVAL_MS = 100

        // Acciones disponibles
        const val ACTION_OFF = "off"
        const val ACTION_NEXT = "next"
        const val ACTION_PREVIOUS = "previous"
        const val ACTION_PLAY_PAUSE = "play_pause"
        const val ACTION_ASSISTANT = "assistant"
    }

    /**
     * Inicia la detección de shake
     */
    fun start() {
        val action = Config.getShakeAction(context)

        if (action == ACTION_OFF) {
            return
        }

        if (isListening) {
            return
        }

        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            return
        }

        val registered = sensorManager?.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_GAME
        ) ?: false

        isListening = registered
    }

    /**
     * Detiene la detección de shake
     */
    fun stop() {
        sensorManager?.unregisterListener(this)
        isListening = false
    }

    /**
     * Reinicia el detector con la configuración actual
     */
    fun restart() {
        stop()
        start()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val currentTime = System.currentTimeMillis()

        // Solo procesar si ha pasado suficiente tiempo desde la última actualización
        if ((currentTime - lastUpdate) < UPDATE_INTERVAL_MS) return

        val diffTime = currentTime - lastUpdate
        lastUpdate = currentTime

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val deltaX = x - lastX
        val deltaY = y - lastY
        val deltaZ = z - lastZ

        lastX = x
        lastY = y
        lastZ = z

        // Calcular la velocidad del movimiento
        val speed = sqrt((deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ).toDouble()) / diffTime * 10000

        if (speed > SHAKE_THRESHOLD) {
            val timeSinceLastShake = currentTime - lastShakeTime

            // Verificar cooldown para evitar múltiples detecciones
            if (timeSinceLastShake > SHAKE_COOLDOWN_MS) {
                lastShakeTime = currentTime

                val action = Config.getShakeAction(context)

                if (action != ACTION_OFF) {
                    onShakeDetected(action)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No action needed
    }
}
