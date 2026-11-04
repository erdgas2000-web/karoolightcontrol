package io.lightprofiles.karoo.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import timber.log.Timber

/**
 * The Karoo's own light sensor, handed on as plain lux readings.
 *
 * Only running while a profile actually reacts to darkness: it is a sensor on
 * a battery-powered device, and most rides never look at it.
 */
class AmbientLight(context: Context, private val onLux: (Float) -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private var running = false

    val isAvailable: Boolean get() = sensor != null

    fun start() {
        if (running) return
        val light = sensor
        if (light == null) {
            Timber.w("AmbientLight: this Karoo has no light sensor")
            return
        }
        sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_NORMAL)
        running = true
        Timber.d("AmbientLight: started")
    }

    fun stop() {
        if (!running) return
        sensorManager.unregisterListener(this)
        running = false
        Timber.d("AmbientLight: stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        onLux(event.values[0])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
