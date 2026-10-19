package com.mustwake.alarm.data.alarm

import com.mustwake.alarm.data.db.AlarmEntity
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextTriggerCalculatorTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun alarm(
        hour: Int = 7,
        minute: Int = 30,
        mask: Int = AlarmRepeatDays.NONE,
        enabled: Boolean = true,
    ) = AlarmEntity(
        hour = hour,
        minute = minute,
        repeatDaysMask = mask,
        goalSteps = 50,
        enabled = enabled,
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        java.time.LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()

    @Test
    fun `disabled alarm has no trigger`() {
        assertNull(NextTriggerCalculator.nextTrigger(alarm(enabled = false), at(2026, 10, 19, 6, 0), zone))
    }

    @Test
    fun `one-shot alarm later today fires today`() {
        val next = NextTriggerCalculator.nextTrigger(alarm(hour = 7, minute = 30), at(2026, 10, 19, 6, 0), zone)

        assertEquals(at(2026, 10, 19, 7, 30), next)
    }

    @Test
    fun `one-shot alarm already passed fires tomorrow`() {
        val next = NextTriggerCalculator.nextTrigger(alarm(hour = 7, minute = 30), at(2026, 10, 19, 8, 0), zone)

        assertEquals(at(2026, 10, 20, 7, 30), next)
    }

    @Test
    fun `one-shot alarm exactly now fires tomorrow`() {
        val next = NextTriggerCalculator.nextTrigger(alarm(hour = 7, minute = 30), at(2026, 10, 19, 7, 30), zone)

        assertEquals(at(2026, 10, 20, 7, 30), next)
    }

    @Test
    fun `repeat mask skips days that are not selected`() {
        // 2026-10-19 is a Monday; only Wednesday is selected.
        val next = NextTriggerCalculator.nextTrigger(
            alarm(hour = 7, minute = 0, mask = AlarmRepeatDays.WEDNESDAY),
            at(2026, 10, 19, 9, 0),
            zone,
        )

        assertEquals(at(2026, 10, 21, 7, 0), next)
    }

    @Test
    fun `repeat mask uses today when the time is still ahead`() {
        // Monday, 06:00 now; Monday/Friday selected -> today 07:00.
        val next = NextTriggerCalculator.nextTrigger(
            alarm(hour = 7, minute = 0, mask = AlarmRepeatDays.MONDAY or AlarmRepeatDays.FRIDAY),
            at(2026, 10, 19, 6, 0),
            zone,
        )

        assertEquals(at(2026, 10, 19, 7, 0), next)
    }

    @Test
    fun `repeat mask rolls to next week when today's time passed`() {
        // Monday 08:00 now, Monday-only -> next Monday.
        val next = NextTriggerCalculator.nextTrigger(
            alarm(hour = 7, minute = 0, mask = AlarmRepeatDays.MONDAY),
            at(2026, 10, 19, 8, 0),
            zone,
        )

        assertEquals(at(2026, 10, 26, 7, 0), next)
    }

    @Test
    fun `all days mask fires next matching day`() {
        val next = NextTriggerCalculator.nextTrigger(
            alarm(hour = 6, minute = 15, mask = AlarmRepeatDays.EVERY_DAY),
            at(2026, 10, 19, 6, 20),
            zone,
        )

        assertEquals(at(2026, 10, 20, 6, 15), next)
    }

    @Test
    fun `sunday is the seventh bit`() {
        val next = NextTriggerCalculator.nextTrigger(
            alarm(hour = 9, minute = 0, mask = AlarmRepeatDays.SUNDAY),
            at(2026, 10, 19, 10, 0),
            zone,
        )

        // 2026-10-19 is Monday, next Sunday is 2026-10-25.
        assertEquals(at(2026, 10, 25, 9, 0), next)
    }
}
