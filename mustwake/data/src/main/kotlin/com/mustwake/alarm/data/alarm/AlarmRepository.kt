package com.mustwake.alarm.data.alarm

import com.mustwake.alarm.data.db.AlarmDao
import com.mustwake.alarm.data.db.AlarmEntity
import kotlinx.coroutines.flow.Flow

/**
 * Read/write access to alarms.
 *
 * Every write goes through [normalize], so invalid times, repeat masks, step goals or
 * over-long labels are rejected before they can reach the database.
 */
class AlarmRepository(
    private val alarmDao: AlarmDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun observeAlarms(): Flow<List<AlarmEntity>> = alarmDao.observeAll()

    fun observeEnabledAlarms(): Flow<List<AlarmEntity>> = alarmDao.observeEnabled()

    fun observeAlarm(id: Long): Flow<AlarmEntity?> = alarmDao.observeById(id)

    suspend fun getAlarm(id: Long): AlarmEntity? = alarmDao.getById(id)

    suspend fun getAllAlarms(): List<AlarmEntity> = alarmDao.getAll()

    suspend fun getEnabledAlarms(): List<AlarmEntity> = alarmDao.getEnabled()

    /**
     * Inserts a new alarm (id == 0) or updates an existing one.
     *
     * @return the row id of the saved alarm.
     * @throws IllegalArgumentException when a field is out of range or [AlarmEntity.id] is unknown.
     */
    suspend fun save(alarm: AlarmEntity): Long {
        val normalized = normalize(alarm)
        val now = clock()
        if (normalized.id == 0L) {
            return alarmDao.insert(normalized.copy(createdAt = now, updatedAt = now))
        }
        val existing = alarmDao.getById(normalized.id)
            ?: throw IllegalArgumentException("alarm ${normalized.id} does not exist")
        alarmDao.update(normalized.copy(createdAt = existing.createdAt, updatedAt = now))
        return normalized.id
    }

    /** @return true when a row was updated. */
    suspend fun setEnabled(id: Long, enabled: Boolean): Boolean =
        alarmDao.setEnabled(id, enabled, clock()) > 0

    /** Deleting an alarm also deletes its history rows via the foreign key cascade. */
    suspend fun delete(id: Long): Boolean = alarmDao.deleteById(id) > 0

    private fun normalize(alarm: AlarmEntity): AlarmEntity = alarm.copy(
        hour = AlarmConstraints.requireValidHour(alarm.hour),
        minute = AlarmConstraints.requireValidMinute(alarm.minute),
        repeatDaysMask = AlarmConstraints.requireValidRepeatMask(alarm.repeatDaysMask),
        goalSteps = AlarmConstraints.requireValidGoalSteps(alarm.goalSteps),
        label = AlarmConstraints.requireValidLabel(alarm.label),
    )
}
