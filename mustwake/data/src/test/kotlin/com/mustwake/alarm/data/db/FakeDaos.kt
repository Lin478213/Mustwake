package com.mustwake.alarm.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [AlarmDao] used by repository unit tests. Mirrors the ordering guarantees of the
 * real SQL queries so repository behaviour is tested against the same contract.
 */
class FakeAlarmDao : AlarmDao {

    private val state = MutableStateFlow<List<AlarmEntity>>(emptyList())
    private var nextId = 1L

    val stored: List<AlarmEntity> get() = state.value

    override fun observeAll(): Flow<List<AlarmEntity>> = state.map { it.sortedForList() }

    override fun observeEnabled(): Flow<List<AlarmEntity>> =
        state.map { alarms -> alarms.filter { it.enabled }.sortedForList() }

    override fun observeById(id: Long): Flow<AlarmEntity?> =
        state.map { alarms -> alarms.firstOrNull { it.id == id } }

    override suspend fun getById(id: Long): AlarmEntity? = state.value.firstOrNull { it.id == id }

    override suspend fun getAll(): List<AlarmEntity> = state.value.sortedForList()

    override suspend fun getEnabled(): List<AlarmEntity> = getAll().filter { it.enabled }

    override suspend fun insert(alarm: AlarmEntity): Long {
        val id = nextId++
        state.value = state.value + alarm.copy(id = id)
        return id
    }

    override suspend fun update(alarm: AlarmEntity) {
        state.value = state.value.map { if (it.id == alarm.id) alarm else it }
    }

    override suspend fun delete(alarm: AlarmEntity) {
        state.value = state.value.filterNot { it.id == alarm.id }
    }

    override suspend fun deleteById(id: Long): Int {
        val before = state.value.size
        state.value = state.value.filterNot { it.id == id }
        return before - state.value.size
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long): Int {
        if (state.value.none { it.id == id }) return 0
        state.value = state.value.map {
            if (it.id == id) it.copy(enabled = enabled, updatedAt = updatedAt) else it
        }
        return 1
    }

    private fun List<AlarmEntity>.sortedForList(): List<AlarmEntity> =
        sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
}

/** In-memory [AlarmEventDao] used by repository unit tests. */
class FakeAlarmEventDao : AlarmEventDao {

    private val state = MutableStateFlow<List<AlarmEventEntity>>(emptyList())
    private var nextId = 1L

    val stored: List<AlarmEventEntity> get() = state.value

    override suspend fun insert(event: AlarmEventEntity): Long {
        val id = nextId++
        state.value = state.value + event.copy(id = id)
        return id
    }

    override fun observeRecent(limit: Int): Flow<List<AlarmEventEntity>> =
        state.map { it.newestFirst(limit) }

    override suspend fun getRecent(limit: Int): List<AlarmEventEntity> =
        state.value.newestFirst(limit)

    override fun observeForAlarm(alarmId: Long, limit: Int): Flow<List<AlarmEventEntity>> =
        state.map { it.filter { event -> event.alarmId == alarmId }.newestFirst(limit) }

    override suspend fun getForAlarm(alarmId: Long, limit: Int): List<AlarmEventEntity> =
        state.value.filter { it.alarmId == alarmId }.newestFirst(limit)

    override suspend fun deleteOlderThan(cutoff: Long): Int {
        val before = state.value.size
        state.value = state.value.filterNot { it.timestamp < cutoff }
        return before - state.value.size
    }

    override suspend fun trimTo(keep: Int): Int {
        val sorted = state.value.newestFirst(state.value.size)
        val kept = sorted.take(keep)
        val removed = state.value.size - kept.size
        state.value = kept
        return removed
    }

    override suspend fun deleteForAlarm(alarmId: Long): Int {
        val before = state.value.size
        state.value = state.value.filterNot { it.alarmId == alarmId }
        return before - state.value.size
    }

    override suspend fun count(): Int = state.value.size

    private fun List<AlarmEventEntity>.newestFirst(limit: Int): List<AlarmEventEntity> =
        sortedWith(
            compareByDescending<AlarmEventEntity> { it.timestamp }.thenByDescending { it.id },
        ).take(limit)
}
