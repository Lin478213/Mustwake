# MustWake 交接文档

> 交接时间：2026-10-19 14:03（UTC+8），T1–T4 完成后更新于 14:49
> 交接状态：**T4 完成，T5 未开始**
> 工作区：`E:\MustWake`（已是 git 仓库）
> 项目：`E:\MustWake\mustwake`
> 工具链：`E:\MustWake\toolchain`

---

## 1. 一页结论

- 目标：Android 步数闹钟。到点响铃后，必须走到目标步数才能停止；MIUIX 风格；设置里两个独立开关控制悬浮底栏与液态玻璃。
- 当前完成了 **T0 工具链**、**T1 数据层**、**T2 调度与权限**、**T3 响铃服务**、**T4 步数与停止策略**：Room v2 + DataStore + 调度/Boot 恢复 + 前台服务 + 计步基线/降级 + `StopPolicy` + snooze + 紧急停止 + 摇晃降级，115 个 JVM 测试全过。
- 下一步是 **T5 MIUIX 界面骨架**：列表/编辑/响铃/设置/历史/引导页，响铃页替换掉当前程序化布局。
- 当前没有连接 Android 设备，APK 尚未安装/运行；真机验收是 T8 的事。
- 必读顺序：本文 → `mustwake/docs/toolchain.md` → `mustwake/docs/data-layer.md` → `mustwake/docs/scheduling.md` → `mustwake/docs/ringing.md` → `mustwake/docs/steps.md` → `.omc/specs/deep-interview-step-alarm.md` → `.omc/plans/step-alarm.md` → `mustwake/README.md`。

---

## 2. 项目目标与范围

### 2.1 产品目标

用户设一个闹钟（时间、重复、标签、目标步数）。到点后：

1. `AlarmManager.setAlarmClock()` 唤醒设备；
2. 全屏响铃页 + 前台服务播放铃声/振动；
3. `TYPE_STEP_COUNTER` 以响铃瞬间为基线计步；缺失时降级 `TYPE_STEP_DETECTOR`；
4. 达到目标步数才停止；
5. 支持 snooze（默认累计步数、最多 3 次）、长按 10 秒紧急停止、传感器不可用时摇晃降级；
6. 设置中两个独立开关：悬浮底栏、液态玻璃；默认关闭；
7. 完整版本地多闹钟、历史、权限引导、HyperOS 白名单引导。

### 2.2 已确定、不要随意改的决策

| 决策 | 值 | 说明 |
|---|---|---|
| 平台 | Android 单平台 | 小米/红米 Android 14+ 为主 |
| minSdk | 26 | |
| compileSdk | **37** | Miuix 0.9.4 / backdrop 2.0.1 的 AAR 要求 `minCompileSdk=37` |
| targetSdk | 36 | |
| UI | Miuix 0.9.4 + Compose Multiplatform 1.12.0 | 已验证编译 |
| 液态玻璃 | `io.github.kyant0:backdrop:2.0.1` | 无高层组件，需要自绘 |
| 工程形态 | `shared` KMP UI + `androidApp` Android 壳 | 与 backdrop 官方工程一致 |
| JDK | Temurin 21（项目内固定） | 不要用系统 JDK 23 替换 |
| 包名 | `com.mustwake.alarm` | 工作假设；改名需用户确认 |
| 应用名 | MustWake | 工作名 |
| 默认步数 | 50（范围 10–500） | |
| snooze | 5 分钟，最多 3 次，步数累计 | |
| 紧急停止 | 长按 10 秒，记“未完成” | |
| 摇晃降级 | 30 次 + 长按确认 | |
| 开关默认 | 两个都关闭 | |
| 玻璃范围 | 底栏 + 卡片 + 对话框 | 设置/历史页后置 |
| root | 可选辅助（pm grant/appops/deviceidle），**不做 daemon** | T9 |

spec 与 plan 是权威来源；若实现需要偏离，先改文档并说明原因，不要静默改。

---

## 3. 工具链（已安装，已验证）

全部在 `E:\MustWake\toolchain`，没有系统级安装：

| 组件 | 版本 | 路径 |
|---|---|---|
| Gradle | 9.8.1 | `E:\MustWake\toolchain\gradle-9.8.1` |
| JDK | Temurin 21.0.12.1+1 | `E:\MustWake\toolchain\jdk-21` |
| Android Platform | android-37.0 | `E:\MustWake\toolchain\android-sdk\platforms\android-37.0` |
| Build-Tools | 37.0.0 | `E:\MustWake\toolchain\android-sdk\build-tools\37.0.0` |
| Platform-Tools | 37.0.1 | `E:\MustWake\toolchain\android-sdk\platform-tools` |

工程依赖：

```toml
agp = "9.3.2"
kotlin = "2.4.20"
compose = "1.12.0"
miuix = "0.9.4"
backdrop = "2.0.1"
```

### 3.1 构建命令（不需要设环境变量）

```powershell
cd E:\MustWake\mustwake
.\gradlew.bat :androidApp:assembleDebug
```

自包含配置：

- `gradle.properties`：`org.gradle.java.home=E:/MustWake/toolchain/jdk-21`
- `local.properties`：`sdk.dir=E:/MustWake/toolchain/android-sdk`
- `gradle/wrapper/gradle-wrapper.properties`：指向 `file:///E:/MustWake/toolchain/downloads/gradle-9.8.1-bin.zip`

如果构建卡住/缓存异常：

```powershell
.\gradlew.bat --stop
.\gradlew.bat :androidApp:assembleDebug
```

不要为了“让它跑起来”改成 compileSdk 36 或移除 Miuix；那会违反 spec 且 UI 验收会失败。T0 硬门禁就是：37 装不上就停下来问用户。

### 3.2 网络

`settings.gradle.kts` 里 Aliyun 镜像优先，然后 Google/Maven Central。直接 Maven 下载很慢；如果 Aliyun 失效，调整仓库顺序或临时移除镜像，不要把整个工程改成离线。

### 3.3 已装但未验证的东西

- Miuix 已验证：`App.kt` 使用 `MiuixTheme` + `lightColorScheme` + Miuix `Text`，构建成功。
- backdrop 已解析、打包，但还没有 UI 用法；T6 才接。
- 没有设备连接，APK 未安装。

---

## 4. 代码现状

### 4.1 文件树（忽略 build/.gradle）

```
E:\MustWake\mustwake\
├─ README.md
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
├─ local.properties
├─ gradlew / gradlew.bat
├─ gradle\
│  ├─ libs.versions.toml
│  └─ wrapper\...
├─ docs\
│  ├─ toolchain.md
│  ├─ data-layer.md
│  ├─ scheduling.md
│  ├─ ringing.md
│  └─ steps.md
├─ data\
│  ├─ build.gradle.kts
│  ├─ schemas\com.mustwake.alarm.data.db.MustWakeDatabase\{1,2}.json
│  └─ src\
│     ├─ main\kotlin\com\mustwake\alarm\data\
│     │  ├─ AppContainer.kt
│     │  ├─ alarm\ (AlarmRepository, AlarmConstraints, AlarmRepeatDays, NextTriggerCalculator)
│     │  ├─ db\ (entities, DAOs, MustWakeDatabase + MIGRATION_1_2, enums)
│     │  ├─ history\ (HistoryRepository, HistoryRetention)
│     │  ├─ sensor\ (StepBaselineTracker, ShakeCounter)
│     │  ├─ session\ (SessionRepository)
│     │  ├─ settings\ (AppSettings, SettingsConstraints, SettingsRepository)
│     │  └─ stop\ (StopPolicy)
│     └─ test\kotlin\com\mustwake\alarm\data\ (97 tests, Robolectric DAO + migration)
├─ shared\
│  ├─ build.gradle.kts
│  └─ src\
│     ├─ commonMain\kotlin\com\mustwake\alarm\App.kt
│     └─ androidMain\AndroidManifest.xml
└─ androidApp\
   ├─ build.gradle.kts
   └─ src\
      ├─ main\
      │  ├─ AndroidManifest.xml
      │  ├─ res\values\strings.xml
      │  └─ kotlin\com\mustwake\alarm\
      │     ├─ MainActivity.kt, MustWakeApp.kt, RingingActivity.kt
      │     ├─ alarm\ (AlarmScheduler, AlarmMaintenance)
      │     ├─ notification\ (AlarmNotifications)
      │     ├─ receiver\ (AlarmReceiver, BootReceiver)
      │     ├─ sensor\ (StepSensorRepository, ShakeDetector)
      │     └─ service\ (AlarmService)
      └─ test\kotlin\com\mustwake\alarm\ (18 tests: scheduler + notifications)
```

### 4.2 当前代码

- `App.kt`：Miuix 主题 + 居中文本 “MustWake”。仅骨架。
- `MainActivity.kt`：`ComponentActivity` + `setContent { App() }`；`singleTop`，showIntent 落到这里（T5 再接闹钟列表）。
- `RingingActivity`：全屏响铃页（锁屏上方、亮屏），实时步数/剩余步数（降级时显示摇晃次数）、snooze（次数用完禁用）、停止按钮需**长按 10 秒**（短按提示）；程序化布局，T5 换 MIUIX。
- `AlarmService`：5 秒内前台；health/specialUse 逐级降级；音频/振动/唤醒锁；计步（STEP_COUNTER 基线、STEP_DETECTOR 降级、无传感器转摇晃）；`StopPolicy` 判定 COMPLETED/EMERGENCY/DEGRADED；snooze 持久化 + 独立 requestCode；状态广播。
- `StepSensorRepository` / `ShakeDetector`：传感器注册与事件转发；纯逻辑 `StepBaselineTracker` / `ShakeCounter` 在 `:data` 并已单测。
- `AndroidManifest.xml`：launcher + `RingingActivity` + `AlarmService`（`foregroundServiceType="health|specialUse"` + specialUse subtype）+ 两个 receiver + 10 个权限。
- `MustWakeApp` 持有 `AppContainer`，启动时异步 sweep 过期会话。
- `AlarmScheduler`：`setAlarmClock`，无精确闹钟权限或 `SecurityException` 时降级 `setAndAllowWhileIdle`；每次注册写 `SCHEDULED` 历史；snooze 用独立 requestCode（`0x40000000`），不会覆盖重复闹钟的下一次。
- `AlarmNotifications`：`alarm_ringing` 高优先级静音 channel；FSI 仅在 `canUseFullScreenIntent()` 时挂；显示实时步数/摇晃进度，snooze 仅剩次数时显示 action，**没有直接停止 action**。
- 数据层：Room `mustwake.db` v2（`alarms` / `alarm_events` / `alarm_sessions`），DataStore 设置，四个 Repository，纯逻辑 `StopPolicy` / `StepBaselineTracker` / `ShakeCounter`，测试在 `data/src/test`（详见 `docs/steps.md`）。

### 4.3 构建产物

- APK：`E:\MustWake\mustwake\androidApp\build\outputs\apk\debug\androidApp-debug.apk`
- 大小：16,962,959 bytes（T4 后；T3 16,971,605；T2 16,480,973；T1 16,429,715；T0 15,234,367）
- SHA256：`5afe4225a2ea018bafdf71feea8e98e77ec55dc6e9077f2503f11b3caea8d944`
- 包名：`com.mustwake.alarm`
- minSdk 26 / targetSdk 36 / compileSdk 37
- 构建日志：`build-selfcontained-log.txt`、`build-miuix-log.txt`（T0 产物，T1–T3 日志未单独落盘）

### 4.4 Git

`E:\MustWake` 是 git 仓库（2026-10-19 初始化）。提交策略：**每个功能/任务一个提交**。

- 已跟踪：`HANDOFF.md`、`mustwake/`（含 `data/`、`schemas/`、`docs/`）、`.omc/spec`+`plan`、`.gitignore`。
- 已忽略：`toolchain/`、`oh-my-claudecode*/`、`**/build/`、`**/.gradle/`、`**/.kotlin/`、`mustwake/local.properties`。
- 不要提交 `toolchain/` 或上游参考仓库；它们体积大且不是本工程代码。

---

## 5. T1 数据层（已完成）

**状态：完成。** 实现说明与文件清单见 `mustwake/docs/data-layer.md`，这里只记录结果与偏离。

### 5.0 实现结果

- 新增 Android-only 模块 `:data`（`com.android.library` + KSP），没有把 Room 塞进 KMP `shared`：AGP 9 内置 Kotlin 编译，`ksp(...)` 直接可用，不需要 `kspAndroidMain`。
- `MustWakeDatabase` v1：`alarms`、`alarm_events`（FK `ON DELETE CASCADE`，索引 `alarm_id`/`timestamp`），schema 导出到 `data/schemas/`。
- `AlarmEventType` 在交接清单外增加了 `SNOOZED`：T4 需要在进程死亡后恢复 snooze 会话。
- `SettingsRepository`：写入 `require` 拒绝非法值，读取 `sanitized()` 把越界值回退默认。
- `AppContainer`：手写懒加载容器，T2 可直接挂到 Application。
- 版本：Room 2.8.5 / KSP 2.3.12 / DataStore 1.2.1 / Robolectric 4.16.1（仅测试）。

### 5.1 目标

把闹钟、历史、设置持久化，给后续 T2–T7 提供稳定接口。**本任务不写 UI、不写 Service、不写权限。**

### 5.2 需要实现

**Room**

- `AlarmEntity`：
  - `id: Long`（自增）
  - `hour: Int`、`minute: Int`
  - `repeatDaysMask: Int`（bitmask，周一=1 … 周日=64）
  - `label: String`
  - `goalSteps: Int`（10–500）
  - `enabled: Boolean`
  - 可选：`createdAt`、`updatedAt`
- `AlarmEventEntity`：
  - `id`、`alarmId`、`type`（`scheduled|ringing|completed|emergency|degraded|missed|cancelled`）
  - `steps: Int`、`timestamp: Long`、`reason: String?`
- DAO：`AlarmDao`（CRUD、按 enabled/时间查询）、`AlarmEventDao`（插入、最近 N 条、按 alarmId 查）。
- Database：`MustWakeDatabase`，version 1，导出 schema（`room.schemaLocation`）。
- Repository：`AlarmRepository`、`HistoryRepository`，暴露 `Flow` / suspend。

**DataStore**

- `SettingsRepository`，字段至少：
  - `floatingBottomBarEnabled: Boolean = false`
  - `liquidGlassEnabled: Boolean = false`
  - `defaultGoalSteps: Int = 50`
  - `snoozeMinutes: Int = 5`
  - `maxSnoozes: Int = 3`
  - `shakeCount: Int = 30`
- 读写用 `DataStore<Preferences>`，不要用 SharedPreferences。

**默认值与约束**

- goal 夹在 10–500；snooze 1–15 分钟；maxSnoozes 0–5；shake 10–100。
- 所有写入做校验，非法值回退默认或拒绝，不要静默存坏值。

**测试**

- JVM 单测：默认值、校验、Repository 逻辑（可用 fake DAO）。
- DAO/Database 测试按需用 Robolectric 或 instrumented test；如果引入 Robolectric，记录版本。
- `commonTest` 目前是空的且未挂到 compilation；T1 要把它接上，或明确改成 Android 测试源集。

### 5.3 依赖选择

需要新增：

- `androidx.room:room-runtime`、`room-ktx`、`room-compiler`（KSP）
- `androidx.datastore:datastore-preferences`
- KSP Gradle 插件，版本前缀必须匹配 Kotlin `2.4.20`
- 测试：JUnit、kotlinx-coroutines-test；DAO 测试按需 `room-testing`/Robolectric

**风险**：Kotlin 2.4.20 + AGP 9.3.2 很新，KSP/Room 版本可能不匹配。要求：

1. 先查 Maven 上可用的 KSP 版本（前缀 `2.4.20-`）。
2. 选一个稳定 Room 版本，确认 AAR `minCompileSdk ≤ 37`。
3. 如果找不到兼容组合，**停下来报告**，不要降级 Kotlin/compileSdk 或移除 Miuix。

KSP 在 KMP `shared` 模块里可能需要 `kspAndroidMain` 配置；如果配置成本过高，可以把 Room 放到 `androidApp` 或新建一个 Android-only `data` 模块，但对外接口（Repository）保持稳定。不要为了 KSP 把整个工程改成非 KMP 或降级 Compose/Miuix。

### 5.4 T1 完成定义（DoD）

- [x] `.\gradlew.bat :androidApp:assembleDebug` 成功（BUILD SUCCESSFUL in 40s，95 tasks）。
- [x] `.\gradlew.bat test` 成功（49 tests, 0 failures；含 Robolectric DAO 测试）。
- [x] Room schema 导出文件存在（`data/schemas/com.mustwake.alarm.data.db.MustWakeDatabase/1.json`）。
- [x] 没有 UI/Service/权限改动；仅 `androidApp` 增加 `implementation(project(":data"))`。
- [x] T1 状态、依赖版本、测试命令已写入 `docs/data-layer.md`、`docs/toolchain.md`、`README.md`、本文。
- [x] 报告：见本次汇报（改动文件 / 验证命令 / 风险）。

测试命令：

```powershell
cd E:\MustWake\mustwake
.\gradlew.bat test
.\gradlew.bat :data:testDebugUnitTest
```

注意：首次跑 Robolectric 需下载 android-all，约 5 分钟；之后走缓存。

---

## 5A. T2 调度与权限（已完成）

**状态：完成。** 细节见 `mustwake/docs/scheduling.md`。

### 实现结果

- `AlarmScheduler`：`setAlarmClock` + `AlarmClockInfo(triggerAt, showIntent)`；无精确闹钟权限或 `SecurityException` 时降级 `setAndAllowWhileIdle` 并在返回值标 `exact=false`；requestCode = `alarmId & 0x7FFFFFFF`，`FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`。
- `scheduleSnooze(alarmId, triggerAt, steps, snoozeCount)`：同一 requestCode 覆盖注册，steps/snooze 写进 `SCHEDULED` 历史 reason。
- `NextTriggerCalculator`（`:data` 纯函数）：一次性闹钟取今天/明天；重复闹钟取下一个匹配星期，严格晚于 now。9 个单测覆盖边界。
- `AlarmReceiver`：校验 action/alarmId、日志、重复闹钟立即续约下一次；一次性闹钟触发后自动 `setEnabled(false)`，避免重启复活。
- `BootReceiver`：BOOT_COMPLETED / TIME_SET / TIMEZONE_CHANGED / MY_PACKAGE_REPLACED → sweep 过期会话（写 `MISSED` 历史）+ 重注册所有 enabled 闹钟。
- `MustWakeApp`：持有 `AppContainer`，启动时异步 sweep，避免历史永远停在“响铃中”。
- **数据库 v2**：新增 `alarm_sessions`（state/steps/snooze_count/deadline_at），`MIGRATION_1_2` 非破坏性迁移，schema 2 已导出；`SessionRepository` 提供 start/snooze/resume/finish/sweep。
- manifest：10 个权限 + `AlarmReceiver`(exported=false) + `BootReceiver`(exported=true)，`MainActivity` 改 `singleTop`。

### 与 plan 的偏离（已记录）

- plan 写的是 T2 的 `AlarmReceiver` 直接 `startForegroundService`。本任务只做调度与投递，**不建空壳 Service**：服务、全屏通知、音频在 T3 一起做，T3 在 `AlarmReceiver` 里接这一行。
- `USE_EXACT_ALARM` 不申请（Play 政策），统一走 `SCHEDULE_EXACT_ALARM` + 降级。

### DoD

- [x] `.\gradlew.bat :androidApp:assembleDebug` → BUILD SUCCESSFUL
- [x] `.\gradlew.bat test` → 82 tests, 0 failures（data 73 + app 9）
- [x] 迁移：`data/schemas/.../2.json` 存在，`Migration1To2Test` 通过（真实 SQLite）
- [x] manifest 合并后 10 个权限、两个 receiver、Application 类均已生效（检查 merged manifest）
- [ ] `dumpsys alarm` / 2 分钟真机闹钟 / 重启重注册：**未验证（无设备）**

---

## 5B. T3 响铃服务（已完成）

**状态：完成。** 细节见 `mustwake/docs/ringing.md`。

### 实现结果

- `AlarmService`：先 `promoteToForeground`（占位通知，5 秒内），再异步加载闹钟；运行时类型 `health`（API 30+ 且 `ACTIVITY_RECOGNITION` 已授予）→ `specialUse`（API 34+）→ 0，逐个尝试并捕获所有 `Throwable`，不会因类型/权限失败而不响。
- 音频：`MediaPlayer` 循环默认闹钟/铃声 URI，`USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION`；失败只记日志，不阻塞响铃状态。
- 振动：`[0, 800, 600]` 循环；唤醒锁：`PARTIAL_WAKE_LOCK` 10 分钟超时；`onDestroy` 全部释放。
- 会话：复用活动会话或新建 `RINGING` 并写 `RINGING` 历史；手动停止 finish 为 `CANCELLED` 并写事件；系统杀进程则留待 sweep 记 `MISSED`。
- `AlarmNotifications`：`alarm_ringing` 高优先级、静音、不振动、锁屏公开；FSI 仅在 `canUseFullScreenIntent()` 时挂，否则 heads-up + contentIntent；含“停止”action。
- `RingingActivity`：锁屏上方全屏、亮屏，显示时间/标签/目标 + 停止按钮；服务广播 `ACTION_RINGING_STOPPED` 时自动关闭。
- `AlarmReceiver` 现在同步 `startForegroundService`，再异步续约重复闹钟。

### 与 plan 的偏离（已记录）

- T3 的停止按钮/通知 action **不做步数校验**，直接 `CANCELLED`；T4 接入 `StopPolicy` 后改为步数/紧急/降级三条路径。这样 T3 可独立验证“响铃—停止—清理”。
- 响铃页用程序化 View，不是 Miuix；T5 重写。

### DoD

- [x] `.\gradlew.bat :androidApp:assembleDebug` → BUILD SUCCESSFUL
- [x] `.\gradlew.bat test` → 86 tests, 0 failures（data 73 + app 13）
- [x] FGS 类型运行时选择 + 逐级降级（代码级；真机未验证）
- [x] 通知 channel/FSI/action 由 Robolectric 测试覆盖
- [ ] 锁屏响铃、全屏页、撤销 FSI 后的 heads-up、音频/振动实际表现：**未验证（无设备）**

---

## 5C. T4 步数与停止策略（已完成）

**状态：完成。** 细节见 `mustwake/docs/steps.md`。

### 实现结果

- 计步：`TYPE_STEP_COUNTER` 首读为基线（`StepBaselineTracker` 纯逻辑，回滚不为负）；无 COUNTER 时用 `TYPE_STEP_DETECTOR`；两者都注册失败（无硬件 / `ACTIVITY_RECOGNITION` 被拒）→ 进入降级摇晃模式。
- 摇晃：`ShakeCounter` 阈值 12 m/s²、150ms 防抖、到 `settings.shakeCount`（默认 30）封顶；无加速度计时退回普通紧急停止，避免“永远停不下来”。
- `StopPolicy`（纯 Kotlin）：步数达标优先判 COMPLETED；其次长按——普通模式 EMERGENCY，降级模式且摇晃达标 DEGRADED，摇晃本身不停止；snooze 上限独立判断。
- 服务：步数按 `baseSteps + sensorDelta` 累计（snooze 后继续累加）；每步刷新通知并广播；snooze 写 `SNOOZED` 会话（deadline 持久化）+ `scheduleSnooze` 独立 requestCode，停止/完成时 `cancelSnooze`。
- 通知：显示实时步数或摇晃进度；snooze action 仅在剩余次数时出现；**去掉直接停止 action**，停止必须满足步数或 10 秒长按。
- 响铃页：实时步数/剩余步数（降级显示摇晃）、snooze 按钮、停止按钮 10 秒长按（短按提示）；启动时 `ACTION_REQUEST_STATE` 拉当前状态，从通知进入不会显示 0 步。
- 服务动作：`ACTION_START` / `ACTION_SNOOZE` / `ACTION_EMERGENCY` / `ACTION_REQUEST_STATE`；未在响铃时收到后三个动作会立即 `stopSelf`。

### DoD

- [x] `.\gradlew.bat :androidApp:assembleDebug` → BUILD SUCCESSFUL
- [x] `.\gradlew.bat test` → 115 tests, 0 failures（data 97 + app 18）
- [x] `StopPolicy`（14）、`StepBaselineTracker`（5）、`ShakeCounter`（5）单测
- [x] snooze deadline 持久化 + 独立 PendingIntent，进程死亡后由闹钟重响恢复同一会话（代码路径）
- [ ] 真机走步停止、锁屏/后台计步、摇晃阈值、snooze 重响：**未验证（无设备）**

---

## 6. 后续任务（按 plan 执行）

完整任务图在 `.omc/plans/step-alarm.md`，摘要：

| ID | 任务 | 关键点 |
|---|---|---|
| T0 | 工具链 + 空 App | **已完成** |
| T1 | 数据层 | **已完成**：Room v2 + DataStore + 73 单测 |
| T2 | 调度与权限 | **已完成**：`setAlarmClock` + 降级、`scheduleSnooze`、Boot/Time 重注册、10 权限 |
| T3 | 响铃服务 | **已完成**：health/specialUse 运行时选择、FSI + 降级、音频/振动/唤醒锁、session 事件 |
| T4 | 步数与停止策略 | **已完成**：COUNTER 基线/DETECTOR/摇晃降级、StopPolicy、snooze、10 秒紧急 |
| T5 | MIUIX 界面骨架 | 列表/编辑/响铃/设置/历史/引导；**当前任务** |
| T6 | 两个开关 + 液态玻璃 | 独立矩阵、默认关闭、持久化、性能降级 |
| T7 | 完整版 | 多闹钟、历史、权限引导、HyperOS 引导、摇晃降级、missed sweep |
| T8 | 真机验收 | spec A–F；FSI/权限撤销、snooze 杀进程、重启、missed |
| T9 | Root 辅助（可选） | allowlist 命令、可撤销；**不做 daemon/轮询/root socket** |
| T10 | 文档与收尾 | README、toolchain、已知限制、状态清理 |

不要跳过 T4 直接做 T5；停止策略与步数基线决定响铃页的进度显示和验收 B/C。

---

## 7. 验收与真机

完整验收在 spec 的 A–F。当前状态：

- A 构建安装：构建 PASS；安装未做（无设备）。
- B 核心闭环：代码完成（调度→响铃→计步→达标停止），**未真机验证**。
- C snooze/紧急/降级：代码完成（snooze 3 次 + deadline 持久化、10 秒长按、摇晃 30 次降级），**未真机验证**。
- D 两个开关：未实现。
- E 完整版：未实现。
- F Root 辅助：未实现。

设备连接后：

```powershell
adb devices
adb install -r E:\MustWake\mustwake\androidApp\build\outputs\apk\debug\androidApp-debug.apk
adb shell am start -n com.mustwake.alarm/.MainActivity
adb logcat -d -s MustWake:* AndroidRuntime:E
```

HyperOS 上需要重点测：锁屏、深度休眠、重启、精确闹钟权限撤销、全屏通知撤销、ACTIVITY_RECOGNITION 撤销、snooze 期间杀进程。

---

## 8. 禁止事项 / 已知坑

1. **不要降 compileSdk 到 36**。Miuix 0.9.4 和 backdrop 2.0.1 的 AAR 都要求 37；降级会直接违反 spec。
2. **不要用系统 JDK 23**。项目固定 JDK 21；JDK 23 可能让 AGP/Gradle 出问题。
3. **不要移除 Miuix 换普通 Compose**。spec 和验收 D/E 依赖 MIUIX。
4. **不要用 daemon/轮询做保活**。root 只做可选辅助（T9），不做常驻进程/root socket。
5. **不要 force-stop 后测试闹钟**。Android 不投递被 force-stop 应用的闹钟；这不是 bug，README 会写明。
6. **不要在没有设备时宣称真机验收通过**。最多说到“构建通过/未安装”。
7. **不要伪造测试**：不要 `test.skip`、`.only`、占位 TODO 当完成。
8. **不要动无关目录**：`E:\MustWake\oh-my-claudecode`、`oh-my-claudecode-port`、`.omc/specs`、`.omc/plans` 是参考/上游，不是本工程代码。
9. **不要改包名/应用名**。`com.mustwake.alarm` / MustWake 是工作假设；要改先问用户。
10. **构建慢是网络**。Aliyun 镜像已加；如果某个依赖卡住，换镜像/重试，不要改架构。

已知警告（非阻塞）：

- Compose 依赖访问器废弃（`compose.runtime` 等）；留给 T5 清理，不要为了警告改版本。
- Gradle 10 不兼容提示；固定 9.8.1。
- `shared` 的 `commonTest` 仍未挂到 compilation；数据层测试放在 `data/src/test`，`shared` 等 T5 有 UI 测试时再处理。

---

## 9. 汇报模板

完成后按这个格式汇报（中文）：

```markdown
## 本次完成
- ...

## 改动文件
- `path:line` — ...

## 验证
- `<command>` → <结果>
- `<command>` → <结果>

## 未完成 / 风险
- ...

## 下一步
- ...
```

要求：只报实际跑过的命令和输出；没验证就写“未验证”；不要用“应该可以”。

---

## 10. 快速开始 checklist

- [ ] 读本文 + `mustwake/docs/toolchain.md` + `mustwake/docs/data-layer.md` + `mustwake/docs/scheduling.md` + `mustwake/docs/ringing.md` + `mustwake/docs/steps.md` + spec + plan。
- [ ] `cd E:\MustWake\mustwake`
- [ ] `.\gradlew.bat :androidApp:assembleDebug` 确认基线可构建。
- [ ] 开始 T5：MIUIX 列表/编辑/响铃/设置/历史页 + MiuixNav。
- [ ] 跑 `.\gradlew.bat test`。
- [ ] 更新 `HANDOFF.md` 状态、`docs/toolchain.md`（如依赖变化）。
- [ ] 提交 git（每个任务一个提交）。
- [ ] 汇报。

---

## 11. 参考文件

- spec：`.omc/specs/deep-interview-step-alarm.md`
- plan：`.omc/plans/step-alarm.md`
- 工具链说明：`mustwake/docs/toolchain.md`
- 数据层说明：`mustwake/docs/data-layer.md`
- 调度说明：`mustwake/docs/scheduling.md`
- 响铃说明：`mustwake/docs/ringing.md`
- 计步与停止说明：`mustwake/docs/steps.md`
- 工程 README：`mustwake/README.md`
- 工具链根说明：`toolchain/README.md`
- APK：`mustwake/androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- 状态文件：`.omc/state/deep-interview-state.json`（deep-interview 已完成，勿当运行状态改）

