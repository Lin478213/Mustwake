# Ringing service and notification (T3)

Status: **complete** (2026-10-19). Step tracking and step-gated stopping are T4; MIUIX re-skin is T5.

## Flow

```
AlarmManager
  -> AlarmReceiver (broadcast)
       -> AlarmService.startForegroundService(ACTION_START, alarmId)
       -> reschedule next occurrence (repeating) / disable (one-shot)
  -> AlarmService
       -> startForeground within 5 s (placeholder notification)
       -> load alarm, create/reuse session, write RINGING event
       -> update notification, start audio + vibration + wake lock
  -> user taps stop (notification action or ringing screen)
       -> ACTION_STOP -> finish session (CANCELLED) + history -> stopForeground -> stopSelf
       -> ACTION_RINGING_STOPPED broadcast closes RingingActivity
```

## `AlarmService`

- Foreground service type is selected at runtime:
  - `health` when API >= 30 and `ACTIVITY_RECOGNITION` is granted;
  - `specialUse` on API >= 34 (manifest declares the subtype property `alarm step tracking`);
  - types are tried in order, every `Throwable` from `startForeground` is caught and the next type
    is attempted, so a denied permission can never leave the alarm silent.
- `onStartCommand` returns `START_NOT_STICKY`; an alarm is re-registered by `AlarmScheduler`, not by
  a sticky service.
- Audio: `MediaPlayer` looping the default alarm/ringtone URI with
  `AudioAttributes.USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`. A failure is logged and does not
  prevent ringing state or the notification.
- Vibration: waveform `0, 800, 600` repeating at index 0.
- Wake lock: `PARTIAL_WAKE_LOCK` named `MustWake:ringing`, 10 minute timeout.
- Session: reuses an active session (future snooze re-ring) or starts a new `RINGING` session and
  writes a `RINGING` history event. Manual stop finishes as `CANCELLED` with a `CANCELLED` event.
  A system kill leaves the session active; the next boot/app-start sweep marks it `MISSED`.
- `onDestroy` releases audio, vibration and the wake lock.

## `AlarmNotifications`

- Channel `alarm_ringing`: `IMPORTANCE_HIGH`, **no sound and no vibration** (the service owns audio
  so it can be stopped deterministically), lockscreen visibility public.
- Notification: ongoing, `CATEGORY_ALARM`, `FOREGROUND_SERVICE_IMMEDIATE`, content intent →
  `RingingActivity`, one "停止" action → service `ACTION_STOP`.
- Full-screen intent is attached only when `NotificationManager.canUseFullScreenIntent()` allows it
  (API < 34 always allowed). Otherwise the heads-up + content intent still provide an entry point.
- `POST_NOTIFICATIONS` is checked and logged; requesting it is part of T5/T7 permission onboarding.

## `RingingActivity`

- Programmatic full-screen dark screen (temporary; T5 replaces it with Miuix):
  alarm time, label, step goal, hint, stop button.
- Shows over the lock screen and turns the screen on (`setShowWhenLocked` / `setTurnScreenOn` on
  API 27+, legacy window flags below).
- Finishes when the service broadcasts `ACTION_RINGING_STOPPED`, so stopping from the notification
  does not leave a stale screen.

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

`:androidApp:testDebugUnitTest` — 13 tests (9 scheduler + 4 notification):

- ringing channel is high importance, silent, no vibration, public lockscreen visibility;
- notification is ongoing, alarm category, has a stop action and a content intent;
- full-screen intent is attached/omitted according to the flag;
- blank label falls back to the default label.

Service/audio/vibration/FSI behaviour on a real device is **not verified** (no device attached).

## Known gaps

- Stop is not yet step-gated: the stop button/action ends the session as `CANCELLED` (T4 replaces it
  with the step policy, snooze and 10 s emergency stop).
- No snooze button yet (T4).
- Ringing screen is not MIUIX and has no progress ring (T5).
- Audio focus is not requested; DND/alarm-stream behaviour must be recorded on device (T8).
- `POST_NOTIFICATIONS` / FSI permission onboarding is T5/T7.
