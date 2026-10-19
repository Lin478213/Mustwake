package com.mustwake.alarm.data.alarm

import com.mustwake.alarm.data.db.AlarmEntity
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure next-trigger calculation shared by scheduling and tests.
 *
 * One-shot alarms (mask 0) fire at the next occurrence of [AlarmEntity.hour]/[AlarmEntity.minute],
 * today if it is still in the future, otherwise tomorrow. Repeating alarms fire at the next
 * matching weekday, strictly after [now].
 */
object NextTriggerCalculator {

    fun nextTrigger(
        alarm: AlarmEntity,
        now: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Instant? {
        if (!alarm.enabled) return null

        val nowZoned = now.atZone(zone)
        val time = LocalTime.of(alarm.hour, alarm.minute)

        if (alarm.repeatDaysMask == AlarmRepeatDays.NONE) {
            var candidate = ZonedDateTime.of(nowZoned.toLocalDate(), time, zone)
            if (!candidate.toInstant().isAfter(now)) {
                candidate = ZonedDateTime.of(nowZoned.toLocalDate().plusDays(1), time, zone)
            }
            return candidate.toInstant()
        }

        for (dayOffset in 0..7) {
            val date = nowZoned.toLocalDate().plusDays(dayOffset.toLong())
            if (!AlarmRepeatDays.isDayEnabled(alarm.repeatDaysMask, date.dayOfWeek.value)) continue
            val candidate = ZonedDateTime.of(date, time, zone).toInstant()
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }
}
