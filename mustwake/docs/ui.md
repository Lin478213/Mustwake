# UI layer (T5)

Status: **complete** (2026-10-19). Toggles' visual effects are T6; permission onboarding is T7.

Technology: native Compose + Miuix 0.9.4, isolated behind a design-system adapter. The Flutter
alternative was evaluated and rejected — see `decisions.md`.

## Structure

```
shared/src/androidMain/kotlin/com/mustwake/alarm/
├─ App.kt                        # nav shell: 3 tabs + editor route
└─ ui/
   ├─ AlarmUiActions.kt          # schedule/cancel callbacks implemented by :androidApp
   ├─ TimeText.kt                # time + repeat-day formatting
   ├─ design/MwComponents.kt     # the ONLY file importing Miuix
   ├─ alarms/AlarmListScreen.kt
   ├─ alarms/AlarmEditScreen.kt
   ├─ history/HistoryScreen.kt
   ├─ settings/SettingsScreen.kt
   └─ ringing/RingingScreen.kt
```

`shared` (androidMain) now depends on `:data`; the UI reads repositories directly and calls
`AlarmUiActions` for scheduling (the app module owns `AlarmScheduler`, receivers and show-intent).

## Design system adapter

`MwComponents.kt` exposes semantic wrappers: `MwTheme`, `MwScaffold`, `MwTopBar`, `MwCard`,
`MwText` + `MwTypo`, `MwPrimaryButton`, `MwSecondaryButton`, `MwChip`, `MwSwitch`, `MwSwitchRow`,
`MwTextField`, `MwNumberPicker`, `MwProgressBar`, `MwFab`, `MwIconAction`, `MwBottomBar`.

Rules:

- screens may import Compose foundation/runtime, never Miuix;
- new Miuix components are added here first, with a semantic name;
- theme = system light/dark for now; Monet/dynamic colors land with T6.

## Screens

| screen | behaviour |
|---|---|
| 闹钟列表 | Room `observeAlarms`; time/label/repeat/goal; enable switch reschedules or cancels; tap opens editor; FAB creates |
| 新建/编辑 | hour/minute `NumberPicker`; label `TextField` (≤40); goal 10–500; 7 repeat chips + 每天; save → repository + schedule; delete for existing |
| 历史 | newest 100 events; alarm label + local time + type + steps + reason; empty state |
| 设置 | floating bar / liquid glass switches (persisted; visual effect T6); goal/snooze/max-snooze/shake steppers bound to DataStore with validation |
| 响铃 | Miuix full screen: time, label, step or shake progress, snooze (disabled at limit), 10 s hold-to-stop |

Navigation is a simple `AppTab` + editor state (no navigation library), per plan's
"MiuixNav 或简单状态导航".

## Ringing screen details

- `RingingActivity` is now a `ComponentActivity` with `setContent { MwTheme { RingingScreen(...) } }`;
  the programmatic View layout was removed.
- State comes from service broadcasts (`ACTION_RINGING_UPDATE` / `ACTION_RINGING_STOPPED`); on start
  the activity asks the service for current state, so steps never reset to 0. The status-bar entry
  (`AlarmClockInfo.showIntent`) opens this screen directly; label/time are loaded from the database
  by alarm id.
- Hold-to-stop uses `detectTapGestures` + `delay(10_000)`; release cancels the job and shows a hint.
  Snooze is a normal button, disabled when `StopPolicy.canSnooze` is false.

## Tests

`androidApp` Robolectric smoke tests (2): `MainActivity` composes the list/empty state, and
`RingingActivity` composes with alarm extras.

Total suite: **117 tests** (data 97, app 20). Screenshot/golden tests are not set up yet; they are
planned with T6 visual work (needs a screenshot library such as Roborazzi).

## Deferred / known gaps

- 悬浮底栏、液态玻璃：已在 T6 实现，见 `docs/appearance.md`。
- 首次启动权限引导（通知、精确闹钟、全屏通知、ACTIVITY_RECOGNITION）：T7。
- 编辑页未暴露"启用"开关；新建默认启用，切换在列表页完成。
- 无 UI 截图基线；Material 风格兜底不存在，Miuix 是唯一实现。
- Robolectric Compose 只验证"能组合"，不验证像素与交互手势。
