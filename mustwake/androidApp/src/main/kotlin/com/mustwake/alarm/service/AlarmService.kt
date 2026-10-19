package com.mustwake.alarm.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mustwake.alarm.MustWakeApp
import com.mustwake.alarm.data.AppContainer
import com.mustwake.alarm.data.db.AlarmEntity
import com.mustwake.alarm.data.db.AlarmEventType
import com.mustwake.alarm.data.db.AlarmSessionState
import com.mustwake.alarm.notification.AlarmNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground ringing service.
 *
 * Responsibilities (T3): promote itself to foreground within 5 s, pick a legal FGS type at
 * runtime (`health` when `ACTIVITY_RECOGNITION` is granted, otherwise `specialUse`), play alarm
 * audio on the alarm stream with looping, vibrate, hold a partial wake lock, and own the session
 * lifecycle (RINGING on start, terminal state + history on stop).
 *
 * Step tracking and step-gated stopping are T4; for now the stop action / stop button ends the
 * session as CANCELLED so the alarm can always be silenced.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var container: AppContainer
    private lateinit var notifications: AlarmNotifications

    private var currentAlarmId = 0L
    private var currentSessionId: Long? = null
    private var foregroundStarted = false
    private var stopping = false

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as MustWakeApp).container
        notifications = AlarmNotifications(this)
        notifications.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop(intent)
            else -> {
                Log.w(TAG, "ignoring start command with action ${intent?.action}")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseFeedback()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleStart(intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, 0L)
        if (alarmId <= 0L) {
            Log.w(TAG, "ignoring start without a valid alarm id")
            stopSelf()
            return
        }
        if (currentAlarmId == alarmId && foregroundStarted) {
            Log.i(TAG, "alarm $alarmId is already ringing")
            return
        }
        currentAlarmId = alarmId

        // Must reach foreground within 5 seconds; the alarm row is loaded afterwards.
        promoteToForeground(alarm = null)

        scope.launch {
            val alarm = container.alarmRepository.getAlarm(alarmId)
            if (alarm == null) {
                Log.e(TAG, "alarm $alarmId disappeared before ringing")
                shutdown()
                return@launch
            }
            val session = container.sessionRepository.activeSession(alarmId)
                ?: container.sessionRepository.startSession(alarmId)
            currentSessionId = session.id
            container.historyRepository.record(
                alarmId = alarmId,
                type = AlarmEventType.RINGING,
                steps = session.steps,
                reason = "service start",
            )
            Log.i(TAG, "ringing alarm $alarmId (session ${session.id})")

            promoteToForeground(alarm)
            startFeedback()
        }
    }

    private fun handleStop(intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, currentAlarmId)
        if (stopping) return
        stopping = true
        releaseFeedback()

        val sessionId = currentSessionId
        scope.launch {
            if (sessionId != null) {
                runCatching {
                    val finished = container.sessionRepository.finish(
                        sessionId = sessionId,
                        state = AlarmSessionState.CANCELLED,
                    )
                    container.historyRepository.record(
                        alarmId = alarmId,
                        type = AlarmEventType.CANCELLED,
                        steps = finished.steps,
                        reason = "manual stop",
                    )
                }.onFailure { Log.e(TAG, "failed to finish session $sessionId", it) }
            }
            withContext(Dispatchers.Main) {
                ServiceCompat.stopForeground(this@AlarmService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                sendBroadcast(Intent(ACTION_RINGING_STOPPED).setPackage(packageName))
                stopSelf()
            }
        }
    }

    private fun promoteToForeground(alarm: AlarmEntity?) {
        val notification = notifications.buildRinging(
            alarmId = currentAlarmId,
            label = alarm?.label.orEmpty(),
            hour = alarm?.hour ?: 0,
            minute = alarm?.minute ?: 0,
            goalSteps = alarm?.goalSteps ?: 0,
        )

        var lastError: Throwable? = null
        for (type in foregroundTypes()) {
            try {
                ServiceCompat.startForeground(this, AlarmNotifications.NOTIFICATION_ID, notification, type)
                foregroundStarted = true
                Log.i(TAG, "foreground started with type $type")
                return
            } catch (throwable: Throwable) {
                lastError = throwable
                Log.w(TAG, "startForeground failed with type $type, trying next", throwable)
            }
        }

        Log.e(TAG, "could not enter foreground with any service type", lastError)
        stopSelf()
    }

    private fun foregroundTypes(): List<Int> {
        val types = mutableListOf<Int>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && hasActivityRecognition()) {
            types += ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            types += ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
        if (types.isEmpty()) types += 0
        return types
    }

    private fun hasActivityRecognition(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACTIVITY_RECOGNITION,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun startFeedback() {
        startAudio()
        startVibration()
        acquireWakeLock()
    }

    private fun startAudio() {
        if (mediaPlayer != null) return
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (throwable: Throwable) {
            Log.e(TAG, "unable to start alarm audio", throwable)
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    private fun startVibration() {
        val systemVibrator = getSystemService(Vibrator::class.java) ?: return
        if (!systemVibrator.hasVibrator()) return
        vibrator = systemVibrator
        try {
            systemVibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0L, 800L, 600L), 0),
            )
        } catch (throwable: Throwable) {
            Log.e(TAG, "unable to start vibration", throwable)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock != null) return
        val powerManager = getSystemService(PowerManager::class.java) ?: return
        wakeLock = powerManager
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:ringing")
            .apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
    }

    private fun releaseFeedback() {
        mediaPlayer?.runCatching {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null

        vibrator?.cancel()
        vibrator = null

        wakeLock?.runCatching { if (isHeld) release() }
        wakeLock = null
    }

    private fun shutdown() {
        releaseFeedback()
        stopSelf()
    }

    companion object {
        private const val TAG = "MustWake"

        const val ACTION_START = "com.mustwake.alarm.action.START_RINGING"
        const val ACTION_STOP = "com.mustwake.alarm.action.STOP_RINGING"

        /** Broadcast to the ringing screen when the session ends. */
        const val ACTION_RINGING_STOPPED = "com.mustwake.alarm.action.RINGING_STOPPED"
        const val EXTRA_ALARM_ID = "com.mustwake.alarm.extra.ALARM_ID"

        private const val WAKE_LOCK_TIMEOUT_MS = 10L * 60L * 1000L

        fun start(context: Context, alarmId: Long) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ALARM_ID, alarmId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context, alarmId: Long) {
            context.startService(stopIntent(context, alarmId))
        }

        fun stopIntent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_ALARM_ID, alarmId)
    }
}
