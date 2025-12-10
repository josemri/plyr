package com.plyr.utils

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * LightSensorDetector - Detecta el nivel de luz ambiental para cambiar el tema automáticamente
 *
 * Usa el sensor de luz del dispositivo para determinar si el entorno es oscuro o claro.
 */
class LightSensorDetector(
    private val context: Context,
    private val onLightLevelChanged: (isDark: Boolean) -> Unit
) : SensorEventListener {

    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var isListening = false
    private var lastIsDark: Boolean? = null

    companion object {
        private const val TAG = "LightSensorDetector"
        // Umbral de luz en lux - por debajo de esto se considera oscuro
        // Valores típicos:
        // - Noche/habitación oscura: 0-10 lux
        // - Habitación con luz tenue: 10-50 lux
        // - Oficina/interior iluminado: 100-500 lux
        // - Exterior nublado: 1000-5000 lux
        // - Exterior soleado: 10000-100000 lux
        private const val DARK_THRESHOLD_LUX = 50f

        // Histéresis para evitar cambios frecuentes cerca del umbral
        private const val HYSTERESIS_LUX = 30f
    }

    fun start() {
        if (isListening) {
            Log.d(TAG, "start() - Already listening")
            return
        }

        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)

        Log.d(TAG, "start() - lightSensor: $lightSensor")

        if (lightSensor == null) {
            Log.e(TAG, "start() - Light sensor not available!")
            return
        }

        // Usamos SENSOR_DELAY_NORMAL ya que no necesitamos actualizaciones frecuentes
        lightSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        isListening = true
        Log.d(TAG, "start() - Started listening to light sensor")
    }

    fun stop() {
        Log.d(TAG, "stop() called")
        sensorManager?.unregisterListener(this)
        isListening = false
        lastIsDark = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_LIGHT) return

        val lux = event.values[0]

        // Log cada lectura del sensor
        Log.d(TAG, "Lux: $lux, Threshold: $DARK_THRESHOLD_LUX, lastIsDark: $lastIsDark")

        // Aplicar histéresis para evitar cambios frecuentes
        val isDark = when {
            lastIsDark == null -> lux < DARK_THRESHOLD_LUX
            lastIsDark == true -> lux < (DARK_THRESHOLD_LUX + HYSTERESIS_LUX)
            else -> lux < (DARK_THRESHOLD_LUX - HYSTERESIS_LUX)
        }

        Log.d(TAG, "isDark calculated: $isDark (lux=$lux)")

        // Solo notificar si cambió el estado
        if (isDark != lastIsDark) {
            Log.d(TAG, ">>> CAMBIO DE TEMA: isDark=$isDark (anterior: $lastIsDark)")
            lastIsDark = isDark
            onLightLevelChanged(isDark)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d(TAG, "onAccuracyChanged: sensor=${sensor?.name}, accuracy=$accuracy")
    }
}
