# Permissions, HyperOS guidance, missed sessions (T7)

Status: **complete** (2026-10-19). Device verification is T8.

## First-run gate

`AppSettings.onboardingCompleted` (DataStore, default `false`) drives a full-screen
`PermissionScreen` on first launch. "完成 / 稍后设置" both set it to `true`; the page can be
reopened from **设置 → 权限状态**.

The settings flow is collected with a `null` initial value, so the gate never flashes before
DataStore emits.

## Permission matrix

| item | API | detection | action |
|---|---|---|---|
| 通知 | 33+ | `POST_NOTIFICATIONS` check | runtime request; "设置" opens app notification settings |
| 身体活动 | 29+ | `ACTIVITY_RECOGNITION` check | runtime request; "设置" opens app details |
| 精确闹钟 | 31+ | `AlarmManager.canScheduleExactAlarms()` | `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (package URI) |
| 全屏通知 | 34+ | `NotificationManager.canUseFullScreenIntent()` | `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` (package URI), app details below 34 |
| 电池优化 | 23+ | `PowerManager.isIgnoringBatteryOptimizations()` | `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` |
| 自启动 | HyperOS | not queryable | MIUI `AutoStartManagementActivity`, app details fallback |

Status is refreshed on `ON_RESUME`, so returning from a system page updates the rows without a
manual refresh. Every startActivity is wrapped in `runCatching`; an unavailable OEM page falls back
to app details instead of crashing.

## Degradation when denied

| denied | behaviour |
|---|---|
| notifications | FGS still runs; the notification may be hidden (task manager only) |
| exact alarms | `AlarmScheduler` falls back to `setAndAllowWhileIdle`, history records `inexact` |
| full-screen intent | heads-up notification + content intent opens the ringing screen |
| ACTIVITY_RECOGNITION | no step sensors → shake fallback (30 shakes + 10 s long press) |
| battery optimization | app may be killed in background; boot reschedule still applies |

## HyperOS guidance

The screen explains that autostart and battery restrictions cannot be granted programmatically and
points at the MIUI security-center page. No "已开启" claim is shown for autostart because the
platform offers no query API.

## Missed sessions

`SessionRepository.startSession`/`resume` now set `deadlineAt = now + 30 min` for RINGING sessions.
`sweepExpired` therefore also turns a ringing session killed by process death / force stop into
`MISSED` after the window, not just snoozed ones. Boot and app start already call the sweep and write
`MISSED` history events.

Tests: `SessionRepositoryTest` covers expired vs alive snoozes, the ringing-window boundary, and
idempotent sweeps; `SettingsRepositoryTest` covers the onboarding flag.

## Alarm list

Each enabled alarm now shows the next ring (`今天 07:00` / `明天 07:30` / `周三 06:45`) computed by
`NextTriggerCalculator`, refreshed every 30 s while the list is visible.

## Tests

Total suite **122 tests** (data 98, app 24):

- new: onboarding flag persistence, ringing-window sweep, first-run screen composition;
- existing: scheduling, notifications, policy, migration, four toggle combinations.

## Known gaps

- No "permanently denied" detection: after the second denial Android stops showing the dialog; the
  row stays "未开启" and the user must use the "设置" shortcut.
- Autostart state is unknown by design; the row is an action, not a status.
- All permission pages and behaviour on HyperOS are **unverified** (no device).
- `USE_EXACT_ALARM` is still intentionally not requested.
