package com.mustwake.alarm.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE enabled = 1 ORDER BY hour, minute, id")
    fun observeEnabled(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    fun observeById(id: Long): Flow<AlarmEntity?>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    suspend fun getAll(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE enabled = 1 ORDER BY hour, minute, id")
    suspend fun getEnabled(): List<AlarmEntity>

    @Insert
    suspend fun insert(alarm: AlarmEntity): Long

    @Update
    suspend fun update(alarm: AlarmEntity)

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM alarms")
    suspend fun deleteAll(): Int

    @Query("UPDATE alarms SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long): Int
}
