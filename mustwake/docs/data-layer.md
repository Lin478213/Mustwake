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
   │  ├─ backup/ BackupModels, BackupCodec, BackupRepository   # M1-A1 JSON backup
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
| floatingBottomBarEnabled | false | **deprecated 2026-10-20**（并入 liquidGlassEnabled 单开关；字段保留做备份 schema v1 往返，UI 不再读取） |
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
- `BackupRepository` (M1-A1) — `exportJson`, `importJson(json, MERGE|REPLACE)` returning an
  `ImportReport` (imported / skippedInvalid / skippedDuplicates + per-item details).
- `AppContainer` — lazy singletons for database + five repositories; T2 wires it into
  `MustWakeApp`.

## Backup / restore JSON (M1-A1)

`backup/BackupCodec` serialises `BackupData` with kotlinx.serialization (plugin + runtime
`kotlinx-serialization-json` 1.11.0; the compiler plugin mounts fine on AGP 9's built-in
Kotlin in `:data`). Document shape (schema version **1**):

```json
{ "schemaVersion": 1, "exportedAt": 0,
  "alarms": [{ "id": 1, "hour": 7, "minute": 0, "repeat_days_mask": 127, "label": "",
               "goal_steps": 50, "enabled": true, "created_at": 0, "updated_at": 0 }],
  "settings": { "floating_bottom_bar_enabled": false, "liquid_glass_enabled": false,
                "default_goal_steps": 50, "snooze_minutes": 5, "max_snoozes": 3,
                "shake_count": 30 } }
```

- Session/history rows are never exported. `onboarding_completed` is excluded and left
  untouched on import: the permission guide is per-device and re-runs after a restore.
- Malformed JSON or an unknown `schemaVersion` (a missing one is rejected too) decode to a
  failed `Result` / failure `ImportReport`; nothing is thrown and nothing is written. The
  same holds for a settings write failure after the alarm transaction committed: it is
  reported in `ImportReport.details` (`alarms imported, settings failed: …`) and the
  imported alarms stay.
- Import writes alarms inside a single Room `withTransaction` (via
  `RoomBackupTransactionRunner`): any failure rolls the whole import back, so REPLACE never
  ends up with a half-cleared table. Settings go in afterwards as one atomic
  `SettingsRepository.importSettings` DataStore edit.
- MERGE dedup key is `(hour, minute, repeatDaysMask, label)`: the existing alarm is kept
  (goalSteps/enabled untouched) and the backup row counts as `skippedDuplicates`. REPLACE
  clears the table first and does no dedup.
- Rows violating `AlarmConstraints` are skipped and counted (including negative
  `created_at`/`updated_at`); out-of-range settings values fall back to defaults
  (`AppSettings.sanitized()` semantics). Timestamps and free ids are restored verbatim, so
  export → wipe → import round-trips exactly. Labels are trimmed for storage and for the
  dedup key.
- Uri file IO (SAF create/open) lives in `:shared` `SettingsScreen.kt`, not in `:data`;
  import files larger than 2 MB are refused.

## Dependencies added

| dependency | version |
|---|---|
| androidx.room:room-runtime / room-ktx / room-compiler | 2.8.5 |
| androidx.datastore:datastore-preferences | 1.2.1 |
| org.jetbrains.kotlin.plugin.serialization (plugin) | 2.4.20 |
| org.jetbrains.kotlinx:kotlinx-serialization-json | 1.11.0 |
| com.google.devtools.ksp (plugin) | 2.3.12 |
| org.robolectric:robolectric (test only) | 4.16.1 |
| androidx.test:core (test only) | 1.7.0 |
| kotlinx-coroutines-test (test only) | 1.11.0 |

KSP 2.3.12 runs against Kotlin 2.4.20 / AGP 9.3.2 (`com.android.library` uses AGP's built-in
Kotlin support); no `kspAndroidMain` workaround was needed.

## Verification

```bash
cd /home/lin/Java/MustWake/mustwake
./gradlew :data:testDebugUnitTest     # 119 tests
./gradlew test                        # all modules (156 tests; incl. 14 Roborazzi screenshot cases)
./gradlew :androidApp:assembleDebug
```

- 119 JVM tests in `:data`: settings defaults/validation, repository validation and timestamps,
  history retention/limit, trigger calculation, session state machine and sweep, plus real Room
  CRUD, ordering, cascade delete and event round-trip via Robolectric (SDK 34 runtime), and the
  v1→v2 migration on real SQLite.
- `:androidApp` adds 24 tests across `AlarmSchedulerTest`, `AlarmNotificationsTest` and
  `MainActivityTest` (registration, replacement, cancel, snooze, history, notifications).
- First Robolectric run downloads `android-all` (~5 min on this network); later runs are fast.

## Known gaps / follow-ups

- Schema version 2 has one migration (`MIGRATION_1_2`), covered by `Migration1To2Test`. Every
  further schema change needs a new `Migration`, a new exported schema and a matching test.
- DAO tests run under Robolectric SDK 34 because Robolectric has no SDK 37 runtime yet.
  Behaviour is SQL-level and version independent; instrumented tests can be added at T8.
- `room-ktx` currently only supplies coroutine helpers; multi-statement writes (e.g. finish
  session + append event) are not wrapped in `withTransaction` yet — T4 should add that.
  (Backup import is the exception: it runs in one `withTransaction`, see above.)
- M1-A1 adds 21 backup tests (8 codec + 13 repository, incl. wipe→restore round-trip,
  mid-import rollback and the settings-IO-failure path), bringing `:data` to 119 and the
  project to 143 JVM tests.
