package com.mustwake.alarm.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmEventDao {

    @Insert
    suspend fun insert(event: AlarmEventEntity): Long

    @Query("SELECT * FROM alarm_events ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AlarmEventEntity>>

    @Query("SELECT * FROM alarm_events ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AlarmEventEntity>

    @Query(
        "SELECT * FROM alarm_events WHERE alarm_id = :alarmId " +
            "ORDER BY timestamp DESC, id DESC LIMIT :limit",
    )
    fun observeForAlarm(alarmId: Long, limit: Int): Flow<List<AlarmEventEntity>>

    @Query(
        "SELECT * FROM alarm_events WHERE alarm_id = :alarmId " +
            "ORDER BY timestamp DESC, id DESC LIMIT :limit",
    )
    suspend fun getForAlarm(alarmId: Long, limit: Int): List<AlarmEventEntity>

    @Query("DELETE FROM alarm_events WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query(
        "DELETE FROM alarm_events WHERE id NOT IN " +
            "(SELECT id FROM alarm_events ORDER BY timestamp DESC, id DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int): Int

    @Query("DELETE FROM alarm_events WHERE alarm_id = :alarmId")
    suspend fun deleteForAlarm(alarmId: Long): Int

    @Query("SELECT COUNT(*) FROM alarm_events")
    suspend fun count(): Int
}
