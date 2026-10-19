package com.mustwake.alarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.mustwake.alarm.MustWakeApp
import com.mustwake.alarm.alarm.AlarmMaintenance
import com.mustwake.alarm.alarm.AlarmScheduler
import com.mustwake.alarm.alarm.ScheduleOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Re-registers alarms after the system forgets them (reboot) and after clock/timezone changes.
 *
 * Also sweeps ringing/snoozed sessions whose deadline passed while the process was dead, writing
 * a MISSED history event for each, so history never stays stuck on "ringing".
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: run {
            Log.w(TAG, "ignoring broadcast without an action")
            return
        }
        if (action !in HANDLED_ACTIONS) {
            Log.w(TAG, "ignoring broadcast with unexpected action: $action")
            return
        }
        val app = context.applicationContext as? MustWakeApp ?: run {
            Log.e(TAG, "application is not MustWakeApp, cannot reschedule alarms")
            return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val container = app.container

                val swept = AlarmMaintenance.sweepExpiredSessions(container, source = action)

                val alarms = container.alarmRepository.getEnabledAlarms()
                val scheduler = AlarmScheduler(appContext, container.historyRepository)
                val outcomes = scheduler.rescheduleAll(alarms, source = "reschedule:$action")
                val scheduled = outcomes.count { it is ScheduleOutcome.Scheduled }
                val failed = outcomes.filterIsInstance<ScheduleOutcome.Failed>()

                Log.i(
                    TAG,
                    "reschedule after $action: $scheduled/${alarms.size} alarms, " +
                        "swept $swept sessions, failed ${failed.size}",
                )
                failed.forEach { Log.e(TAG, "reschedule failed: $it") }
            } catch (throwable: Throwable) {
                Log.e(TAG, "reschedule after $action failed", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "MustWake"

        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
