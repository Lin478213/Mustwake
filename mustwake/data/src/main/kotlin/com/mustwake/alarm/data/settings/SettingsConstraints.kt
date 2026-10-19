package com.mustwake.alarm.data.settings

/**
 * Single source of truth for user-facing setting ranges and defaults.
 *
 * [MIN_GOAL_STEPS] / [MAX_GOAL_STEPS] are also used by alarm validation, because the
 * step target on an alarm shares the same product limits as the default step target.
 */
object SettingsConstraints {
    const val MIN_GOAL_STEPS = 10
    const val MAX_GOAL_STEPS = 500
    const val DEFAULT_GOAL_STEPS = 50

    const val MIN_SNOOZE_MINUTES = 1
    const val MAX_SNOOZE_MINUTES = 15
    const val DEFAULT_SNOOZE_MINUTES = 5

    const val MIN_MAX_SNOOZES = 0
    const val MAX_MAX_SNOOZES = 5
    const val DEFAULT_MAX_SNOOZES = 3

    const val MIN_SHAKE_COUNT = 10
    const val MAX_SHAKE_COUNT = 100
    const val DEFAULT_SHAKE_COUNT = 30

    fun isValidGoalSteps(value: Int): Boolean = value in MIN_GOAL_STEPS..MAX_GOAL_STEPS

    fun isValidSnoozeMinutes(value: Int): Boolean = value in MIN_SNOOZE_MINUTES..MAX_SNOOZE_MINUTES

    fun isValidMaxSnoozes(value: Int): Boolean = value in MIN_MAX_SNOOZES..MAX_MAX_SNOOZES

    fun isValidShakeCount(value: Int): Boolean = value in MIN_SHAKE_COUNT..MAX_SHAKE_COUNT

    /** Used when reading persisted values: corrupted input falls back to the default. */
    fun validGoalStepsOrDefault(value: Int): Int =
        if (isValidGoalSteps(value)) value else DEFAULT_GOAL_STEPS

    fun validSnoozeMinutesOrDefault(value: Int): Int =
        if (isValidSnoozeMinutes(value)) value else DEFAULT_SNOOZE_MINUTES

    fun validMaxSnoozesOrDefault(value: Int): Int =
        if (isValidMaxSnoozes(value)) value else DEFAULT_MAX_SNOOZES

    fun validShakeCountOrDefault(value: Int): Int =
        if (isValidShakeCount(value)) value else DEFAULT_SHAKE_COUNT

    /** Used when writing: invalid input is rejected instead of being stored. */
    fun requireValidGoalSteps(value: Int): Int {
        require(isValidGoalSteps(value)) {
            "goalSteps must be within $MIN_GOAL_STEPS..$MAX_GOAL_STEPS, was $value"
        }
        return value
    }

    fun requireValidSnoozeMinutes(value: Int): Int {
        require(isValidSnoozeMinutes(value)) {
            "snoozeMinutes must be within $MIN_SNOOZE_MINUTES..$MAX_SNOOZE_MINUTES, was $value"
        }
        return value
    }

    fun requireValidMaxSnoozes(value: Int): Int {
        require(isValidMaxSnoozes(value)) {
            "maxSnoozes must be within $MIN_MAX_SNOOZES..$MAX_MAX_SNOOZES, was $value"
        }
        return value
    }

    fun requireValidShakeCount(value: Int): Int {
        require(isValidShakeCount(value)) {
            "shakeCount must be within $MIN_SHAKE_COUNT..$MAX_SHAKE_COUNT, was $value"
        }
        return value
    }
}
