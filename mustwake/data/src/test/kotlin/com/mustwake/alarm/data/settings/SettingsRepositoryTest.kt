package com.mustwake.alarm.data.settings

import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsRepositoryTest {

    private lateinit var dataStore: InMemoryPreferencesDataStore
    private lateinit var repository: SettingsRepository

    @Before
    fun setUp() {
        dataStore = InMemoryPreferencesDataStore()
        repository = SettingsRepository(dataStore)
    }

    @Test
    fun `empty store returns spec defaults`() = runTest {
        val settings = repository.currentSettings()

        assertFalse(settings.floatingBottomBarEnabled)
        assertFalse(settings.liquidGlassEnabled)
        assertEquals(50, settings.defaultGoalSteps)
        assertEquals(5, settings.snoozeMinutes)
        assertEquals(3, settings.maxSnoozes)
        assertEquals(30, settings.shakeCount)
    }

    @Test
    fun `two toggles are independent and persist`() = runTest {
        repository.setFloatingBottomBarEnabled(true)
        assertTrue(repository.currentSettings().floatingBottomBarEnabled)
        assertFalse(repository.currentSettings().liquidGlassEnabled)

        repository.setLiquidGlassEnabled(true)
        assertTrue(repository.currentSettings().liquidGlassEnabled)
        assertTrue(repository.currentSettings().floatingBottomBarEnabled)

        repository.setFloatingBottomBarEnabled(false)
        assertFalse(repository.currentSettings().floatingBottomBarEnabled)
        assertTrue(repository.currentSettings().liquidGlassEnabled)
    }

    @Test
    fun `numeric settings persist`() = runTest {
        repository.setDefaultGoalSteps(120)
        repository.setSnoozeMinutes(10)
        repository.setMaxSnoozes(1)
        repository.setShakeCount(40)

        val settings = repository.currentSettings()

        assertEquals(120, settings.defaultGoalSteps)
        assertEquals(10, settings.snoozeMinutes)
        assertEquals(1, settings.maxSnoozes)
        assertEquals(40, settings.shakeCount)
    }

    @Test
    fun `invalid writes are rejected and keep previous values`() = runTest {
        repository.setDefaultGoalSteps(80)

        val failures = listOf<suspend () -> Unit>(
            { repository.setDefaultGoalSteps(9) },
            { repository.setDefaultGoalSteps(501) },
            { repository.setSnoozeMinutes(0) },
            { repository.setSnoozeMinutes(16) },
            { repository.setMaxSnoozes(-1) },
            { repository.setMaxSnoozes(6) },
            { repository.setShakeCount(9) },
            { repository.setShakeCount(101) },
        )

        failures.forEach { call ->
            val error = runCatching { call() }.exceptionOrNull()
            assertTrue("expected IllegalArgumentException but was $error", error is IllegalArgumentException)
        }

        val settings = repository.currentSettings()
        assertEquals(80, settings.defaultGoalSteps)
        assertEquals(5, settings.snoozeMinutes)
        assertEquals(3, settings.maxSnoozes)
        assertEquals(30, settings.shakeCount)
    }

    @Test
    fun `out of range stored value is sanitized to default on read`() = runTest {
        dataStore.updateData { preferences ->
            preferences.toMutablePreferences().apply {
                this[intPreferencesKey("default_goal_steps")] = 9_999
                this[intPreferencesKey("snooze_minutes")] = 0
                this[intPreferencesKey("max_snoozes")] = 42
                this[intPreferencesKey("shake_count")] = -5
            }
        }

        val settings = repository.currentSettings()

        assertEquals(50, settings.defaultGoalSteps)
        assertEquals(5, settings.snoozeMinutes)
        assertEquals(3, settings.maxSnoozes)
        assertEquals(30, settings.shakeCount)
    }

    @Test
    fun `boundary values are accepted`() = runTest {
        repository.setDefaultGoalSteps(10)
        repository.setSnoozeMinutes(15)
        repository.setMaxSnoozes(0)
        repository.setShakeCount(100)

        val settings = repository.currentSettings()

        assertEquals(10, settings.defaultGoalSteps)
        assertEquals(15, settings.snoozeMinutes)
        assertEquals(0, settings.maxSnoozes)
        assertEquals(100, settings.shakeCount)
    }
}
