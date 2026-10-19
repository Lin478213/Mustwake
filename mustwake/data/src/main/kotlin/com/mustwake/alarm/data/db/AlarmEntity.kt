package com.mustwake.alarm.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mustwake.alarm.data.alarm.AlarmRepeatDays
import com.mustwake.alarm.data.settings.SettingsConstraints

/**
 * A single alarm definition.
 *
 * [repeatDaysMask] is a bitmask where Monday is bit 0 and Sunday is bit 6; `0` means a
 * one-shot alarm. [goalSteps] is constrained to the same 10..500 range as the settings default.
 */
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val hour: Int,
    val minute: Int,
    @ColumnInfo(name = "repeat_days_mask")
    val repeatDaysMask: Int = AlarmRepeatDays.NONE,
    val label: String = "",
    @ColumnInfo(name = "goal_steps")
    val goalSteps: Int = SettingsConstraints.DEFAULT_GOAL_STEPS,
    val enabled: Boolean = true,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = 0L,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
)
