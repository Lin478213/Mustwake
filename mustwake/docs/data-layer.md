# Data layer (T1)

Status: **complete** (2026-10-19). Room + DataStore + repositories, no UI / Service / permission changes.

## Module layout

The data layer lives in a standalone Android-only module `:data` instead of `shared/androidMain`.

Reason: KSP inside the KMP `com.android.kotlin.multiplatform.library` module needs an extra
`kspAndroidMain` wiring; a plain `com.android.library` module keeps annotation processing on the
well-trodden path. `:androidApp` and (later) `:shared/androidMain` depend on `:data`, so
repository interfaces stay stable and the KMP/Compose setup is untouched.

```
data/
├─ build.gradle.kts                     # com.android.library + ksp, compileSdk 37
├─ schemas/                             # exported Room schema (committed)
│  └─ com.mustwake.alarm.data.db.MustWakeDatabase/1.json
└─ src/
   ├─ main/kotlin/com/mustwake/alarm/data/
   │  ├─ AppContainer.kt                # manual DI container
   │  ├─ alarm/  AlarmRepository.kt, AlarmConstraints.kt, AlarmRepeatDays.kt
   │  ├─ db/     AlarmEntity, AlarmEventEntity, AlarmEventType, AlarmDao,
   │  │          AlarmEventDao, MustWakeDatabase
   │  ├─ history/ HistoryRepository.kt, HistoryRetention.kt
   │  ├─ session/ SessionRepository.kt
   │  └─ settings/ AppSettings, SettingsConstraints, SettingsRepository
   └─ test/kotlin/com/mustwake/alarm/data/
      ├─ alarm/AlarmRepositoryTest.kt, NextTriggerCalculatorTest.kt
      ├─ db/MustWakeDatabaseTest.kt     # Robolectric, real in-memory SQLite
      ├─ db/Migration1To2Test.kt        # real SQLite migration test
      ├─ db/FakeDaos.kt
      ├─ history/HistoryRepositoryTest.kt
      ├─ session/SessionRepositoryTest.kt
      └─ settings/SettingsConstraintsTest.kt, SettingsRepositoryTest.kt
```

## Room

Database `mustwake.db`, version **2** (`MIGRATION_1_2` adds `alarm_sessions`),
`exportSchema = true`, no destructive migration. Exported schemas: `schemas/.../1.json`, `2.json`.

`alarms`

| column | type | notes |
|---|---|---|
| id | INTEGER PK autoincrement | |
| hour / minute | INTEGER | validated 0–23 / 0–59 on write |
| repeat_days_mask | INTEGER | bitmask, Monday = 1 … Sunday = 64, 0 = one-shot |
| label | TEXT | trimmed, max 40 chars |
| goal_steps | INTEGER | 10–500 |
| enabled | INTEGER | |
| created_at / updated_at | INTEGER | epoch millis, set by repository |

`alarm_events`

`id`, `alarm_id` (FK → `alarms.id`, `ON DELETE CASCADE`), `type` (enum name, TEXT),
`steps`, `timestamp`, `reason`. Indices on `alarm_id` and `timestamp`.

`AlarmEventType`: `SCHEDULED`, `RINGING`, `SNOOZED`, `COMPLETED`, `EMERGENCY`, `DEGRADED`,
`MISSED`, `CANCELLED`. `SNOOZED` is the one addition to the handoff list; T4 needs it to
reconstruct a snoozed session after process death.

`alarm_sessions` (v2, T2)

`id`, `alarm_id` (FK → `alarms.id`, cascade), `state` (`AlarmSessionState` enum as TEXT),
`started_at`, `updated_at`, `steps`, `snooze_count`, `deadline_at` (nullable). Indices on
`alarm_id` and `state`. Active sessions (`RINGING`/`SNOOZED`) with a deadline in the past are
swept to `MISSED`; see `docs/scheduling.md`.

## Settings (DataStore)

`Context.mustWakeSettingsDataStore` → `DataStore<Preferences>` named `mustwake_settings`.
`SettingsRepository` exposes `settings: Flow<AppSettings>` and suspend setters.

| field | default | valid range |
|---|---|---|
| floatingBottomBarEnabled | false | — |
| liquidGlassEnabled | false | — |
| defaultGoalSteps | 50 | 10–500 |
| snoozeMinutes | 5 | 1–15 |
| maxSnoozes | 3 | 0–5 |
| shakeCount | 30 | 10–100 |

Validation policy:

- **Write**: `require(...)` — invalid values throw `IllegalArgumentException` and are never stored.
- **Read**: `AppSettings.sanitized()` — out-of-range persisted values are replaced by the default,
  so a corrupted store can never feed invalid values into scheduling or step logic.

## Repositories

- `AlarmRepository` — `observeAlarms`, `observeEnabledAlarms`, `observeAlarm`, `getEnabledAlarms`,
  `save` (insert when `id == 0`, otherwise update and preserve `createdAt`), `setEnabled`, `delete`.
  All writes are normalized/validated; unknown ids are rejected.
- `HistoryRepository` — `record`, `observeRecent`, `observeForAlarm`, `recent`, `forAlarm`,
  `trimToLimit` (newest 200), `purgeExpired` (90 days), `count`.
- `SessionRepository` — `startSession`, `snooze`, `resume`, `finish`, `sweepExpired`, `activeSession`.
- `AppContainer` — lazy singletons for database + four repositories; T2 wires it into
  `MustWakeApp`.

## Dependencies added

| dependency | version |
|---|---|
| androidx.room:room-runtime / room-ktx / room-compiler | 2.8.5 |
| androidx.datastore:datastore-preferences | 1.2.1 |
| com.google.devtools.ksp (plugin) | 2.3.12 |
| org.robolectric:robolectric (test only) | 4.16.1 |
| androidx.test:core (test only) | 1.7.0 |
| kotlinx-coroutines-test (test only) | 1.11.0 |

KSP 2.3.12 runs against Kotlin 2.4.20 / AGP 9.3.2 (`com.android.library` uses AGP's built-in
Kotlin support); no `kspAndroidMain` workaround was needed.

## Verification

```powershell
cd E:\MustWake\mustwake
.\gradlew.bat :data:testDebugUnitTest     # 73 tests
.\gradlew.bat test                        # all modules (82 tests)
.\gradlew.bat :androidApp:assembleDebug
```

- 73 JVM tests in `:data`: settings defaults/validation, repository validation and timestamps,
  history retention/limit, trigger calculation, session state machine and sweep, plus real Room
  CRUD, ordering, cascade delete and event round-trip via Robolectric (SDK 34 runtime), and the
  v1→v2 migration on real SQLite.
- `:androidApp` adds 9 Robolectric tests for `AlarmScheduler` (registration, replacement, cancel,
  snooze, history).
- First Robolectric run downloads `android-all` (~5 min on this network); later runs are fast.

## Known gaps / follow-ups

- Schema version 2 has one migration (`MIGRATION_1_2`), covered by `Migration1To2Test`. Every
  further schema change needs a new `Migration`, a new exported schema and a matching test.
- DAO tests run under Robolectric SDK 34 because Robolectric has no SDK 37 runtime yet.
  Behaviour is SQL-level and version independent; instrumented tests can be added at T8.
- `room-ktx` currently only supplies coroutine helpers; multi-statement writes (e.g. finish
  session + append event) are not wrapped in `withTransaction` yet — T4 should add that.
