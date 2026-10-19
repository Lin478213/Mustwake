# Ringing service and notification (T3 + T4)

Status: **complete** through T4 (2026-10-19). Step tracking details: `docs/steps.md`.
MIUIX re-skin: T5.

## Flow

```
AlarmManager
  -> AlarmReceiver (broadcast)
       -> AlarmService.startForegroundService(ACTION_START, alarmId)
       -> reschedule next occurrence (repeating) / disable (one-shot)
  -> AlarmService
       -> startForeground within 5 s (placeholder notification)
       -> load alarm, create/reuse session, write RINGING event
       -> notification + audio + vibration + wake lock
       -> step tracking (counter baseline, detector fallback, shake fallback)
  -> stop conditions (StopPolicy)
       -> steps >= goal      -> COMPLETED
       -> 10 s long press    -> EMERGENCY
       -> shakes + long press -> DEGRADED (sensor unavailable)
       -> snooze             -> SNOOZED + scheduleSnooze, service stops
       -> ACTION_RINGING_STOPPED broadcast closes RingingActivity
```

## `AlarmService`

- Foreground service type is selected at runtime:
  - `health` when API >= 30 and `ACTIVITY_RECOGNITION` is granted;
  - `specialUse` on API >= 34 (manifest declares the subtype property `alarm step tracking`);
  - types are tried in order, every `Throwable` from `startForeground` is caught and the next type
    is attempted, so a denied permission can never leave the alarm silent.
- `onStartCommand` returns `START_NOT_STICKY`; an alarm is re-registered by `AlarmScheduler`, not by
  a sticky service. Snooze/emergency/state requests on a non-ringing service stop it immediately.
- Audio: `MediaPlayer` looping the default alarm/ringtone URI with
  `AudioAttributes.USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`. A failure is logged and does not
  prevent ringing state or the notification.
- Vibration: waveform `0, 800, 600` repeating at index 0.
- Wake lock: `PARTIAL_WAKE_LOCK` named `MustWake:ringing`, 10 minute timeout.
- Session: reuses an active session (snooze re-ring) or starts a new `RINGING` session and writes a
  `RINGING` history event. Terminal states are `COMPLETED` / `EMERGENCY` / `DEGRADED`; a system kill
  leaves the session active and the next boot/app-start sweep marks it `MISSED`.
- Steps: `baseSteps + sensorDelta`, where `baseSteps` is the persisted session step count (only
  non-zero after a snooze). Every accepted step updates the notification and broadcasts state.
- Snooze: session → `SNOOZED` (deadline persisted), `AlarmScheduler.scheduleSnooze()` registers a
  separate PendingIntent, service stops ringing. The next delivery resumes the same session.
- `onDestroy` releases audio, vibration, wake lock and unregisters sensors.

## `AlarmNotifications`

- Channel `alarm_ringing`: `IMPORTANCE_HIGH`, **no sound and no vibration** (the service owns audio
  so it can be stopped deterministically), lockscreen visibility public.
- Notification: ongoing, `CATEGORY_ALARM`, `FOREGROUND_SERVICE_IMMEDIATE`, content intent →
  `RingingActivity`, live step (or shake) progress, and a snooze action **only while snoozes remain**.
  There is intentionally no plain stop action: stopping is step-gated or a 10 s emergency press.
- Full-screen intent is attached only when `NotificationManager.canUseFullScreenIntent()` allows it
  (API < 34 always allowed). Otherwise the heads-up + content intent still provide an entry point.
- `POST_NOTIFICATIONS` is checked and logged; requesting it is part of T5/T7 permission onboarding.

## `RingingActivity`

- Programmatic full-screen dark screen (temporary; T5 replaces it with Miuix): alarm time, label,
  live step/remaining or shake progress, snooze button, stop button with a 10 s long-press.
- Asks the service for the current state on start (`ACTION_REQUEST_STATE`), so a screen opened from
  the notification does not start at 0 steps.
- Shows over the lock screen and turns the screen on (`setShowWhenLocked` / `setTurnScreenOn` on
  API 27+, legacy window flags below).
- Finishes when the service broadcasts `ACTION_RINGING_STOPPED` (toast when snoozed).

## Manifest additions

```xml
<activity android:name=".RingingActivity" android:exported="false"
    android:excludeFromRecents="true" android:launchMode="singleInstance"
    android:showWhenLocked="true" android:turnScreenOn="true"
    android:theme="@android:style/Theme.Material.NoActionBar" />

<service android:name=".service.AlarmService" android:exported="false"
    android:foregroundServiceType="health|specialUse">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="alarm step tracking" />
</service>
```

## Tests

`:androidApp:testDebugUnitTest` — 18 tests (12 scheduler + 6 notification), plus 24 T4 unit tests in
`:data` (policy, baseline, shake counting):

- scheduler: registration, replacement, cancel, snooze request-code isolation, exactness;
- notification: channel config, progress text, snooze action limit, degraded text, FSI fallback;
- policy: goal/emergency/degraded ordering, snooze limit, validation.

Service/audio/vibration/FSI/sensor behaviour on a real device is **not verified** (no device).

## Known gaps

- Audio focus is not requested; DND/alarm-stream behaviour must be recorded on a device (T8).
- Shake threshold (12 m/s²), debounce (150 ms) and 30-shake target are defaults to tune on hardware.
- The hold-to-stop button is intentionally retryable after a rejected attempt (policy decides, the
  button never latches). Verified on the emulator.
- Emulator coverage and the remaining device-only checks: `testing.md`.
