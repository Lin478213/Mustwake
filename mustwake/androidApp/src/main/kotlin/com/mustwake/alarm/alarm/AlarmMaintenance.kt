package com.mustwake.alarm.alarm

import android.util.Log
import com.mustwake.alarm.data.AppContainer
import com.mustwake.alarm.data.db.AlarmEventType

/** Shared maintenance used at boot and at app start. */
object AlarmMaintenance {

    private const val TAG = "MustWake"

    /**
     * Moves ringing/snoozed sessions whose deadline passed to MISSED and writes the matching
     * history events.
     *
     * @return number of swept sessions.
     */
    suspend fun sweepExpiredSessions(container: AppContainer, source: String): Int {
        val swept = container.sessionRepository.sweepExpired()
        swept.forEach { session ->
            container.historyRepository.record(
                alarmId = session.alarmId,
                type = AlarmEventType.MISSED,
                steps = session.steps,
                reason = "session expired ($source)",
            )
        }
        if (swept.isNotEmpty()) {
            Log.i(TAG, "swept ${swept.size} expired sessions ($source)")
        }
        return swept.size
    }
}
