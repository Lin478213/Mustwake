package com.mustwake.alarm.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal object SettingsKeys {
    val FLOATING_BOTTOM_BAR = booleanPreferencesKey("floating_bottom_bar_enabled")
    val LIQUID_GLASS = booleanPreferencesKey("liquid_glass_enabled")
    val DEFAULT_GOAL_STEPS = intPreferencesKey("default_goal_steps")
    val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
    val MAX_SNOOZES = intPreferencesKey("max_snoozes")
    val SHAKE_COUNT = intPreferencesKey("shake_count")
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
}

/** The single DataStore instance backing [SettingsRepository]. */
val Context.mustWakeSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mustwake_settings",
)

/**
 * Maps persisted preferences to [AppSettings].
 *
 * Missing entries fall back to spec defaults; out-of-range numbers are replaced by defaults
 * via [AppSettings.sanitized], so a corrupted store can never produce an invalid setting.
 */
fun Preferences.toAppSettings(): AppSettings = AppSettings(
    floatingBottomBarEnabled = this[SettingsKeys.FLOATING_BOTTOM_BAR] ?: false,
    liquidGlassEnabled = this[SettingsKeys.LIQUID_GLASS] ?: false,
    defaultGoalSteps = this[SettingsKeys.DEFAULT_GOAL_STEPS]
        ?: SettingsConstraints.DEFAULT_GOAL_STEPS,
    snoozeMinutes = this[SettingsKeys.SNOOZE_MINUTES]
        ?: SettingsConstraints.DEFAULT_SNOOZE_MINUTES,
    maxSnoozes = this[SettingsKeys.MAX_SNOOZES]
        ?: SettingsConstraints.DEFAULT_MAX_SNOOZES,
    shakeCount = this[SettingsKeys.SHAKE_COUNT]
        ?: SettingsConstraints.DEFAULT_SHAKE_COUNT,
    onboardingCompleted = this[SettingsKeys.ONBOARDING_COMPLETED] ?: false,
).sanitized()

/**
 * Persists user settings in a [DataStore].
 *
 * Read path is corruption-tolerant (falls back to defaults), write path is strict
 * ([IllegalArgumentException] on invalid values) so invalid values are never stored.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { it.toAppSettings() }

    suspend fun currentSettings(): AppSettings = settings.first()

    suspend fun setFloatingBottomBarEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.FLOATING_BOTTOM_BAR] = enabled }
    }

    suspend fun setLiquidGlassEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsKeys.LIQUID_GLASS] = enabled }
    }

    suspend fun setDefaultGoalSteps(steps: Int) {
        SettingsConstraints.requireValidGoalSteps(steps)
        dataStore.edit { it[SettingsKeys.DEFAULT_GOAL_STEPS] = steps }
    }

    suspend fun setSnoozeMinutes(minutes: Int) {
        SettingsConstraints.requireValidSnoozeMinutes(minutes)
        dataStore.edit { it[SettingsKeys.SNOOZE_MINUTES] = minutes }
    }

    suspend fun setMaxSnoozes(maxSnoozes: Int) {
        SettingsConstraints.requireValidMaxSnoozes(maxSnoozes)
        dataStore.edit { it[SettingsKeys.MAX_SNOOZES] = maxSnoozes }
    }

    suspend fun setShakeCount(count: Int) {
        SettingsConstraints.requireValidShakeCount(count)
        dataStore.edit { it[SettingsKeys.SHAKE_COUNT] = count }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[SettingsKeys.ONBOARDING_COMPLETED] = completed }
    }
}
