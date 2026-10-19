package com.mustwake.alarm.data.history

import com.mustwake.alarm.data.db.AlarmEventDao
import com.mustwake.alarm.data.db.AlarmEventEntity
import com.mustwake.alarm.data.db.AlarmEventType
import kotlinx.coroutines.flow.Flow

/**
 * Append-only history of alarm sessions.
 *
 * Writes are validated so a bad event (negative steps, unknown alarm) is never stored.
 */
class HistoryRepository(
    private val eventDao: AlarmEventDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun observeRecent(limit: Int = HistoryRetention.MAX_EVENTS): Flow<List<AlarmEventEntity>> =
        eventDao.observeRecent(requireLimit(limit))

    fun observeForAlarm(
        alarmId: Long,
        limit: Int = HistoryRetention.MAX_EVENTS,
    ): Flow<List<AlarmEventEntity>> {
        require(alarmId > 0) { "alarmId must be positive, was $alarmId" }
        return eventDao.observeForAlarm(alarmId, requireLimit(limit))
    }

    suspend fun recent(limit: Int = HistoryRetention.MAX_EVENTS): List<AlarmEventEntity> =
        eventDao.getRecent(requireLimit(limit))

    suspend fun forAlarm(
        alarmId: Long,
        limit: Int = HistoryRetention.MAX_EVENTS,
    ): List<AlarmEventEntity> {
        require(alarmId > 0) { "alarmId must be positive, was $alarmId" }
        return eventDao.getForAlarm(alarmId, requireLimit(limit))
    }

    /**
     * Records one session event.
     *
     * @param steps step count at the moment of the event; must not be negative.
     * @param reason human-readable explanation for degraded/emergency/missed events.
     */
    suspend fun record(
        alarmId: Long,
        type: AlarmEventType,
        steps: Int = 0,
        reason: String? = null,
        timestamp: Long = clock(),
    ): Long {
        require(alarmId > 0) { "alarmId must be positive, was $alarmId" }
        require(steps >= 0) { "steps must not be negative, was $steps" }
        return eventDao.insert(
            AlarmEventEntity(
                alarmId = alarmId,
                type = type,
                steps = steps,
                timestamp = timestamp,
                reason = reason?.trim()?.takeIf { it.isNotEmpty() },
            ),
        )
    }

    /** Drops the oldest events beyond [HistoryRetention.MAX_EVENTS]. */
    suspend fun trimToLimit(): Int = eventDao.trimTo(HistoryRetention.MAX_EVENTS)

    /** Drops events older than [HistoryRetention.RETENTION_DAYS]. */
    suspend fun purgeExpired(now: Long = clock()): Int {
        val cutoff = now - HistoryRetention.RETENTION_DAYS * HistoryRetention.MILLIS_PER_DAY
        return eventDao.deleteOlderThan(cutoff)
    }

    suspend fun count(): Int = eventDao.count()

    private fun requireLimit(limit: Int): Int {
        require(limit > 0) { "limit must be positive, was $limit" }
        return limit
    }
}
