package com.mustwake.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.mustwake.alarm.MainActivity
import com.mustwake.alarm.data.alarm.NextTriggerCalculator
import com.mustwake.alarm.data.db.AlarmEntity
import com.mustwake.alarm.data.db.AlarmEventType
import com.mustwake.alarm.data.history.HistoryRepository
import com.mustwake.alarm.receiver.AlarmReceiver
import java.time.Instant
import java.time.ZoneId

/** Result of one scheduling attempt. */
sealed interface ScheduleOutcome {
    data class Scheduled(val alarmId: Long, val triggerAt: Instant, val exact: Boolean) : ScheduleOutcome

    /** No future trigger (disabled alarm) or scheduling is impossible. */
    data class Skipped(val alarmId: Long, val reason: String) : ScheduleOutcome

    data class Failed(val alarmId: Long, val reason: String) : ScheduleOutcome
}

/**
 * Registers alarms with [AlarmManager].
 *
 * Exact alarms use [AlarmManager.setAlarmClock], which gives the system clock semantics
 * (status bar entry, Doze wake-up). When `SCHEDULE_EXACT_ALARM` is not granted, or the platform
 * throws [SecurityException], scheduling degrades to [AlarmManager.setAndAllowWhileIdle] and the
 * outcome reports `exact = false` so the UI can warn about possible delay (T5).
 */
class AlarmScheduler(
    private val context: Context,
    private val historyRepository: HistoryRepository,
    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java),
    private val clock: () -> Instant = Instant::now,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    fun nextTrigger(alarm: AlarmEntity): Instant? =
        NextTriggerCalculator.nextTrigger(alarm, clock(), zone())

    /** Schedules the next occurrence of [alarm]. */
    suspend fun schedule(alarm: AlarmEntity, source: String = "app"): ScheduleOutcome {
        if (!alarm.enabled) return ScheduleOutcome.Skipped(alarm.id, "disabled")
        val triggerAt = nextTrigger(alarm)
            ?: return ScheduleOutcome.Skipped(alarm.id, "no future trigger")
        return register(alarm.id, triggerAt, "$source:scheduled")
    }

    /**
     * Re-registers a snooze alarm at [triggerAt].
     *
     * [steps] and [snoozeCount] are persisted in the history reason so a process death during
     * snooze leaves an audit trail; the session itself is updated by the caller (T4).
     */
    suspend fun scheduleSnooze(
        alarmId: Long,
        triggerAt: Instant,
        steps: Int,
        snoozeCount: Int,
    ): ScheduleOutcome {
        require(alarmId > 0L) { "alarmId must be positive, was $alarmId" }
        require(steps >= 0) { "steps must not be negative, was $steps" }
        require(snoozeCount >= 0) { "snoozeCount must not be negative, was $snoozeCount" }
        return register(alarmId, triggerAt, "snooze#$snoozeCount:${steps}steps", snooze = true)
    }

    /** Reschedules every entry, e.g. after boot or a time zone change. */
    suspend fun rescheduleAll(alarms: List<AlarmEntity>, source: String): List<ScheduleOutcome> =
        alarms.map { schedule(it, source) }

    /** Cancels both the regular registration and any pending snooze for [alarmId]. */
    fun cancel(alarmId: Long) {
        cancelPendingIntent(alarmPendingIntent(alarmId, snooze = false))
        cancelPendingIntent(alarmPendingIntent(alarmId, snooze = true))
    }

    /** Cancels only the pending snooze, leaving the repeating series untouched. */
    fun cancelSnooze(alarmId: Long) {
        cancelPendingIntent(alarmPendingIntent(alarmId, snooze = true))
    }

    private fun cancelPendingIntent(pendingIntent: PendingIntent) {
        alarmManager?.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private suspend fun register(
        alarmId: Long,
        triggerAt: Instant,
        reason: String,
        snooze: Boolean = false,
    ): ScheduleOutcome {
        val manager = alarmManager
            ?: return ScheduleOutcome.Failed(alarmId, "AlarmManager unavailable")
        val pendingIntent = alarmPendingIntent(alarmId, snooze)
        val triggerMillis = triggerAt.toEpochMilli()

        val exact = try {
            if (canScheduleExactAlarms()) {
                manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerMillis, showPendingIntent(alarmId)),
                    pendingIntent,
                )
                true
            } else {
                setInexact(manager, triggerMillis, pendingIntent)
                false
            }
        } catch (security: SecurityException) {
            Log.w(TAG, "exact alarm rejected for $alarmId, falling back to inexact", security)
            setInexact(manager, triggerMillis, pendingIntent)
            false
        }

        historyRepository.record(
            alarmId = alarmId,
            type = AlarmEventType.SCHEDULED,
            reason = "$reason/${if (exact) "exact" else "inexact"}@$triggerMillis",
        )
        Log.i(TAG, "scheduled alarm $alarmId at $triggerAt (exact=$exact, $reason)")
        return ScheduleOutcome.Scheduled(alarmId, triggerAt, exact)
    }

    @Suppress("DEPRECATION")
    private fun setInexact(manager: AlarmManager, triggerMillis: Long, pendingIntent: PendingIntent) {
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
    }

    private fun alarmPendingIntent(alarmId: Long, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_ALARM_FIRED)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
        return PendingIntent.getBroadcast(
            context,
            requestCode(alarmId, snooze),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showPendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_SHOW_RINGING)
            .putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarmId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            requestCode(alarmId, snooze = false),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        private const val TAG = "MustWake"

        /** Action for the `showIntent` of [AlarmManager.AlarmClockInfo]. */
        const val ACTION_SHOW_RINGING = "com.mustwake.alarm.action.SHOW_RINGING"

        private const val SNOOZE_REQUEST_FLAG = 0x4000_0000L

        /**
         * Alarm ids are positive row ids; the low 30 bits plus the snooze flag form a stable,
         * unique request code. Snooze uses a separate code so it never replaces the regular
         * (repeating) registration.
         */
        fun requestCode(alarmId: Long, snooze: Boolean = false): Int {
            val base = alarmId and 0x3FFF_FFFFL
            return (base or if (snooze) SNOOZE_REQUEST_FLAG else 0L).toInt()
        }
    }
}
