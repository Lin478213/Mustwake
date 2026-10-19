package com.mustwake.alarm.data.stop

/** How a ringing session was stopped. */
enum class StopReason {
    /** Target step count reached. */
    STEP_GOAL,

    /** 10 second long press; counts as not completed. */
    EMERGENCY,

    /** Sensor fallback: required shakes plus confirmation. */
    DEGRADED,
}

sealed interface StopDecision {
    data object Continue : StopDecision

    data class Stop(val reason: StopReason) : StopDecision
}

/**
 * Input snapshot for one stop evaluation.
 *
 * [degradedMode] is true when no step sensor could be registered (missing hardware or denied
 * `ACTIVITY_RECOGNITION`); in that mode the emergency long press only counts after
 * [shakeTarget] shakes.
 */
data class StopPolicyInput(
    val currentSteps: Int,
    val goalSteps: Int,
    val emergencyRequested: Boolean = false,
    val degradedMode: Boolean = false,
    val shakeCount: Int = 0,
    val shakeTarget: Int = DEFAULT_SHAKE_TARGET,
    val snoozeCount: Int = 0,
    val maxSnoozes: Int = DEFAULT_MAX_SNOOZES,
) {
    companion object {
        const val DEFAULT_SHAKE_TARGET = 30
        const val DEFAULT_MAX_SNOOZES = 3
    }
}

/**
 * Pure stop rules from the spec:
 *
 * 1. reaching the step goal completes the session;
 * 2. a long-press emergency stop is accepted immediately in normal mode;
 * 3. in degraded mode the long press is only accepted after enough shakes, and is recorded as
 *    DEGRADED instead of EMERGENCY.
 *
 * Snooze is independent of stopping and only limited by [canSnooze].
 */
object StopPolicy {

    fun evaluate(input: StopPolicyInput): StopDecision {
        require(input.goalSteps > 0) { "goalSteps must be positive, was ${input.goalSteps}" }
        require(input.currentSteps >= 0) { "currentSteps must not be negative" }
        require(input.shakeCount >= 0) { "shakeCount must not be negative" }
        require(input.shakeTarget > 0) { "shakeTarget must be positive" }
        require(input.snoozeCount >= 0) { "snoozeCount must not be negative" }
        require(input.maxSnoozes >= 0) { "maxSnoozes must not be negative" }

        if (input.currentSteps >= input.goalSteps) {
            return StopDecision.Stop(StopReason.STEP_GOAL)
        }

        if (input.emergencyRequested) {
            if (input.degradedMode && input.shakeCount < input.shakeTarget) {
                return StopDecision.Continue
            }
            return StopDecision.Stop(
                if (input.degradedMode) StopReason.DEGRADED else StopReason.EMERGENCY,
            )
        }

        return StopDecision.Continue
    }

    fun remainingSteps(currentSteps: Int, goalSteps: Int): Int =
        (goalSteps - currentSteps).coerceAtLeast(0)

    fun canSnooze(snoozeCount: Int, maxSnoozes: Int): Boolean {
        require(snoozeCount >= 0) { "snoozeCount must not be negative" }
        require(maxSnoozes >= 0) { "maxSnoozes must not be negative" }
        return snoozeCount < maxSnoozes
    }
}
