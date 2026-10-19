package com.mustwake.alarm.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mustwake.alarm.data.sensor.StepBaselineTracker

/**
 * Session-scoped step source.
 *
 * Prefers `TYPE_STEP_COUNTER` (relative to the reading at [start], so pre-alarm steps are not
 * counted) and falls back to `TYPE_STEP_DETECTOR` (one event = one step). Registration fails
 * when `ACTIVITY_RECOGNITION` is denied, in which case [Mode.UNAVAILABLE] is returned and the
 * caller switches to the shake fallback.
 */
class StepSensorRepository(
    context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper()),
) : SensorEventListener {

    enum class Mode { STEP_COUNTER, STEP_DETECTOR, UNAVAILABLE }

    private val sensorManager: SensorManager? = context.getSystemService(SensorManager::class.java)
    private val baseline = StepBaselineTracker()

    private var callback: ((Int) -> Unit)? = null
    private var registered = false
    private var detectorSteps = 0

    var mode: Mode = Mode.UNAVAILABLE
        private set

    fun start(onSteps: (Int) -> Unit): Mode {
        stop()
        callback = onSteps
        baseline.reset()
        detectorSteps = 0

        val counter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (counter != null &&
            sensorManager.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL, handler)
        ) {
            mode = Mode.STEP_COUNTER
            registered = true
            Log.i(TAG, "step tracking with TYPE_STEP_COUNTER")
            return mode
        }

        val detector = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (detector != null &&
            sensorManager.registerListener(this, detector, SensorManager.SENSOR_DELAY_NORMAL, handler)
        ) {
            mode = Mode.STEP_DETECTOR
            registered = true
            Log.i(TAG, "step tracking with TYPE_STEP_DETECTOR")
            return mode
        }

        mode = Mode.UNAVAILABLE
        Log.w(TAG, "no step sensor available (hardware or ACTIVITY_RECOGNITION denied)")
        return mode
    }

    fun stop() {
        if (registered) {
            runCatching { sensorManager?.unregisterListener(this) }
        }
        registered = false
        callback = null
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> callback?.invoke(baseline.stepsFrom(event.values[0]))
            Sensor.TYPE_STEP_DETECTOR -> {
                detectorSteps += 1
                callback?.invoke(detectorSteps)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val TAG = "MustWake"
    }
}
