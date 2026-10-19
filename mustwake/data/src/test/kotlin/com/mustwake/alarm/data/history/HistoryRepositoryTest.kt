package com.mustwake.alarm.data.history

import com.mustwake.alarm.data.db.AlarmEventType
import com.mustwake.alarm.data.db.FakeAlarmEventDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRepositoryTest {

    private val dao = FakeAlarmEventDao()
    private var now = 10_000L
    private val repository = HistoryRepository(dao) { now }

    @Test
    fun `record stores event with clock timestamp`() = runTest {
        repository.record(alarmId = 1L, type = AlarmEventType.RINGING, steps = 12)

        val events = repository.recent()
        assertEquals(1, events.size)
        assertEquals(1L, events[0].alarmId)
        assertEquals(AlarmEventType.RINGING, events[0].type)
        assertEquals(12, events[0].steps)
        assertEquals(10_000L, events[0].timestamp)
        assertNull(events[0].reason)
    }

    @Test
    fun `blank reason is stored as null`() = runTest {
        repository.record(
            alarmId = 1L,
            type = AlarmEventType.DEGRADED,
            reason = "   ",
        )
        assertNull(dao.stored.single().reason)
    }

    @Test
    fun `reason is trimmed`() = runTest {
        repository.record(
            alarmId = 1L,
            type = AlarmEventType.EMERGENCY,
            reason = "  long press  ",
        )
        assertEquals("long press", dao.stored.single().reason)
    }

    @Test
    fun `negative steps are rejected`() = runTest {
        val error = runCatching {
            repository.record(alarmId = 1L, type = AlarmEventType.COMPLETED, steps = -1)
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException but was $error", error is IllegalArgumentException)
        assertEquals(0, dao.stored.size)
    }

    @Test
    fun `non positive alarm id is rejected`() = runTest {
        val error = runCatching {
            repository.record(alarmId = 0L, type = AlarmEventType.SCHEDULED)
        }.exceptionOrNull()

        assertTrue("expected IllegalArgumentException but was $error", error is IllegalArgumentException)
    }

    @Test
    fun `recent returns newest first and respects limit`() = runTest {
        repeat(5) { index ->
            repository.record(
                alarmId = 1L,
                type = AlarmEventType.SCHEDULED,
                timestamp = 1_000L + index,
            )
        }

        val events = repository.recent(limit = 3)

        assertEquals(3, events.size)
        assertEquals(1_004L, events[0].timestamp)
        assertEquals(1_002L, events[2].timestamp)
    }

    @Test
    fun `events can be filtered by alarm`() = runTest {
        repository.record(alarmId = 1L, type = AlarmEventType.COMPLETED, steps = 50)
        repository.record(alarmId = 2L, type = AlarmEventType.MISSED)

        val events = repository.forAlarm(alarmId = 2L)

        assertEquals(1, events.size)
        assertEquals(2L, events[0].alarmId)
    }

    @Test
    fun `trimToLimit keeps only newest 200 events`() = runTest {
        repeat(205) { index ->
            repository.record(
                alarmId = 1L,
                type = AlarmEventType.RINGING,
                timestamp = 1_000L + index,
            )
        }

        val removed = repository.trimToLimit()

        assertEquals(5, removed)
        assertEquals(HistoryRetention.MAX_EVENTS, repository.count())
        assertEquals(1_204L, repository.recent()[0].timestamp)
    }

    @Test
    fun `purgeExpired removes events older than 90 days`() = runTest {
        now = 100L * HistoryRetention.MILLIS_PER_DAY
        repository.record(
            alarmId = 1L,
            type = AlarmEventType.COMPLETED,
            timestamp = now - 91L * HistoryRetention.MILLIS_PER_DAY,
        )
        repository.record(
            alarmId = 1L,
            type = AlarmEventType.COMPLETED,
            timestamp = now - 89L * HistoryRetention.MILLIS_PER_DAY,
        )

        val removed = repository.purgeExpired()

        assertEquals(1, removed)
        assertEquals(1, repository.count())
    }

    @Test
    fun `non positive limit is rejected`() = runTest {
        val error = runCatching { repository.recent(limit = 0) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException but was $error", error is IllegalArgumentException)
    }
}
