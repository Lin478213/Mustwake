package com.mustwake.alarm.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mustwake.alarm.R
import com.mustwake.alarm.RingingActivity
import com.mustwake.alarm.data.stop.StopPolicy
import com.mustwake.alarm.service.AlarmService

/**
 * Builds and posts the ringing notification.
 *
 * The channel is silent and does not vibrate: [AlarmService] owns audio and vibration so they can
 * be stopped and re-armed deterministically. The notification never offers a plain "stop" action —
 * stopping is step-gated (or a 10 s emergency long press on the ringing screen); it only offers
 * snooze while snoozes remain.
 */
class AlarmNotifications(private val context: Context) {

    private val manager: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_RINGING,
            context.getString(R.string.alarm_channel_ringing_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.alarm_channel_ringing_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        manager?.createNotificationChannel(channel)
    }

    fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            manager?.canUseFullScreenIntent() == true

    fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    /**
     * The ringing / foreground-service notification.
     *
     * @param showFullScreen whether to attach the full-screen intent (see [canUseFullScreenIntent]).
     */
    fun buildRinging(
        alarmId: Long,
        label: String,
        hour: Int,
        minute: Int,
        goalSteps: Int,
        steps: Int = 0,
        snoozeCount: Int = 0,
        maxSnoozes: Int = 0,
        snoozeMinutes: Int = 5,
        degradedMode: Boolean = false,
        shakeCount: Int = 0,
        shakeTarget: Int = 30,
        showFullScreen: Boolean = canUseFullScreenIntent(),
    ): Notification {
        val contentIntent = contentIntent(alarmId, label, hour, minute, goalSteps)
        val displayLabel = label.ifBlank { context.getString(R.string.ringing_default_label) }
        val progress = if (degradedMode) {
            context.getString(R.string.alarm_notification_degraded, shakeCount, shakeTarget)
        } else {
            context.getString(R.string.alarm_notification_progress, steps, goalSteps)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.alarm_notification_title))
            .setContentText(progress)
            .setSubText(displayLabel)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply {
                if (showFullScreen) {
                    setFullScreenIntent(contentIntent, true)
                }
            }

        if (StopPolicy.canSnooze(snoozeCount, maxSnoozes)) {
            builder.addAction(
                android.R.drawable.ic_menu_recent_history,
                context.getString(R.string.alarm_notification_snooze, snoozeMinutes),
                snoozePendingIntent(alarmId),
            )
        }

        return builder.build()
    }

    private fun snoozePendingIntent(alarmId: Long): PendingIntent {
        val intent = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_SNOOZE)
            .putExtra(AlarmService.EXTRA_ALARM_ID, alarmId)
        return PendingIntent.getService(
            context,
            requestCode(alarmId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun contentIntent(
        alarmId: Long,
        label: String,
        hour: Int,
        minute: Int,
        goalSteps: Int,
    ): PendingIntent {
        val intent = RingingActivity.intent(context, alarmId, label, hour, minute, goalSteps)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            requestCode(alarmId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun requestCode(alarmId: Long): Int = (alarmId and 0x7FFF_FFFFL).toInt()

    companion object {
        const val CHANNEL_RINGING = "alarm_ringing"
        const val NOTIFICATION_ID = 1001
    }
}
