# Steps, stop policy, snooze (T4)

Status: **complete** (2026-10-19). MIUIX ringing UI and the progress ring are T5.

## Step source

`StepSensorRepository` (`androidApp/.../sensor/`) registers a session-scoped listener:

| priority | sensor | behaviour |
|---|---|---|
| 1 | `TYPE_STEP_COUNTER` | first reading becomes the baseline; every reading returns `raw - baseline` |
| 2 | `TYPE_STEP_DETECTOR` | one sensor event = one step |
| 3 | — | returns `UNAVAILABLE` (missing hardware or denied `ACTIVITY_RECOGNITION`) |

`StepBaselineTracker` (pure Kotlin, `:data`) owns the counter math and is covered by JVM tests:
pre-alarm steps are never counted, a counter rollback clamps to 0, and `reset()` re-arms the
baseline when a new service instance starts.

## Degraded shake path

- `ShakeCounter` (pure): accepts accelerometer magnitudes ≥ 12 m/s² (≈1.2 g above rest), debounced
  at 150 ms, saturating at the configured target (default 30). Fully unit tested.
- `ShakeDetector` (Android): listens to `TYPE_ACCELEROMETER` and forwards accepted counts.
- If neither step sensor can be registered, the service enters degraded mode, starts the shake
  detector and shows shake progress instead of step progress. Stopping then requires **shakes +
  the 10 s long press**.
- If no accelerometer exists either, degraded mode is skipped so the emergency long press still
  works (no dead-lock).

## StopPolicy (pure, `:data`)

`StopPolicy.evaluate(StopPolicyInput)` is the single source of truth:

1. `currentSteps >= goalSteps` → **COMPLETED** (checked first, even if an emergency press races);
2. `emergencyRequested` in normal mode → **EMERGENCY**;
3. `emergencyRequested` in degraded mode with enough shakes → **DEGRADED**;
4. otherwise → **Continue** (shakes alone never stop a session).

`StopPolicy.canSnooze(snoozeCount, maxSnoozes)` implements the 3-snooze limit from settings.

14 JVM tests cover goal boundaries, emergency vs goal ordering, degraded gating (29 vs 30 shakes),
shake-only continuation, snooze limits and input validation.

## Service integration

- Session steps are `baseSteps + sensorDelta`, where `baseSteps` is the persisted session step count
  (non-zero only after a snooze, so steps continue accumulating across snoozes).
- Every accepted step (or shake) updates the foreground notification and broadcasts
  `ACTION_RINGING_UPDATE` with steps, goal, snooze count/limit/minutes, degraded flag and shake
  progress.
- Reaching the goal finishes the session as `COMPLETED` and writes a `COMPLETED` history event;
  the emergency long press writes `EMERGENCY`; degraded completion writes `DEGRADED`.
- Snooze: session → `SNOOZED` with the persisted deadline, `AlarmScheduler.scheduleSnooze()`
  registers a **separate** PendingIntent (request code flag `0x40000000`), so a snooze never
  replaces the repeating alarm's next occurrence. A `SNOOZED` history event records steps and the
  snooze index. When the alarm fires again, the service resumes the same session.
- The notification no longer offers a plain stop: it shows live progress and a snooze action only
  while snoozes remain.
- `scheduler.cancelSnooze(alarmId)` runs when a session ends, so a stale snooze cannot ring later.

## Ringing screen

`RingingActivity` now shows live steps (`已走 X / Y 步`, remaining steps) or shake progress, enables
the snooze button while snoozes remain, and only stops on a **10 second continuous press** of the
stop button (short press shows a hint). It asks the service for the current state on start
(`ACTION_REQUEST_STATE`) and closes on `ACTION_RINGING_STOPPED` (with a toast when snoozed).

## Tests

| suite | count |
|---|---|
| `:data:testDebugUnitTest` | 97 |
| `:androidApp:testDebugUnitTest` | 18 |
| total | 115, 0 failures |

New coverage: `StopPolicyTest` (14), `StepBaselineTrackerTest` (5), `ShakeCounterTest` (5),
notification snooze/degraded progress, scheduler snooze request-code isolation (4).

## Known gaps

- No device: real `TYPE_STEP_COUNTER` behaviour, lock-screen counting, FGS `health` type, shake
  sensitivity and DND/audio behaviour are **unverified**. Thresholds (12 m/s², 150 ms, 30 shakes)
  are defaults to tune on hardware at T8.
- The ringing screen is still a programmatic layout; progress ring and Miuix styling are T5.
- `ACTIVITY_RECOGNITION` permission request flow and the degraded-mode explanation UI are T5/T7.
