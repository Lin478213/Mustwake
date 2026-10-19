package com.mustwake.alarm.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One history record for an alarm session.
 *
 * Deleting an alarm cascades to its events so history never references a missing alarm.
 */
@Entity(
    tableName = "alarm_events",
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
        Index(value = ["timestamp"]),
    ],
)
data class AlarmEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "alarm_id")
    val alarmId: Long,
    val type: AlarmEventType,
    val steps: Int = 0,
    val timestamp: Long,
    val reason: String? = null,
)
