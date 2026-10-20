# Emulator test run (T8 pre-check)

Date: 2026-10-19/20. Emulator is a **pre-check**, not the HyperOS acceptance in T8.

## Environment (Linux)

The original run (2026-10-19/20) was on Windows with WHPX. The toolchain was migrated to Linux
x86_64 on 2026-10-20; the same AVD is reused with KVM acceleration.

| item | value |
|---|---|
| SDK | `/home/lin/Java/MustWake/toolchain/android-sdk` |
| emulator | 37.2.12 (linux-x64) |
| image | `system-images;android-36;google_apis;x86_64` (rev 7) |
| AVD | `/home/lin/Java/MustWake/toolchain/android-home/avd/mustwake36` |
| user home | `ANDROID_USER_HOME=/home/lin/Java/MustWake/toolchain/android-home` |
| accel | KVM (`/dev/kvm` present) |
| device | 1080×2400, 420 dpi, API 36 |

Start:

```bash
export ANDROID_USER_HOME=/home/lin/Java/MustWake/toolchain/android-home
export ANDROID_AVD_HOME=/home/lin/Java/MustWake/toolchain/android-home/avd
/home/lin/Java/MustWake/toolchain/android-sdk/emulator/emulator -avd mustwake36 \
  -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -no-snapshot
```

On the original Windows setup, `sdkmanager` stalled at ~0.5 MB/s, so the emulator and image zips
were downloaded with curl and unpacked into the SDK; `avdmanager` refused to create the AVD
without `package.xml`, so the AVD was created manually with the same `config.ini` fields.

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

## M1-A3 Baseline Profile (emulator pre-check)

Date: 2026-10-20. **Emulator numbers are a pre-check only; G0 real-device runs are the
acceptance gate.** Method and tables are recorded so the G0 run can be compared against
the same setup.

### Method

| item | value |
|---|---|
| device | emulator mustwake36 (API 36, 1080×2400, 4 cores, KVM); benchmark output prints `WARNING: Running on Emulator` |
| metric | `StartupTimingMetric` → `timeToInitialDisplayMs`, `StartupMode.COLD` |
| iterations | 5 per test (each cell below is n=5) |
| before | `CompilationMode.None()` (no profile) |
| after | `CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require)` — fails if the baseline profile is not installed |
| profile collectors | `MainActivityBaselineProfile` / `RingingActivityBaselineProfile` (cold start), maxIterations=5, stableIterations=3 |
| commands | `./gradlew :androidApp:generateBaselineProfile -PbenchmarkAllowEmulator=true` then `./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest -PbenchmarkAllowEmulator=true` |
| percentiles | P50/P90 computed from the 5 `runs` values in `benchmarkData.json` (linear interpolation). With n=5 the P90 sits close to the max — indicative, not a tail estimate |
| raw data | `baselineprofile/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/.../com.mustwake.alarm.baselineprofile-benchmarkData.json` (build output, gitignored) |

### Results (timeToInitialDisplayMs, ms)

| scenario | before P50 | before P90 | after P50 | after P90 | ΔP50 |
|---|---|---|---|---|---|
| MainActivity cold start | 348.8 | 393.4 | 344.5 | 385.0 | −1.2% |
| RingingActivity cold start | 664.4 | 694.0 | 625.8 | 668.5 | −5.8% |

Main cold start is flat within noise (per-run CV ≈ 9–12%); Ringing improves modestly and is
the slower path (turnScreenOn/showWhenLocked flags). Both `Require` tests passed, so the
profile was actually installed and applied for the "after" column — this is not a null
comparison. **All values are emulator pre-check; only G0 real-device numbers count.**
RingingActivity is `exported=false` with `turnScreenOn`/`showWhenLocked`; if those flags
distort real-device startup, recalibrate the scenario after G0.

`RingingActivity` is started from instrumentation with an explicit `setClassName`
component and no extras (renders the default ringing state). No app manifest change was
needed — no Permission Denial on API 36.

### Profile artifact

`androidApp/src/release/generated/baselineProfiles/baseline-prof.txt` — 13,742 rules
(one per line), merged from both collectors (53 rules reference the Ringing journey,
12 the Main/list journey). Produced by `:androidApp:generateBaselineProfile` and kept in
the repo as the real-scenario profile (spike empty-scenario profiles were not committed).
`generateBaselineProfile` warns that no `includeInStartupProfile=true` rules were generated;
startup profiles are out of scope, only baseline profiles are shipped.
