# Scheduling and permissions (T2)

Status: **complete** (2026-10-19). Ringing service / full-screen UI are T3; this task covers
registration, exact-alarm fallback, boot/timezone recovery and session persistence.

## What is implemented

### `AlarmScheduler` (`androidApp/.../alarm/AlarmScheduler.kt`)

- `schedule(alarm)` — computes the next occurrence and registers it.
- `scheduleSnooze(alarmId, triggerAt, steps, snoozeCount)` — re-registers a snooze alarm; the step
  count and snooze index go into the `SCHEDULED` history reason (audit trail after process death).
- `rescheduleAll(alarms, source)` — used after boot / time changes.
- `cancel(alarmId)` — cancels the `PendingIntent` and the AlarmManager entry.
- `canScheduleExactAlarms()` — `true` below API 31, otherwise `AlarmManager.canScheduleExactAlarms()`.

Registration rules:

| item | value |
|---|---|
| exact path | `setAlarmClock(AlarmClockInfo(triggerAt, showIntent), pendingIntent)` |
| fallback | `setAndAllowWhileIdle(RTC_WAKEUP, triggerAt, pendingIntent)` when exact permission is missing or `SecurityException` is thrown |
| request code | `alarmId and 0x7FFFFFFF` (one registration per alarm; rescheduling replaces it) |
| flags | `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE` |
| `showIntent` | `MainActivity` with `ACTION_SHOW_RINGING` + alarm id (T3 turns this into the ringing screen) |
| history | every successful registration writes a `SCHEDULED` event with `source/exact|inexact@epochMillis` |

`NextTriggerCalculator` (in `:data`, pure Kotlin/JVM) computes the trigger:

- one-shot (`repeatDaysMask == 0`): today at the alarm time, or tomorrow when the time has passed;
- repeating: nearest matching weekday strictly in the future (`Monday = 1 … Sunday = 64`).

### Receivers

`AlarmReceiver` (`exported=false`, explicit PendingIntent only)

- validates action and alarm id, logs `alarm <id> fired`;
- repeating alarms: immediately registers the next occurrence, so a missed window does not break
  the series;
- one-shot alarms: disables the alarm after firing so a reboot cannot resurrect it;
- T3 adds the `startForegroundService` + full-screen notification call here.

`BootReceiver` (`BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED`)

- sweeps expired sessions (see below) and writes `MISSED` history events;
- re-registers all enabled alarms;
- logs a summary: rescheduled / total / swept / failed.

`MustWakeApp` also runs the session sweep on app start, so a process death outside boot does not
leave history stuck on "ringing".

### Manifest

Permissions declared in `androidApp/src/main/AndroidManifest.xml`:

`SCHEDULE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`,
`FOREGROUND_SERVICE_HEALTH`, `FOREGROUND_SERVICE_SPECIAL_USE`, `ACTIVITY_RECOGNITION`, `WAKE_LOCK`,
`VIBRATE`, `RECEIVE_BOOT_COMPLETED`.

`<application android:name=".MustWakeApp">`; `MainActivity` is `singleTop`.

## Sessions (database v2)

`alarm_sessions` was added so a snooze deadline survives process death:

| column | notes |
|---|---|
| id | PK autoincrement |
| alarm_id | FK → `alarms.id`, `ON DELETE CASCADE` |
| state | `RINGING`, `SNOOZED`, `COMPLETED`, `EMERGENCY`, `DEGRADED`, `MISSED`, `CANCELLED` |
| started_at / updated_at | epoch millis |
| steps | accumulated across snoozes (never reset) |
| snooze_count | incremented by `SessionRepository.snooze` |
| deadline_at | missed window while RINGING (30 min), or snooze re-ring deadline; `NULL` when terminal |

`SessionRepository`: `startSession`, `snooze` (deadline must be in the future, steps are preserved
as a maximum), `resume` (SNOOZED → RINGING with a fresh 30 min missed window), `finish` (terminal
states only), `sweepExpired` (deadline passed → MISSED).

Migration `MIGRATION_1_2` creates the table + indices, `MustWakeDatabase` is version 2 and exports
`schema/2.json`. No destructive migration.

## Tests

| suite | count |
|---|---|
| `:data:testDebugUnitTest` | 73 |
| `:androidApp:testDebugUnitTest` | 9 |
| total | 82, 0 failures |

Covered: trigger calculation (one-shot/repeat/DST-free zone, exactly-now, weekday rollover),
session state transitions, sweep, migration v1→v2 on real SQLite, scheduler registration /
replacement / cancel / snooze / history via Robolectric `ShadowAlarmManager`.

## Known gaps

- **No ringing yet**: `AlarmReceiver` only logs and reschedules. The foreground service, full-screen
  intent, audio/vibration and step tracking are T3/T4.
- No device connected: `dumpsys alarm` and real `setAlarmClock` behaviour are unverified.
- `POST_NOTIFICATIONS` / `USE_FULL_SCREEN_INTENT` / `SCHEDULE_EXACT_ALARM` permission UX and deep
  links are T5/T7; T2 only declares and checks them.
- `USE_EXACT_ALARM` is intentionally not requested (Play policy); fallback is inexact scheduling.
- The Android 14 `specialUse` FGS subtype property is added in T3 together with the service.
