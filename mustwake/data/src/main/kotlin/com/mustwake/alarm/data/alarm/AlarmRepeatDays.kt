package com.mustwake.alarm.data.alarm

/** Repeat-day bitmask helpers. Monday is bit 0, Sunday is bit 6. */
object AlarmRepeatDays {
    const val NONE = 0
    const val MONDAY = 1
    const val TUESDAY = 1 shl 1
    const val WEDNESDAY = 1 shl 2
    const val THURSDAY = 1 shl 3
    const val FRIDAY = 1 shl 4
    const val SATURDAY = 1 shl 5
    const val SUNDAY = 1 shl 6

    const val MAX_MASK = 0x7F
    const val EVERY_DAY = MAX_MASK

    /** Bit for [dayOfWeek] using [java.time.DayOfWeek] numbering (1 = Monday .. 7 = Sunday). */
    fun bitFor(dayOfWeek: Int): Int {
        require(dayOfWeek in 1..7) { "dayOfWeek must be within 1..7, was $dayOfWeek" }
        return 1 shl (dayOfWeek - 1)
    }

    fun isValidMask(mask: Int): Boolean = mask in 0..MAX_MASK

    fun isDayEnabled(mask: Int, dayOfWeek: Int): Boolean = mask and bitFor(dayOfWeek) != 0
}
