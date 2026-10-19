package com.mustwake.alarm.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsConstraintsTest {

    @Test
    fun `defaults match the spec`() {
        val settings = AppSettings()
        assertFalse(settings.floatingBottomBarEnabled)
        assertFalse(settings.liquidGlassEnabled)
        assertEquals(50, settings.defaultGoalSteps)
        assertEquals(5, settings.snoozeMinutes)
        assertEquals(3, settings.maxSnoozes)
        assertEquals(30, settings.shakeCount)
    }

    @Test
    fun `goal steps range is 10 to 500`() {
        assertFalse(SettingsConstraints.isValidGoalSteps(9))
        assertTrue(SettingsConstraints.isValidGoalSteps(10))
        assertTrue(SettingsConstraints.isValidGoalSteps(500))
        assertFalse(SettingsConstraints.isValidGoalSteps(501))
    }

    @Test
    fun `snooze minutes range is 1 to 15`() {
        assertFalse(SettingsConstraints.isValidSnoozeMinutes(0))
        assertTrue(SettingsConstraints.isValidSnoozeMinutes(1))
        assertTrue(SettingsConstraints.isValidSnoozeMinutes(15))
        assertFalse(SettingsConstraints.isValidSnoozeMinutes(16))
    }

    @Test
    fun `max snoozes range is 0 to 5`() {
        assertTrue(SettingsConstraints.isValidMaxSnoozes(0))
        assertTrue(SettingsConstraints.isValidMaxSnoozes(5))
        assertFalse(SettingsConstraints.isValidMaxSnoozes(-1))
        assertFalse(SettingsConstraints.isValidMaxSnoozes(6))
    }

    @Test
    fun `shake count range is 10 to 100`() {
        assertFalse(SettingsConstraints.isValidShakeCount(9))
        assertTrue(SettingsConstraints.isValidShakeCount(10))
        assertTrue(SettingsConstraints.isValidShakeCount(100))
        assertFalse(SettingsConstraints.isValidShakeCount(101))
    }

    @Test
    fun `invalid persisted values fall back to defaults`() {
        assertEquals(50, SettingsConstraints.validGoalStepsOrDefault(0))
        assertEquals(50, SettingsConstraints.validGoalStepsOrDefault(501))
        assertEquals(50, SettingsConstraints.validGoalStepsOrDefault(Int.MIN_VALUE))
        assertEquals(5, SettingsConstraints.validSnoozeMinutesOrDefault(99))
        assertEquals(3, SettingsConstraints.validMaxSnoozesOrDefault(-2))
        assertEquals(30, SettingsConstraints.validShakeCountOrDefault(311))
    }

    @Test
    fun `valid persisted values are kept`() {
        assertEquals(120, SettingsConstraints.validGoalStepsOrDefault(120))
        assertEquals(12, SettingsConstraints.validSnoozeMinutesOrDefault(12))
        assertEquals(0, SettingsConstraints.validMaxSnoozesOrDefault(0))
        assertEquals(75, SettingsConstraints.validShakeCountOrDefault(75))
    }

    @Test
    fun `sanitized replaces only out of range fields`() {
        val dirty = AppSettings(
            floatingBottomBarEnabled = true,
            liquidGlassEnabled = true,
            defaultGoalSteps = 9999,
            snoozeMinutes = 0,
            maxSnoozes = 9,
            shakeCount = 1,
        )

        val clean = dirty.sanitized()

        assertTrue(clean.floatingBottomBarEnabled)
        assertTrue(clean.liquidGlassEnabled)
        assertEquals(50, clean.defaultGoalSteps)
        assertEquals(5, clean.snoozeMinutes)
        assertEquals(3, clean.maxSnoozes)
        assertEquals(30, clean.shakeCount)
    }

    @Test
    fun `require helpers reject invalid values`() {
        val failures = listOf<() -> Unit>(
            { SettingsConstraints.requireValidGoalSteps(9) },
            { SettingsConstraints.requireValidSnoozeMinutes(0) },
            { SettingsConstraints.requireValidMaxSnoozes(6) },
            { SettingsConstraints.requireValidShakeCount(101) },
        )

        failures.forEach { call ->
            val error = runCatching(call).exceptionOrNull()
            assertTrue("expected IllegalArgumentException but was $error", error is IllegalArgumentException)
        }
    }
}
