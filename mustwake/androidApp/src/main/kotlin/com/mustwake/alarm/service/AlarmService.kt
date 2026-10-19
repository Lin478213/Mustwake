package com.mustwake.alarm.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.mustwake.alarm.alarm.AlarmScheduler
import com.mustwake.alarm.data.AppContainer
import com.mustwake.alarm.data.db.AlarmEntity
import com.mustwake.alarm.data.db.AlarmEventType
import com.mustwake.alarm.data.db.AlarmSessionState
import com.mustwake.alarm.data.settings.AppSettings
import com.mustwake.alarm.data.stop.StopDecision
import com.mustwake.alarm.data.stop.StopPolicy
import com.mustwake.alarm.data.stop.StopPolicyInput
import com.mustwake.alarm.data.stop.StopReason
import com.mustwake.alarm.notification.AlarmNotifications
import com.mustwake.alarm.sensor.ShakeDetector
import com.mustwake.alarm.sensor.StepSensorRepository
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground ringing service.
 *
 * Responsibilities:
 * - promote itself to foreground within 5 s, picking a legal FGS type at runtime (`health` when
 *   `ACTIVITY_RECOGNITION` is granted, otherwise `specialUse`, with a fallback chain);
 * - play looping alarm audio, vibrate and hold a partial wake lock;
 * - track steps (counter with baseline, detector fallback) and let [StopPolicy] decide when the
 *   session completes, is stopped as emergency, or completes in degraded shake mode;
 * - own the session lifecycle and persist steps across snoozes.
 *
 * Snooze, step completion and emergency stops are T4; the ringing UI re-skins them in T5.
 */
class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var container: AppContainer
    private lateinit var notifications: AlarmNotifications
    private lateinit var scheduler: AlarmScheduler

    private var currentAlarmId = 0L
    private var currentSessionId: Long? = null
    private var alarm: AlarmEntity? = null
    private var settings: AppSettings? = null

    private var baseSteps = 0
    private var currentSteps = 0
    private var snoozeCount = 0
    private var degradedMode = false
    private var shakeCount = 0

    private var foregroundStarted = false
    private var foregroundType = 0
    private var stopping = false

    private var stepSensor: StepSensorRepository? = null
    private var shakeDetector: ShakeDetector? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as MustWakeApp).container
        notifications = AlarmNotifications(this)
        notifications.ensureChannels()
        scheduler = AlarmScheduler(this, container.historyRepository)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_SNOOZE -> if (foregroundStarted) handleSnooze() else stopSelf()
            ACTION_EMERGENCY -> if (foregroundStarted) handleEmergency() else stopSelf()
            ACTION_REQUEST_STATE -> if (foregroundStarted) publishState() else stopSelf()
            else -> {
                Log.w(TAG, "ignoring start command with action ${intent?.action}")
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopSensors()
        releaseFeedback()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // region start

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
        stopping = false

        // Must reach foreground within 5 seconds; the alarm row is loaded afterwards.
        promoteToForeground(force = true)

        scope.launch {
            val loadedAlarm = container.alarmRepository.getAlarm(alarmId)
            if (loadedAlarm == null) {
                Log.e(TAG, "alarm $alarmId disappeared before ringing")
                shutdown()
                return@launch
            }
            val loadedSettings = container.settingsRepository.currentSettings()
            alarm = loadedAlarm
            settings = loadedSettings

            val existing = container.sessionRepository.activeSession(alarmId)
            val resumedFromSnooze = existing?.state == AlarmSessionState.SNOOZED
            val session = when {
                existing == null -> container.sessionRepository.startSession(alarmId)
                resumedFromSnooze -> container.sessionRepository.resume(existing.id)
                else -> existing
            }
            currentSessionId = session.id
            baseSteps = session.steps
            currentSteps = session.steps
            snoozeCount = session.snoozeCount

            container.historyRepository.record(
                alarmId = alarmId,
                type = AlarmEventType.RINGING,
                steps = currentSteps,
                reason = if (resumedFromSnooze) "resumed after snooze" else "service start",
            )
            Log.i(
                TAG,
                "ringing alarm $alarmId (session ${session.id}, baseSteps=$baseSteps, " +
                    "snooze=$snoozeCount/${loadedSettings.maxSnoozes})",
            )

            startFeedback()
            startSensors()
            publishState()
        }
    }

    // endregion

    // region stop paths

    private fun handleSnooze() {
        val activeSettings = settings ?: return
        val sessionId = currentSessionId ?: return
        if (stopping) return
        if (!StopPolicy.canSnooze(snoozeCount, activeSettings.maxSnoozes)) {
            Log.i(TAG, "snooze rejected: $snoozeCount/${activeSettings.maxSnoozes} used")
            publishState()
            return
        }

        stopping = true
        stopSensors()
        releaseFeedback()

        val alarmId = currentAlarmId
        val steps = currentSteps
        scope.launch {
            val deadlineAt = System.currentTimeMillis() + activeSettings.snoozeMinutes * 60_000L
            val session = container.sessionRepository.snooze(sessionId, deadlineAt, steps)
            scheduler.scheduleSnooze(
                alarmId = alarmId,
                triggerAt = Instant.ofEpochMilli(deadlineAt),
                steps = steps,
                snoozeCount = session.snoozeCount,
            )
            container.historyRepository.record(
                alarmId = alarmId,
                type = AlarmEventType.SNOOZED,
                steps = steps,
                reason = "snooze #${session.snoozeCount}, ${activeSettings.snoozeMinutes} min",
            )
            Log.i(TAG, "snoozed alarm $alarmId until $deadlineAt (steps=$steps)")
            goToBackground(snoozed = true)
        }
    }

    private fun handleEmergency() {
        val decision = StopPolicy.evaluate(buildPolicyInput(emergencyRequested = true))
        when (decision) {
            is StopDecision.Stop -> finishWith(decision.reason)
            StopDecision.Continue -> {
                Log.i(
                    TAG,
                    "emergency ignored: degraded=$degradedMode, shakes=$shakeCount/${settings?.shakeCount}",
                )
                publishState()
            }
        }
    }

    private fun onSteps(deltaOrTotal: Int) {
        val total = maxOf(currentSteps, deltaOrTotal)
        if (total == currentSteps) return
        currentSteps = total
        publishState()
        when (val decision = StopPolicy.evaluate(buildPolicyInput())) {
            is StopDecision.Stop -> finishWith(decision.reason)
            StopDecision.Continue -> Unit
        }
    }

    private fun finishWith(reason: StopReason) {
        if (stopping) return
        stopping = true
        stopSensors()
        releaseFeedback()

        val alarmId = currentAlarmId
        val sessionId = currentSessionId
        val steps = currentSteps
        val state = when (reason) {
            StopReason.STEP_GOAL -> AlarmSessionState.COMPLETED
            StopReason.EMERGENCY -> AlarmSessionState.EMERGENCY
            StopReason.DEGRADED -> AlarmSessionState.DEGRADED
        }
        Log.i(TAG, "finishing alarm $alarmId as $state after $steps steps ($reason)")

        scope.launch {
            scheduler.cancelSnooze(alarmId)
            if (sessionId != null) {
                runCatching {
                    val finished = container.sessionRepository.finish(sessionId, state, steps)
                    container.historyRepository.record(
                        alarmId = alarmId,
                        type = state.toEventType(),
                        steps = finished.steps,
                        reason = reason.name.lowercase(),
                    )
                }.onFailure { Log.e(TAG, "failed to finish session $sessionId", it) }
            }
            goToBackground(snoozed = false)
        }
    }

    private suspend fun goToBackground(snoozed: Boolean) {
        withContext(Dispatchers.Main) {
            if (foregroundStarted) {
                ServiceCompat.stopForeground(this@AlarmService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                foregroundStarted = false
            }
            sendBroadcast(
                Intent(ACTION_RINGING_STOPPED)
                    .setPackage(packageName)
                    .putExtra(EXTRA_SNOOZED, snoozed),
            )
            stopSelf()
        }
    }

    private fun shutdown() {
        stopSensors()
        releaseFeedback()
        stopSelf()
    }

    // endregion

    // region sensors

    private fun startSensors() {
        val activeSettings = settings ?: return
        val stepRepository = StepSensorRepository(this)
        stepSensor = stepRepository
        val mode = stepRepository.start { delta -> onSteps(baseSteps + delta) }
        if (mode != StepSensorRepository.Mode.UNAVAILABLE) return

        degradedMode = true
        val detector = ShakeDetector(
            context = this,
            target = activeSettings.shakeCount,
            onShake = { count ->
                shakeCount = count
                publishState()
            },
        )
        if (detector.start()) {
            shakeDetector = detector
            Log.w(TAG, "step sensor unavailable, shake fallback armed (${activeSettings.shakeCount})")
        } else {
            // No accelerometer either: keep the emergency long press usable instead of dead-locking.
            degradedMode = false
            Log.e(TAG, "no step sensor and no accelerometer; emergency long press only")
        }
    }

    private fun stopSensors() {
        stepSensor?.stop()
        stepSensor = null
        shakeDetector?.stop()
        shakeDetector = null
    }

    private fun buildPolicyInput(emergencyRequested: Boolean = false): StopPolicyInput {
        val activeSettings = settings ?: AppSettings()
        return StopPolicyInput(
            currentSteps = currentSteps,
            goalSteps = alarm?.goalSteps ?: activeSettings.defaultGoalSteps,
            emergencyRequested = emergencyRequested,
            degradedMode = degradedMode,
            shakeCount = shakeCount,
            shakeTarget = activeSettings.shakeCount,
            snoozeCount = snoozeCount,
            maxSnoozes = activeSettings.maxSnoozes,
        )
    }

    // endregion

    // region notification

    private fun publishState() {
        if (!foregroundStarted) {
            promoteToForeground(force = true)
            return
        }
        updateForegroundNotification()
        broadcastState()
    }

    private fun promoteToForeground(force: Boolean) {
        if (foregroundStarted && !force) return
        val notification = buildNotification()

        for (type in foregroundTypes()) {
            try {
                ServiceCompat.startForeground(this, AlarmNotifications.NOTIFICATION_ID, notification, type)
                foregroundStarted = true
                foregroundType = type
                Log.i(TAG, "foreground started with type $type")
                broadcastState()
                return
            } catch (throwable: Throwable) {
                Log.w(TAG, "startForeground failed with type $type, trying next", throwable)
            }
        }

        Log.e(TAG, "could not enter foreground with any service type")
        stopSelf()
    }

    private fun updateForegroundNotification() {
        try {
            ServiceCompat.startForeground(
                this,
                AlarmNotifications.NOTIFICATION_ID,
                buildNotification(),
                foregroundType,
            )
        } catch (throwable: Throwable) {
            Log.e(TAG, "failed to update ringing notification", throwable)
        }
    }

    private fun buildNotification() = notifications.buildRinging(
        alarmId = currentAlarmId,
        label = alarm?.label.orEmpty(),
        hour = alarm?.hour ?: 0,
        minute = alarm?.minute ?: 0,
        goalSteps = alarm?.goalSteps ?: 0,
        steps = currentSteps,
        snoozeCount = snoozeCount,
        maxSnoozes = settings?.maxSnoozes ?: 0,
        snoozeMinutes = settings?.snoozeMinutes ?: 5,
        degradedMode = degradedMode,
        shakeCount = shakeCount,
        shakeTarget = settings?.shakeCount ?: StopPolicyInput.DEFAULT_SHAKE_TARGET,
    )

    private fun broadcastState() {
        val activeSettings = settings ?: return
        val currentAlarm = alarm ?: return
        sendBroadcast(
            Intent(ACTION_RINGING_UPDATE)
                .setPackage(packageName)
                .putExtra(EXTRA_STEPS, currentSteps)
                .putExtra(EXTRA_GOAL, currentAlarm.goalSteps)
                .putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
                .putExtra(EXTRA_MAX_SNOOZES, activeSettings.maxSnoozes)
                .putExtra(EXTRA_SNOOZE_MINUTES, activeSettings.snoozeMinutes)
                .putExtra(EXTRA_DEGRADED, degradedMode)
                .putExtra(EXTRA_SHAKE_COUNT, shakeCount)
                .putExtra(EXTRA_SHAKE_TARGET, activeSettings.shakeCount),
        )
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
            Manifest.permission.ACTIVITY_RECOGNITION,
        ) == PackageManager.PERMISSION_GRANTED

    // endregion

    // region feedback

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
            systemVibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0L, 800L, 600L), 0))
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

    // endregion

    private fun AlarmSessionState.toEventType(): AlarmEventType = when (this) {
        AlarmSessionState.COMPLETED -> AlarmEventType.COMPLETED
        AlarmSessionState.EMERGENCY -> AlarmEventType.EMERGENCY
        AlarmSessionState.DEGRADED -> AlarmEventType.DEGRADED
        AlarmSessionState.MISSED -> AlarmEventType.MISSED
        AlarmSessionState.CANCELLED -> AlarmEventType.CANCELLED
        AlarmSessionState.RINGING, AlarmSessionState.SNOOZED -> AlarmEventType.CANCELLED
    }

    companion object {
        private const val TAG = "MustWake"

        const val ACTION_START = "com.mustwake.alarm.action.START_RINGING"
        const val ACTION_SNOOZE = "com.mustwake.alarm.action.SNOOZE_RINGING"
        const val ACTION_EMERGENCY = "com.mustwake.alarm.action.EMERGENCY_STOP"
        const val ACTION_REQUEST_STATE = "com.mustwake.alarm.action.REQUEST_RINGING_STATE"

        /** Broadcast to the ringing screen with the latest progress. */
        const val ACTION_RINGING_UPDATE = "com.mustwake.alarm.action.RINGING_UPDATE"

        /** Broadcast to the ringing screen when the session ends (completed/snoozed/stopped). */
        const val ACTION_RINGING_STOPPED = "com.mustwake.alarm.action.RINGING_STOPPED"

        const val EXTRA_ALARM_ID = "com.mustwake.alarm.extra.ALARM_ID"
        const val EXTRA_STEPS = "com.mustwake.alarm.extra.STEPS"
        const val EXTRA_GOAL = "com.mustwake.alarm.extra.GOAL"
        const val EXTRA_SNOOZE_COUNT = "com.mustwake.alarm.extra.SNOOZE_COUNT"
        const val EXTRA_MAX_SNOOZES = "com.mustwake.alarm.extra.MAX_SNOOZES"
        const val EXTRA_SNOOZE_MINUTES = "com.mustwake.alarm.extra.SNOOZE_MINUTES"
        const val EXTRA_DEGRADED = "com.mustwake.alarm.extra.DEGRADED"
        const val EXTRA_SHAKE_COUNT = "com.mustwake.alarm.extra.SHAKE_COUNT"
        const val EXTRA_SHAKE_TARGET = "com.mustwake.alarm.extra.SHAKE_TARGET"
        const val EXTRA_SNOOZED = "com.mustwake.alarm.extra.SNOOZED"

        private const val WAKE_LOCK_TIMEOUT_MS = 10L * 60L * 1000L

        fun start(context: Context, alarmId: Long) {
            val intent = Intent(context, AlarmService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ALARM_ID, alarmId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun snooze(context: Context, alarmId: Long) {
            context.startService(
                Intent(context, AlarmService::class.java)
                    .setAction(ACTION_SNOOZE)
                    .putExtra(EXTRA_ALARM_ID, alarmId),
            )
        }

        fun emergency(context: Context, alarmId: Long) {
            context.startService(
                Intent(context, AlarmService::class.java)
                    .setAction(ACTION_EMERGENCY)
                    .putExtra(EXTRA_ALARM_ID, alarmId),
            )
        }

        fun requestState(context: Context) {
            context.startService(
                Intent(context, AlarmService::class.java).setAction(ACTION_REQUEST_STATE),
            )
        }
    }
}
