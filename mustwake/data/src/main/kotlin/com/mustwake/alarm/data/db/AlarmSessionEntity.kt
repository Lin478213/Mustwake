package com.mustwake.alarm.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One ringing attempt for an alarm.
 *
 * The session is the persistent state machine behind snooze and missed detection: [steps] keeps
 * accumulating across snoozes, [deadlineAt] is the snooze re-ring deadline, and a session still in
 * [AlarmSessionState.RINGING] / [AlarmSessionState.SNOOZED] past its deadline is swept to
 * [AlarmSessionState.MISSED] on boot or app start.
 */
@Entity(
    tableName = "alarm_sessions",
    foreignKeys = [
        ForeignKey(
            entity = AlarmEntity::class,
            parentColumns = ["id"],
            childColumns = ["alarm_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["alarm_id"]),
        Index(value = ["state"]),
    ],
)
data class AlarmSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "alarm_id")
    val alarmId: Long,
    val state: AlarmSessionState,
    @ColumnInfo(name = "started_at")
    val startedAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    val steps: Int = 0,
    @ColumnInfo(name = "snooze_count")
    val snoozeCount: Int = 0,
    @ColumnInfo(name = "deadline_at")
    val deadlineAt: Long? = null,
)
