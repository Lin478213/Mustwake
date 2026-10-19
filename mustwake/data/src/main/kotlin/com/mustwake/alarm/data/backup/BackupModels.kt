package com.mustwake.alarm.data.backup

import com.mustwake.alarm.data.settings.SettingsConstraints
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * On-disk backup document.
 *
 * Serialised as JSON with a stable field set (schema version 1):
 * `schemaVersion`, `exportedAt`, `alarms[]` and `settings{}`. Only alarms and user
 * preferences are included; session/history rows and the per-device
 * `onboarding_completed` flag are deliberately excluded, so restoring on a new device
 * re-runs the permission guide instead of skipping it.
 *
 * Missing fields fall back to the defaults below; unknown fields are ignored so newer
 * backups stay importable after the format gains fields (Room v3 obligation).
 */
@Serializable
data class BackupData(
    /** Required on decode: a document without a version is rejected instead of guessed. */
    val schemaVersion: Int,
    val exportedAt: Long = 0L,
    val alarms: List<BackupAlarm> = emptyList(),
    val settings: BackupSettings = BackupSettings(),
)

/** One alarm row as stored in a backup. JSON names are snake_case and must stay stable. */
@Serializable
data class BackupAlarm(
    val id: Long = 0L,
    val hour: Int = 0,
    val minute: Int = 0,
    @SerialName("repeat_days_mask")
    val repeatDaysMask: Int = 0,
    val label: String = "",
    @SerialName("goal_steps")
    val goalSteps: Int = SettingsConstraints.DEFAULT_GOAL_STEPS,
    val enabled: Boolean = true,
    @SerialName("created_at")
    val createdAt: Long = 0L,
    @SerialName("updated_at")
    val updatedAt: Long = 0L,
)

/**
 * Importable user settings.
 *
 * [com.mustwake.alarm.data.settings.AppSettings.onboardingCompleted] is intentionally
 * absent: the permission guide is per-device and must not travel with a backup.
 */
@Serializable
data class BackupSettings(
    @SerialName("floating_bottom_bar_enabled")
    val floatingBottomBarEnabled: Boolean = false,
    @SerialName("liquid_glass_enabled")
    val liquidGlassEnabled: Boolean = false,
    @SerialName("default_goal_steps")
    val defaultGoalSteps: Int = SettingsConstraints.DEFAULT_GOAL_STEPS,
    @SerialName("snooze_minutes")
    val snoozeMinutes: Int = SettingsConstraints.DEFAULT_SNOOZE_MINUTES,
    @SerialName("max_snoozes")
    val maxSnoozes: Int = SettingsConstraints.DEFAULT_MAX_SNOOZES,
    @SerialName("shake_count")
    val shakeCount: Int = SettingsConstraints.DEFAULT_SHAKE_COUNT,
)
