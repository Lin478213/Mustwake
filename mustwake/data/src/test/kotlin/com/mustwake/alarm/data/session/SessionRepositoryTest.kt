package com.mustwake.alarm.data.session

import com.mustwake.alarm.data.db.AlarmSessionState
import com.mustwake.alarm.data.db.FakeAlarmSessionDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {

    private val dao = FakeAlarmSessionDao()
    private var now = 1_000L
    private val repository = SessionRepository(dao) { now }

    @Test
    fun `startSession creates a ringing session`() = runTest {
        val session = repository.startSession(alarmId = 7L)

        assertEquals(7L, session.alarmId)
        assertEquals(AlarmSessionState.RINGING, session.state)
        assertEquals(1_000L, session.startedAt)
        assertEquals(1_000L, session.updatedAt)
        assertEquals(0, session.steps)
        assertEquals(0, session.snoozeCount)
        assertNull(session.deadlineAt)
    }

    @Test
    fun `snooze increments count and stores the deadline`() = runTest {
        val session = repository.startSession(alarmId = 7L)
        now = 2_000L

        val snoozed = repository.snooze(sessionId = session.id, deadlineAt = 302_000L, steps = 12)

        assertEquals(AlarmSessionState.SNOOZED, snoozed.state)
        assertEquals(1, snoozed.snoozeCount)
        assertEquals(302_000L, snoozed.deadlineAt)
        assertEquals(12, snoozed.steps)
        assertEquals(2_000L, snoozed.updatedAt)
    }

    @Test
    fun `snooze keeps the higher step count`() = runTest {
        val session = repository.startSession(alarmId = 7L)
        now = 2_000L
        repository.snooze(session.id, deadlineAt = 10_000L, steps = 25)
        now = 3_000L

        val second = repository.snooze(session.id, deadlineAt = 20_000L, steps = 10)

        assertEquals(25, second.steps)
        assertEquals(2, second.snoozeCount)
    }

    @Test
    fun `snooze rejects a deadline in the past`() = runTest {
        val session = repository.startSession(alarmId = 7L)
        now = 5_000L

        val error = runCatching { repository.snooze(session.id, deadlineAt = 4_999L) }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException but was $error", error is IllegalArgumentException)
        assertEquals(AlarmSessionState.RINGING, repository.getSession(session.id)?.state)
    }

    @Test
    fun `resume re-arms a snoozed session`() = runTest {
        val session = repository.startSession(alarmId = 7L)
        repository.snooze(session.id, deadlineAt = 50_000L)
        now = 6_000L

        val resumed = repository.resume(session.id)

        assertEquals(AlarmSessionState.RINGING, resumed.state)
        assertNull(resumed.deadlineAt)
    }

    @Test
    fun `resume rejects a ringing session`() = runTest {
        val session = repository.startSession(alarmId = 7L)

        val error = runCatching { repository.resume(session.id) }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException but was $error", error is IllegalArgumentException)
    }

    @Test
    fun `finish records a terminal state`() = runTest {
        val session = repository.startSession(alarmId = 7L)
        now = 9_000L

        val finished = repository.finish(session.id, AlarmSessionState.COMPLETED, steps = 50)

        assertEquals(AlarmSessionState.COMPLETED, finished.state)
        assertEquals(50, finished.steps)
        assertEquals(9_000L, finished.updatedAt)
        assertNull(repository.activeSession(7L))
    }

    @Test
    fun `finish rejects an active state`() = runTest {
        val session = repository.startSession(alarmId = 7L)

        val error = runCatching {
            repository.finish(session.id, AlarmSessionState.RINGING)
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException but was $error", error is IllegalArgumentException)
    }

    @Test
    fun `sweepExpired marks only expired sessions as missed`() = runTest {
        val expired = repository.startSession(alarmId = 1L)
        repository.snooze(expired.id, deadlineAt = 10_000L)
        val alive = repository.startSession(alarmId = 2L)
        now = 20_000L
        repository.snooze(alive.id, deadlineAt = 200_000L)
        now = 90_000L

        val swept = repository.sweepExpired()

        assertEquals(1, swept.size)
        assertEquals(expired.id, swept.single().id)
        assertEquals(AlarmSessionState.MISSED, repository.getSession(expired.id)?.state)
        assertEquals(AlarmSessionState.SNOOZED, repository.getSession(alive.id)?.state)
    }

    @Test
    fun `ringing session without deadline is not swept`() = runTest {
        repository.startSession(alarmId = 1L)
        now = 500_000L

        assertTrue(repository.sweepExpired().isEmpty())
    }

    @Test
    fun `activeSession ignores finished sessions`() = runTest {
        val session = repository.startSession(alarmId = 3L)
        repository.finish(session.id, AlarmSessionState.CANCELLED)

        assertNull(repository.activeSession(3L))
    }

    @Test
    fun `non positive alarm id is rejected`() = runTest {
        val error = runCatching { repository.startSession(0L) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException but was $error", error is IllegalArgumentException)
    }
}
