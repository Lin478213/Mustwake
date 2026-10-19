package com.mustwake.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mustwake.alarm.MustWakeApp
import com.mustwake.alarm.alarm.AlarmScheduler
import com.mustwake.alarm.data.alarm.AlarmRepeatDays
import com.mustwake.alarm.service.AlarmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives the broadcast registered by [AlarmScheduler].
 *
 * Starts [AlarmService] immediately (foreground within 5 s is the service's responsibility),
 * then keeps repeating alarms chained by registering the next occurrence. One-shot alarms are
 * disabled after firing so a reboot cannot resurrect them.
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM_FIRED) {
            Log.w(TAG, "ignoring broadcast with unexpected action: ${intent.action}")
            return
        }
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
        if (alarmId <= 0L) {
            Log.w(TAG, "ignoring broadcast without a valid alarm id")
            return
        }

        Log.i(TAG, "alarm $alarmId fired")
        try {
            AlarmService.start(context, alarmId)
        } catch (throwable: Throwable) {
            Log.e(TAG, "failed to start ringing service for $alarmId", throwable)
        }

        val app = context.applicationContext as? MustWakeApp ?: run {
            Log.e(TAG, "application is not MustWakeApp, cannot reschedule alarm $alarmId")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                rescheduleRepeatingAlarm(context.applicationContext, app, alarmId)
            } catch (throwable: Throwable) {
                Log.e(TAG, "failed to reschedule repeating alarm $alarmId", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun rescheduleRepeatingAlarm(
        context: Context,
        app: MustWakeApp,
        alarmId: Long,
    ) {
        val container = app.container
        val alarm = container.alarmRepository.getAlarm(alarmId) ?: run {
            Log.w(TAG, "alarm $alarmId no longer exists")
            return
        }
        if (!alarm.enabled) return

        if (alarm.repeatDaysMask == AlarmRepeatDays.NONE) {
            // A one-shot alarm must not come back tomorrow just because the device reboots.
            container.alarmRepository.setEnabled(alarmId, false)
            Log.i(TAG, "one-shot alarm $alarmId disabled after firing")
            return
        }

        val scheduler = AlarmScheduler(context, container.historyRepository)
        val outcome = scheduler.schedule(alarm, source = "after-fire")
        Log.i(TAG, "repeating alarm $alarmId rescheduled: $outcome")
    }

    companion object {
        private const val TAG = "MustWake"

        const val ACTION_ALARM_FIRED = "com.mustwake.alarm.action.ALARM_FIRED"
        const val EXTRA_ALARM_ID = "com.mustwake.alarm.extra.ALARM_ID"
    }
}
