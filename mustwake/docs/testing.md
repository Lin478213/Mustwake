# Emulator test run (T8 pre-check)

Date: 2026-10-19/20. Emulator is a **pre-check**, not the HyperOS acceptance in T8.

## Environment (all on E:)

| item | value |
|---|---|
| SDK | `E:\MustWake\toolchain\android-sdk` |
| emulator | 37.2.12 (manual zip install) |
| image | `system-images;android-36;google_apis;x86_64` (rev 7) |
| AVD | `E:\MustWake\toolchain\android-home\avd\mustwake36` (manual config, E: only) |
| user home | `ANDROID_USER_HOME=E:\MustWake\toolchain\android-home` |
| accel | WHPX usable |
| device | 1080×2400, 420 dpi, API 36 |

Start:

```powershell
$env:ANDROID_USER_HOME="E:\MustWake\toolchain\android-home"
$env:ANDROID_AVD_HOME="E:\MustWake\toolchain\android-home\avd"
E:\MustWake\toolchain\android-sdk\emulator\emulator.exe -avd mustwake36 `
  -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot
```

`sdkmanager` stalled at ~0.5 MB/s during install, so the emulator and image zips were downloaded
with curl (60+ MB/s) and unpacked into the SDK. `avdmanager` refused to create the AVD without
`package.xml`, so the AVD was created manually with the same `config.ini` fields.

## Results

| # | scenario | result | evidence |
|---|---|---|---|
| 1 | fresh install → permission guide | **PASS** | screenshot `s2.png`; all rows "已开启" after `-g` + appops |
| 2 | complete guide → empty alarm list | **PASS** | `s3.png` |
| 3 | create alarm via UI (07:00, daily, 50 steps) | **PASS** | list shows `下次 明天 07:00`; DB row created |
| 4 | exact alarm registration | **PASS** | `dumpsys alarm`: RTC_WAKEUP 2026-10-20 07:00, `exactAllowReason=permission`, showIntent → RingingActivity |
| 5 | lock screen → alarm fires with FSI | **PASS** | `ring1.png` full-screen 07:00; notification `category=alarm`, `fullscreenIntent` attached |
| 6 | no step counter → shake fallback | **PASS** | log `no step sensor available`, UI `摇晃 0 / 30 次` |
| 7 | shake 30 times → 30/30 | **PASS** | injected accelerometer via `adb emu`; UI `30 / 30` |
| 8 | long press with 0 shakes | **PASS (gated)** | log `emergency ignored: degraded=true, shakes=0/30`, service kept running |
| 9 | shake + 10 s hold → DEGRADED | **PASS** | log `finishing alarm 1 as DEGRADED`; session row `DEGRADED`; service gone |
| 10 | snooze (1 min) | **PASS** | session `SNOOZED`, count 1, deadline set; `dumpsys` shows 07:02 snooze **and** next day 07:00 |
| 11 | snooze re-ring | **PASS** | same session id returns to RINGING, `snooze=1/3`; heads-up + tap opens ringing screen (`ring5.png`) |
| 12 | appearance matrix (off/off, on/off, off/on, on/on) | **PASS** | `s3.png`, `floating_solid.png`, `glass_std.png`, `glass1.png` |
| 13 | reboot → alarm restored | **PASS** | after unlock, log `BOOT_COMPLETED: 1/1 alarms`; alarm re-registered |
| 14 | app-start missed sweep | **PASS** | inserted expired RINGING row → `MISSED`; history `MISSED / session expired (app-start)` |

## Bug found and fixed

**Hold-to-stop latched disabled after a rejected attempt.** The button kept a local `fired` flag
set when the 10 s hold completed, even if `StopPolicy` rejected the stop (degraded mode with 0
shakes). After that the button was permanently disabled and the alarm could no longer be stopped,
even after reaching 30 shakes.

Fix: remove the `fired` latch; the button stays retryable, and only the service policy decides.
Re-tested: hold at 0 shakes → ignored; shake 30 → hold again → `DEGRADED` (log at 07:02:43).

## Not covered by the emulator

- Real step counter (`TYPE_STEP_COUNTER`) does not exist on the emulator → the normal step-gated
  path is still unverified on hardware.
- Audio/vibration, alarm stream and DND behaviour (emulator started with `-no-audio`).
- HyperOS battery/autostart behaviour, FSI revocation, exact-alarm revocation, permission denial
  after first grant, force-stop restrictions.
- Boot sweep only received `BOOT_COMPLETED` after unlocking, which matches Android's
  credential-protected storage rules.
