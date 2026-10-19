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
   │  └─ settings/ AppSettings, SettingsConstraints, SettingsRepository
   └─ test/kotlin/com/mustwake/alarm/data/
      ├─ alarm/AlarmRepositoryTest.kt
      ├─ db/MustWakeDatabaseTest.kt     # Robolectric, real in-memory SQLite
      ├─ db/FakeDaos.kt
      ├─ history/HistoryRepositoryTest.kt
      └─ settings/SettingsConstraintsTest.kt, SettingsRepositoryTest.kt
```

## Room

Database `mustwake.db`, version 1, `exportSchema = true`, no destructive migration.

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
- `AppContainer` — lazy singletons for database + three repositories; T2 can hold it from an
  `Application` subclass.

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
.\gradlew.bat :data:testDebugUnitTest     # 49 tests
.\gradlew.bat test                        # all modules
.\gradlew.bat :androidApp:assembleDebug
```

- 49 JVM tests: settings defaults/validation, repository validation and timestamps,
  history retention/limit, plus real Room CRUD, ordering, cascade delete and event round-trip
  via Robolectric (SDK 34 runtime).
- First Robolectric run downloads `android-all` (~5 min on this network); later runs are fast.

## Known gaps / follow-ups

- Schema version 1 has no migrations; when T2+ adds columns, add a `Migration` and a
  `room-testing` migration test (`MigrationTestHelper`).
- DAO tests run under Robolectric SDK 34 because Robolectric has no SDK 37 runtime yet.
  Behaviour is SQL-level and version independent; instrumented tests can be added at T8.
- `room-ktx` currently only supplies coroutine helpers; `HistoryRepository` does not use
  `withTransaction` yet (no multi-statement writes in T1).
