package com.mustwake.alarm.data.sensor

/**
 * Converts raw `TYPE_STEP_COUNTER` readings (total since boot) into steps since the alarm started.
 *
 * The first reading becomes the baseline, so steps taken before the alarm are never counted.
 * A counter rollback (sensor reset / reboot) yields 0 instead of a negative step count.
 */
class StepBaselineTracker {

    private var baseline: Float? = null

    val hasBaseline: Boolean get() = baseline != null

    fun stepsFrom(raw: Float): Int {
        val base = baseline ?: raw.also { baseline = it }
        return (raw - base).toInt().coerceAtLeast(0)
    }

    fun reset() {
        baseline = null
    }
}
