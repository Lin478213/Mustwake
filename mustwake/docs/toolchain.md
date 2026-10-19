# Toolchain (T0)

Installed on 2026-10-19, entirely inside `E:\MustWake\toolchain` (no system-wide install).

## Versions

| Component | Version | Path |
|---|---|---|
| Gradle | 9.8.1 | `E:\MustWake\toolchain\gradle-9.8.1` |
| JDK | Temurin 21.0.12.1+1 (LTS) | `E:\MustWake\toolchain\jdk-21` |
| Android SDK platform | android-37.0 | `E:\MustWake\toolchain\android-sdk\platforms\android-37.0` |
| Android build-tools | 37.0.0 | `E:\MustWake\toolchain\android-sdk\build-tools\37.0.0` |
| Android platform-tools | 37.0.1 | `E:\MustWake\toolchain\android-sdk\platform-tools` |

Project dependency versions are pinned in `gradle/libs.versions.toml`:

- AGP `9.3.2`
- Kotlin `2.4.20`
- Compose Multiplatform `1.12.0`
- Miuix `0.9.4` (`miuix-ui`, `miuix-preference`, `miuix-icons`, `miuix-nav`)
- Liquid Glass `io.github.kyant0:backdrop:2.0.1`

T1 data layer additions:

- Room `2.8.5` (`room-runtime`, `room-ktx`, `room-compiler` via KSP), applied to the `:data` module
- DataStore Preferences `1.2.1`
- KSP plugin `2.3.12` (decoupled versioning; runs against Kotlin 2.4.20 / AGP 9.3.2)
- Test only: Robolectric `4.16.1`, `androidx.test:core` `1.7.0`, `kotlinx-coroutines-test` `1.11.0`

## Build result

```
BUILD SUCCESSFUL
:androidApp:assembleDebug
APK: androidApp\build\outputs\apk\debug\androidApp-debug.apk (15,234,367 bytes)
```

APK metadata (`aapt2 dump badging`):

- package `com.mustwake.alarm`
- versionCode `1`, versionName `1.0`
- minSdk `26`, targetSdk `36`, compileSdk `37`

The shared UI module imports and compiles `MiuixTheme` + `lightColorScheme` + Miuix `Text`, so the Miuix path is proven. Backdrop resolves and is packaged; its first UI usage lands in T6.

## T1–T7 result

```
BUILD SUCCESSFUL
:androidApp:assembleDebug + test
122 JVM tests, 0 failures (Room / scheduler / notification / policy / migration / UI smoke)
APK: androidApp\build\outputs\apk\debug\androidApp-debug.apk (16,963,136 bytes)
```

UI is native Compose Miuix 0.9.4 behind `shared/.../ui/design/MwComponents.kt`; liquid glass uses
`io.github.kyant0:backdrop` 2.0.1. Flutter (`flutter_miuix`) was evaluated and rejected; rationale
in `docs/decisions.md`. No Flutter SDK is installed or required.

`AndroidManifest.xml` declares the alarm permissions (`SCHEDULE_EXACT_ALARM`,
`USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS`, FGS types, `ACTIVITY_RECOGNITION`, `WAKE_LOCK`,
`VIBRATE`, `RECEIVE_BOOT_COMPLETED`), registers `MustWakeApp`, `AlarmReceiver`, `BootReceiver`,
`RingingActivity` and `AlarmService` (`foregroundServiceType="health|specialUse"`).
Room schema is at version 2. Details: `docs/data-layer.md`, `docs/scheduling.md`,
`docs/ringing.md`, `docs/steps.md`, `docs/ui.md`, `docs/appearance.md`, `docs/permissions.md`.

`:data` uses `com.android.library` + KSP. AGP 9's built-in Kotlin support compiles Kotlin
without the `org.jetbrains.kotlin.android` plugin, and `ksp(...)` works as usual; no
`kspAndroidMain` workaround was needed. Details: `docs/data-layer.md`.

## Self-contained build

`gradle.properties` pins:

```
org.gradle.java.home=E:/MustWake/toolchain/jdk-21
```

`local.properties` pins:

```
sdk.dir=E:/MustWake/toolchain/android-sdk
```

`gradle/wrapper/gradle-wrapper.properties` points at the local Gradle zip:

```
distributionUrl=file\:///E:/MustWake/toolchain/downloads/gradle-9.8.1-bin.zip
```

The build was re-run with `JAVA_HOME`/`ANDROID_HOME` unset and still succeeded (`BUILD SUCCESSFUL in 2s`, all tasks up-to-date), proving the project is self-contained.

## Known warnings

- Compose Multiplatform marks `compose.runtime` / `foundation` / `material3` / `ui` / `components.resources` accessors deprecated ("specify dependency directly"). Non-blocking; can clean up in T1.
- Gradle reports deprecated features making the build incompatible with Gradle 10. Stay on Gradle 9.8.1 for now.
- `commonTest` is configured but not attached to a compilation. Add test source sets when T1 starts.

## Network note

Direct Maven Central/Google downloads were slow on this network. `settings.gradle.kts` lists Aliyun mirrors before Google/Maven Central; the full build completed after switching. If a mirror is unavailable, remove it or reorder.

## Not done yet

- No Android device was connected (`adb devices` empty), so the APK was not installed or launched.
- Next: T1 data layer (Room + DataStore), then T2 alarm scheduling/permissions.
