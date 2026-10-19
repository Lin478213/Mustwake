# MustWake

Android step-gated alarm clock. The alarm rings until the step target is reached; MIUIX UI; independent toggles for a floating bottom bar and liquid glass.

## Current state

- **T0 (toolchain + empty app) — complete.**
- **T1 (data layer) — complete**: Room database (`alarms`, `alarm_events`), DataStore settings,
  repositories, validation, JVM + Robolectric tests. See `docs/data-layer.md`.
- No UI, alarm service, scheduling or permissions yet; the app still shows a Miuix-themed
  "MustWake" screen.

## Build

```powershell
cd E:\MustWake\mustwake
.\gradlew.bat :androidApp:assembleDebug
```

APK: `androidApp\build\outputs\apk\debug\androidApp-debug.apk`

## Test

```powershell
.\gradlew.bat test                 # all JVM unit tests (49 tests)
.\gradlew.bat :data:testDebugUnitTest
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
