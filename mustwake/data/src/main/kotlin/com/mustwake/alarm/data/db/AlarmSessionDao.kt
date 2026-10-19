package com.mustwake.alarm.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmSessionDao {

    @Insert
    suspend fun insert(session: AlarmSessionEntity): Long

    @Update
    suspend fun update(session: AlarmSessionEntity)

    @Query("SELECT * FROM alarm_sessions WHERE id = :id")
    suspend fun getById(id: Long): AlarmSessionEntity?

    /** Newest active session for an alarm, if any. */
    @Query(
        "SELECT * FROM alarm_sessions WHERE alarm_id = :alarmId AND state IN ('RINGING', 'SNOOZED') " +
            "ORDER BY started_at DESC LIMIT 1",
    )
    suspend fun getActiveForAlarm(alarmId: Long): AlarmSessionEntity?

    @Query(
        "SELECT * FROM alarm_sessions WHERE state IN ('RINGING', 'SNOOZED') " +
            "ORDER BY started_at DESC",
    )
    fun observeActive(): Flow<List<AlarmSessionEntity>>

    @Query(
        "SELECT * FROM alarm_sessions WHERE state IN ('RINGING', 'SNOOZED') " +
            "ORDER BY started_at DESC",
    )
    suspend fun getActive(): List<AlarmSessionEntity>

    /** Active sessions whose snooze deadline has passed. */
    @Query(
        "SELECT * FROM alarm_sessions WHERE state IN ('RINGING', 'SNOOZED') " +
            "AND deadline_at IS NOT NULL AND deadline_at < :now",
    )
    suspend fun getExpired(now: Long): List<AlarmSessionEntity>

    @Query(
        "UPDATE alarm_sessions SET state = :state, steps = :steps, snooze_count = :snoozeCount, " +
            "deadline_at = :deadlineAt, updated_at = :updatedAt WHERE id = :id",
    )
    suspend fun updateState(
        id: Long,
        state: AlarmSessionState,
        steps: Int,
        snoozeCount: Int,
        deadlineAt: Long?,
        updatedAt: Long,
    ): Int

    @Query("SELECT COUNT(*) FROM alarm_sessions")
    suspend fun count(): Int
}
