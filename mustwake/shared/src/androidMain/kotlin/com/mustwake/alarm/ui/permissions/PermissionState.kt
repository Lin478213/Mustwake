package com.mustwake.alarm.ui.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Runtime permission / special access snapshot used by the onboarding screen. */
data class PermissionState(
    val notifications: Boolean,
    val exactAlarms: Boolean,
    val fullScreenIntent: Boolean,
    val activityRecognition: Boolean,
    val batteryUnrestricted: Boolean,
) {
    val criticalReady: Boolean get() = notifications && activityRecognition
    val allReady: Boolean get() = criticalReady && exactAlarms && fullScreenIntent
}

fun readPermissionState(context: Context): PermissionState = PermissionState(
    notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        granted(context, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        true
    },
    exactAlarms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    } else {
        true
    },
    fullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent() == true
    } else {
        true
    },
    activityRecognition = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        granted(context, Manifest.permission.ACTIVITY_RECOGNITION)
    } else {
        true
    },
    batteryUnrestricted = context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true,
)

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    startSafely(
        context,
        Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}"),
        ),
    )
}

fun openFullScreenIntentSettings(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startSafely(
            context,
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    } else {
        openAppDetails(context)
    }
}

fun openNotificationSettings(context: Context) {
    startSafely(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}

fun openBatteryOptimizationSettings(context: Context) {
    startSafely(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

fun openAppDetails(context: Context) {
    startSafely(
        context,
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ),
    )
}

/** HyperOS autostart screen; falls back to the app details page on other ROMs. */
fun openAutoStartSettings(context: Context) {
    val miui = Intent().apply {
        component = ComponentName(
            "com.miui.securitycenter",
            "com.miui.permcenter.autostart.AutoStartManagementActivity",
        )
    }
    if (miui.resolveActivity(context.packageManager) != null) {
        startSafely(context, miui)
    } else {
        openAppDetails(context)
    }
}

private fun startSafely(context: Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
