# MustWake

Android step-gated alarm clock. The alarm rings until the step target is reached; MIUIX UI; independent toggles for a floating bottom bar and liquid glass.

## Current state

- **T0 (toolchain + empty app) — complete.**
- **T1 (data layer) — complete**: Room database (`alarms`, `alarm_events`), DataStore settings,
  repositories, validation, JVM + Robolectric tests. See `docs/data-layer.md`.
- **T2 (scheduling + permissions) — complete**: `AlarmScheduler` (`setAlarmClock` with inexact
  fallback), boot/time-zone reschedule, session table v2 + missed sweep, manifest permissions.
  See `docs/scheduling.md`.
- **T3 (ringing service) — complete**: foreground service with runtime `health`/`specialUse` type,
  looping alarm audio + vibration + wake lock, silent high-importance channel with full-screen
  intent fallback, full-screen `RingingActivity`. See `docs/ringing.md`.
- Still missing: step tracking / step-gated stop (T4), MIUIX screens (T5), the two toggles and
  liquid glass (T6). A fired alarm currently rings until the user stops it manually.

## Build

```powershell
cd E:\MustWake\mustwake
.\gradlew.bat :androidApp:assembleDebug
```

APK: `androidApp\build\outputs\apk\debug\androidApp-debug.apk`

## Test

```powershell
.\gradlew.bat test                 # all JVM unit tests (86 tests)
.\gradlew.bat :data:testDebugUnitTest
.\gradlew.bat :androidApp:testDebugUnitTest
```

The first Robolectric run downloads its Android runtime (~5 min on this network).

The project is self-contained under `E:\MustWake\toolchain`; see `docs/toolchain.md` for versions and paths.

## Spec and plan

- `.omc/specs/deep-interview-step-alarm.md`
- `.omc/plans/step-alarm.md`

## Install (requires a connected device)

```powershell
adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
```
