package com.mustwake.alarm.data.sensor

/**
 * Counts shake spikes for the degraded stop path.
 *
 * [onMagnitude] accepts a spike only when it exceeds [thresholdG] and at least [minIntervalMs]
 * passed since the previous accepted spike, so one physical shake is not counted many times.
 * The count saturates at [target].
 */
class ShakeCounter(
    val target: Int,
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val thresholdG: Float = DEFAULT_THRESHOLD_G,
) {
    var count: Int = 0
        private set

    private var lastAcceptedAt: Long? = null

    val isArmed: Boolean get() = count >= target

    fun onMagnitude(magnitude: Float, nowMs: Long): Int {
        if (magnitude < thresholdG) return count
        val last = lastAcceptedAt
        if (last != null && nowMs - last < minIntervalMs) return count
        lastAcceptedAt = nowMs
        if (count < target) count++
        return count
    }

    fun reset() {
        count = 0
        lastAcceptedAt = null
    }

    companion object {
        /** ~1.2 g above rest gravity; a deliberate shake easily exceeds it. */
        const val DEFAULT_THRESHOLD_G = 12f
        const val DEFAULT_MIN_INTERVAL_MS = 150L
    }
}
