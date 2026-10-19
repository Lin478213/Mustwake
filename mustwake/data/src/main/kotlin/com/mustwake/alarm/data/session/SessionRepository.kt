package com.mustwake.alarm.data.session

import com.mustwake.alarm.data.db.AlarmSessionDao
import com.mustwake.alarm.data.db.AlarmSessionEntity
import com.mustwake.alarm.data.db.AlarmSessionState
import kotlinx.coroutines.flow.Flow

/**
 * Persistent ringing sessions.
 *
 * State transitions:
 * `startSession` (RINGING) -> `snooze` (SNOOZED, deadline set) -> RINGING again on re-ring,
 * or `finish` (COMPLETED / EMERGENCY / DEGRADED / CANCELLED). `sweepExpired` moves sessions whose
 * deadline passed to MISSED so history never stays stuck on "ringing".
 */
class SessionRepository(
    private val sessionDao: AlarmSessionDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun observeActive(): Flow<List<AlarmSessionEntity>> = sessionDao.observeActive()

    suspend fun getSession(id: Long): AlarmSessionEntity? = sessionDao.getById(id)

    suspend fun activeSession(alarmId: Long): AlarmSessionEntity? {
        require(alarmId > 0) { "alarmId must be positive, was $alarmId" }
        return sessionDao.getActiveForAlarm(alarmId)
    }

    suspend fun activeSessions(): List<AlarmSessionEntity> = sessionDao.getActive()

    /** Starts a new RINGING session for [alarmId]. */
    suspend fun startSession(alarmId: Long): AlarmSessionEntity {
        require(alarmId > 0) { "alarmId must be positive, was $alarmId" }
        val now = clock()
        val session = AlarmSessionEntity(
            alarmId = alarmId,
            state = AlarmSessionState.RINGING,
            startedAt = now,
            updatedAt = now,
        )
        val id = sessionDao.insert(session)
        return session.copy(id = id)
    }

    /**
     * Moves an active session to SNOOZED and stores the re-ring [deadlineAt].
     *
     * Steps accumulated before the snooze are preserved unless a larger [steps] value is passed.
     */
    suspend fun snooze(sessionId: Long, deadlineAt: Long, steps: Int? = null): AlarmSessionEntity {
        val session = requireSession(sessionId)
        require(session.state.isActive) { "session $sessionId is already ${session.state}" }
        val now = clock()
        require(deadlineAt > now) { "snooze deadline must be in the future, was $deadlineAt" }
        val updatedSteps = maxOf(session.steps, steps ?: session.steps)
        val updated = session.copy(
            state = AlarmSessionState.SNOOZED,
            steps = updatedSteps,
            snoozeCount = session.snoozeCount + 1,
            deadlineAt = deadlineAt,
            updatedAt = now,
        )
        sessionDao.update(updated)
        return updated
    }

    /** Re-arms a SNOOZED session when its deadline fires. */
    suspend fun resume(sessionId: Long): AlarmSessionEntity {
        val session = requireSession(sessionId)
        require(session.state == AlarmSessionState.SNOOZED) {
            "session $sessionId is not snoozed, was ${session.state}"
        }
        val updated = session.copy(
            state = AlarmSessionState.RINGING,
            deadlineAt = null,
            updatedAt = clock(),
        )
        sessionDao.update(updated)
        return updated
    }

    /** Finishes a session with a terminal [state]; active states are rejected. */
    suspend fun finish(sessionId: Long, state: AlarmSessionState, steps: Int? = null): AlarmSessionEntity {
        require(state.isTerminal) { "$state is not a terminal session state" }
        val session = requireSession(sessionId)
        require(session.state.isActive) { "session $sessionId is already ${session.state}" }
        val updated = session.copy(
            state = state,
            steps = maxOf(session.steps, steps ?: session.steps),
            deadlineAt = null,
            updatedAt = clock(),
        )
        sessionDao.update(updated)
        return updated
    }

    /**
     * Marks active sessions whose snooze deadline passed as MISSED.
     *
     * @return the sessions that were swept, so the caller can write MISSED history events.
     */
    suspend fun sweepExpired(now: Long = clock()): List<AlarmSessionEntity> {
        val expired = sessionDao.getExpired(now)
        return expired.map { session ->
            val updated = session.copy(
                state = AlarmSessionState.MISSED,
                deadlineAt = null,
                updatedAt = now,
            )
            sessionDao.update(updated)
            updated
        }
    }

    suspend fun count(): Int = sessionDao.count()

    private suspend fun requireSession(id: Long): AlarmSessionEntity =
        sessionDao.getById(id) ?: throw IllegalArgumentException("session $id does not exist")
}
