package com.plyr.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs

/**
 * OrientationDetector - Detecta el giro del teléfono como un control knob
 *
 * Funciona detectando el movimiento de giro acumulado del dispositivo:
 * - Girar a la IZQUIERDA: ejecuta acción izquierda (vol-, previous)
 * - Girar a la DERECHA: ejecuta acción derecha (vol+, next)
 *
 * El usuario configura qué acción quiere:
 * - OFF: Deshabilitado
 * - VOLUME: Giro izq = bajar volumen, giro der = subir volumen
 * - SKIP: Giro izq = canción anterior, giro der = siguiente canción
 */
class OrientationDetector(
    private val context: Context,
    private val onLeftAction: () -> Unit,
    private val onRightAction: () -> Unit
) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null
    private var isListening = false

    private var lastActionTime: Long = 0

    // Acumulador de rotación
    private var accumulatedRotation: Float = 0f
    private var lastTimestamp: Long = 0

    companion object {
        private const val TAG = "OrientationDetector"
        // Tiempo mínimo entre acciones
        private const val ACTION_COOLDOWN_MS = 500L
        // Rotación acumulada necesaria para activar una acción (en grados)
        private const val ROTATION_THRESHOLD = 15f
        // Umbral mínimo de velocidad para considerar movimiento intencional (rad/s)
        private const val MIN_VELOCITY_THRESHOLD = 0.25f
        // Tiempo máximo para acumular rotación antes de resetear (ms)
        private const val ACCUMULATION_TIMEOUT_MS = 600L

        // Acciones disponibles
        const val ACTION_OFF = "off"
        const val ACTION_VOLUME = "volume"
        const val ACTION_SKIP = "skip"
    }

    fun start() {
        val action = Config.getOrientationAction(context)

        if (action == ACTION_OFF) {
            return
        }

        if (isListening) {
            return
        }

        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        if (gyroscope == null) {
            Log.e(TAG, "Gyroscope not available!")
            return
        }

        accumulatedRotation = 0f
        lastTimestamp = 0

        gyroscope?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        isListening = true
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        isListening = false
        accumulatedRotation = 0f
        lastTimestamp = 0
    }

    fun restart() {
        stop()
        start()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_GYROSCOPE) return

        val currentTime = System.currentTimeMillis()

        // Velocidad angular en el eje Z (yaw - rotación sobre mesa cuando el móvil está horizontal)
        // event.values[0] = rotación en X (pitch)
        // event.values[1] = rotación en Y (roll)
        // event.values[2] = rotación en Z (yaw) - este es el que queremos para rotar sobre mesa
        val angularVelocityZ = event.values[2] // rad/s

        // Ignorar movimientos muy pequeños (ruido)
        if (abs(angularVelocityZ) < MIN_VELOCITY_THRESHOLD) {
            // Si no hay movimiento significativo por un tiempo, resetear acumulador
            if (lastTimestamp > 0 && currentTime - lastTimestamp > ACCUMULATION_TIMEOUT_MS) {
                accumulatedRotation = 0f
            }
            return
        }

        // Calcular tiempo transcurrido
        if (lastTimestamp == 0L) {
            lastTimestamp = currentTime
            return
        }

        val dt = (currentTime - lastTimestamp) / 1000f // segundos
        lastTimestamp = currentTime

        // Evitar saltos de tiempo muy grandes
        if (dt > 0.5f) {
            return
        }

        // Acumular rotación (convertir a grados)
        val rotationDelta = Math.toDegrees(angularVelocityZ.toDouble()).toFloat() * dt
        accumulatedRotation += rotationDelta

        // Verificar cooldown
        if (currentTime - lastActionTime < ACTION_COOLDOWN_MS) {
            return
        }

        val action = Config.getOrientationAction(context)
        if (action == ACTION_OFF) {
            return
        }

        // Detectar si se acumuló suficiente rotación
        if (abs(accumulatedRotation) > ROTATION_THRESHOLD) {
            lastActionTime = currentTime

            if (accumulatedRotation > 0) {
                // Giró a la DERECHA (sentido horario visto desde arriba)
                onRightAction()
            } else {
                // Giró a la IZQUIERDA (sentido antihorario visto desde arriba)
                onLeftAction()
            }

            // Resetear acumulador después de la acción
            accumulatedRotation = 0f
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No action needed
    }
}
