package com.mustwake.alarm.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [AlarmSessionDao] used by session repository unit tests. */
class FakeAlarmSessionDao : AlarmSessionDao {

    private val state = MutableStateFlow<List<AlarmSessionEntity>>(emptyList())
    private var nextId = 1L

    val stored: List<AlarmSessionEntity> get() = state.value

    override suspend fun insert(session: AlarmSessionEntity): Long {
        val id = nextId++
        state.value = state.value + session.copy(id = id)
        return id
    }

    override suspend fun update(session: AlarmSessionEntity) {
        state.value = state.value.map { if (it.id == session.id) session else it }
    }

    override suspend fun getById(id: Long): AlarmSessionEntity? =
        state.value.firstOrNull { it.id == id }

    override suspend fun getActiveForAlarm(alarmId: Long): AlarmSessionEntity? =
        state.value
            .filter { it.alarmId == alarmId && it.state.isActive }
            .maxByOrNull { it.startedAt }

    override fun observeActive(): Flow<List<AlarmSessionEntity>> =
        state.map { sessions -> sessions.filter { it.state.isActive } }

    override suspend fun getActive(): List<AlarmSessionEntity> =
        state.value.filter { it.state.isActive }

    override suspend fun getExpired(now: Long): List<AlarmSessionEntity> =
        state.value.filter { it.state.isActive && it.deadlineAt != null && it.deadlineAt < now }

    override suspend fun updateState(
        id: Long,
        state: AlarmSessionState,
        steps: Int,
        snoozeCount: Int,
        deadlineAt: Long?,
        updatedAt: Long,
    ): Int {
        if (this.state.value.none { it.id == id }) return 0
        this.state.value = this.state.value.map {
            if (it.id == id) {
                it.copy(
                    state = state,
                    steps = steps,
                    snoozeCount = snoozeCount,
                    deadlineAt = deadlineAt,
                    updatedAt = updatedAt,
                )
            } else {
                it
            }
        }
        return 1
    }

    override suspend fun count(): Int = state.value.size
}
