package com.mustwake.alarm.data.settings

/**
 * Immutable snapshot of all user settings.
 *
 * Defaults match the spec: both toggles off, 50 steps, 5 minute snooze, at most 3 snoozes,
 * 30 shakes for the degraded stop path.
 */
data class AppSettings(
    val floatingBottomBarEnabled: Boolean = false,
    val liquidGlassEnabled: Boolean = false,
    val defaultGoalSteps: Int = SettingsConstraints.DEFAULT_GOAL_STEPS,
    val snoozeMinutes: Int = SettingsConstraints.DEFAULT_SNOOZE_MINUTES,
    val maxSnoozes: Int = SettingsConstraints.DEFAULT_MAX_SNOOZES,
    val shakeCount: Int = SettingsConstraints.DEFAULT_SHAKE_COUNT,
    /** Whether the first-run permission guide has been shown and dismissed. */
    val onboardingCompleted: Boolean = false,
) {
    /**
     * Returns a copy where every numeric field is guaranteed to be within its valid range.
     *
     * Applied on read so that corrupted or hand-edited preferences can never leak invalid
     * values into scheduling or step-tracking logic.
     */
    fun sanitized(): AppSettings = copy(
        defaultGoalSteps = SettingsConstraints.validGoalStepsOrDefault(defaultGoalSteps),
        snoozeMinutes = SettingsConstraints.validSnoozeMinutesOrDefault(snoozeMinutes),
        maxSnoozes = SettingsConstraints.validMaxSnoozesOrDefault(maxSnoozes),
        shakeCount = SettingsConstraints.validShakeCountOrDefault(shakeCount),
    )
}
