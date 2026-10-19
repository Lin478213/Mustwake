package com.mustwake.alarm.data.alarm

import com.mustwake.alarm.data.settings.SettingsConstraints

/** Field constraints for [com.mustwake.alarm.data.db.AlarmEntity]. */
object AlarmConstraints {
    const val MIN_HOUR = 0
    const val MAX_HOUR = 23
    const val MIN_MINUTE = 0
    const val MAX_MINUTE = 59
    const val LABEL_MAX_LENGTH = 40

    fun requireValidHour(hour: Int): Int {
        require(hour in MIN_HOUR..MAX_HOUR) { "hour must be within $MIN_HOUR..$MAX_HOUR, was $hour" }
        return hour
    }

    fun requireValidMinute(minute: Int): Int {
        require(minute in MIN_MINUTE..MAX_MINUTE) {
            "minute must be within $MIN_MINUTE..$MAX_MINUTE, was $minute"
        }
        return minute
    }

    fun requireValidRepeatMask(mask: Int): Int {
        require(AlarmRepeatDays.isValidMask(mask)) {
            "repeatDaysMask must be within 0..${AlarmRepeatDays.MAX_MASK}, was $mask"
        }
        return mask
    }

    fun requireValidGoalSteps(steps: Int): Int = SettingsConstraints.requireValidGoalSteps(steps)

    fun requireValidLabel(label: String): String {
        val trimmed = label.trim()
        require(trimmed.length <= LABEL_MAX_LENGTH) {
            "label must be at most $LABEL_MAX_LENGTH characters, was ${trimmed.length}"
        }
        return trimmed
    }
}
