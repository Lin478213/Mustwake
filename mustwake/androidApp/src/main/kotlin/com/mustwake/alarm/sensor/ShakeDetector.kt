package com.mustwake.alarm.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.mustwake.alarm.data.sensor.ShakeCounter
import kotlin.math.sqrt

/**
 * Accelerometer shake counter for the degraded stop path.
 *
 * Reports the accepted shake count (saturating at [target]) through [onShake]; the counting and
 * debouncing logic lives in [ShakeCounter] so it is covered by JVM tests.
 */
class ShakeDetector(
    context: Context,
    target: Int,
    private val onShake: (Int) -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : SensorEventListener {

    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val counter = ShakeCounter(target)
    private var registered = false
    private var lastReported = 0

    val count: Int get() = counter.count

    fun start(): Boolean {
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            Log.w(TAG, "no accelerometer for shake fallback")
            return false
        }
        registered = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_GAME,
            handler,
        )
        return registered
    }

    fun stop() {
        if (registered) {
            runCatching { sensorManager?.unregisterListener(this) }
        }
        registered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val accepted = counter.onMagnitude(magnitude, SystemClock.elapsedRealtime())
        if (accepted != lastReported) {
            lastReported = accepted
            onShake(accepted)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val TAG = "MustWake"
    }
}
