# MustWake — 实施计划（由 deep-interview spec 生成）

## Goal

在 `E:\MustWake\mustwake` 新建 Android 应用（包名 `com.mustwake.alarm`，工作名 MustWake）：MIUIX 风格闹钟，响铃后用手机步数判定停止；设置中两个独立开关控制悬浮底栏与液态玻璃；完整版包含 snooze、紧急停止、多闹钟、历史、权限引导、传感器降级；最终在小米/红米 Android 14+ 真机上通过验收。

spec：`.omc/specs/deep-interview-step-alarm.md`（最终歧义 14.5%）。

## Non-goals

iOS/桌面/Web、云同步/账号、社交/排行榜、Wear OS、摄像头/二维码反作弊、付费、24/7 后台常驻服务、**root daemon/轮询**。Root 仅做可选辅助（权限/白名单/appops），不替代 `setAlarmClock`。

## Context

- 工具链（本机实测）：JDK 23，Android SDK platforms 34/35/36，build-tools 34.0.0/36.1.0，adb 可用，当前无连接设备，无全局 Gradle。
- 依赖（已核对 Maven）：
  - Miuix `top.yukonga.miuix.kmp:miuix-ui:0.9.4`，依赖 Kotlin 2.4.20 / Compose 1.12.0；AAR `minCompileSdk=37`。
  - Liquid Glass `io.github.kyant0:backdrop:2.0.1`，依赖 Kotlin 2.4.10 / Compose 1.12.0；AAR `minCompileSdk=37`。
  - 结论：要用当前最新版，必须安装 **compileSdk 37**；不采用“用 36 强行压制 AAR 元数据”的不可靠路径。
- 约束：Miuix/backdrop 均为实验性/快速演进；锁定版本，升级单独验证。

## Acceptance criteria

以 spec 的 A–F 清单为准，全部在真机逐项 PASS/FAIL：

- A 构建安装；B 核心闭环；C snooze/紧急/降级；D 两个开关；E 多闹钟/历史/引导/重启。
- 另加：`gradlew test` 通过；`adb logcat` 无崩溃/ANR；`dumpsys alarm` 能看到本应用闹钟。

## Task graph

| ID | 任务 | 主要文件/产物 | 依赖 | 验证 |
|---|---|---|---|---|
| T0 | 工具链 spike 与脚手架 | Gradle wrapper 引导、`mustwake/`、`gradle/libs.versions.toml`、空 `composeApp` | — | `gradlew --version`；`assembleDebug` 成功；APK 存在 |
| T1 | 数据层与设置 | Room `AlarmEntity`/`AlarmEventEntity`/DAO/DB；DataStore `SettingsRepository` | T0 | `gradlew test`；DAO/设置单测 |
| T2 | 闹钟调度与权限 | `AlarmScheduler`（含 `scheduleSnooze`）、`AlarmReceiver`、`BootReceiver`、manifest 权限、`canScheduleExactAlarms` | T1 | `dumpsys alarm`；2 分钟闹钟；snooze 重响；重启重注册 |
| T3 | 响铃前台服务与音频 | `AlarmService`（health/specialUse 运行时选择）、FSI channel、`RingingActivity`、音频/振动/唤醒锁、session 事件 | T2 | 锁屏响铃；撤销 FSI/ACTIVITY_RECOGNITION 仍可响；停止清理 |
| T4 | 步数追踪与停止策略 | `StepSensorRepository`、`StopPolicy`、fake step source、snooze deadline 恢复 | T2,T3 | 单测 baseline/snooze/例外；真机走步停止 |
| T5 | MIUIX 界面骨架 | `MiuixTheme`、导航、列表/编辑/设置/历史/响铃页 | T0,T1 | Compose UI 测试/截图；页面可达 |
| T6 | 两个开关与液态玻璃 | `floatingBottomBarEnabled`/`liquidGlassEnabled`、`GlassSurface`、`FloatingBottomBar` | T5 | 4 种组合矩阵；重启持久化；无崩溃 |
| T7 | 完整版能力 | 多闹钟、历史、权限引导、HyperOS 白名单引导、摇晃降级、missed sweep | T2,T4,T6 | 真机场景 C/E；missed 事件有写入 |
| T9 | Root 辅助（可选，无 daemon） | `RootHelper`、allowlist 命令、设置入口、撤销 | T2 | F；拒绝 su 回退标准路径 |
| T8 | 真机验收与修复 | 验收记录、logcat、截图、`dumpsys alarm` | T0–T7,T9 | spec A–F 全项；FSI/权限撤销/进程死亡/restart 场景 |
| T10 | 文档与收尾 | `README.md`、构建/权限说明、已知限制、`.omc` 证据 | T8 | 文档命令可复现；状态清理 |

## 详细步骤

### T0 — 工具链 spike（先做，阻塞后续；硬门禁）

1. **先引导 wrapper**：工作区没有项目/`gradlew`、没有全局 Gradle。下载固定版本 Gradle 9.x 发行包，生成 `gradlew`/`gradlew.bat`，确认 `.\gradlew.bat --version` 可运行；不要假设系统已有 Gradle。
2. 安装/确认 `compileSdk 37`（platforms + build-tools 37）；接受 licenses。记录实际路径与来源（AGP 自动下载 / sdkmanager / 手动）。
3. 验证版本组合：AGP 9.3.2、Kotlin 2.4.20、Compose Multiplatform 1.12.0、Miuix 0.9.4、backdrop 2.0.1。
4. **硬门禁**：Miuix 0.9.4 与 backdrop 2.0.1 的 AAR 都要求 `minCompileSdk=37`。若 37 无法安装/下载/授权，或找不到一组**实测 AAR minCompileSdk ≤ 36 的 Miuix + backdrop** 组合，就停下来问用户；**禁止“放弃 Miuix”的降级**（spec 要求 MIUIX，D/E 验收依赖它）。
5. 工程形态：KMP `composeApp`（仅 Android target），Android app 壳；`minSdk 26`，`compileSdk 37`，`targetSdk 36`。
6. 空 App 只显示 `MiuixTheme` + 一个 Text；构建并安装。
7. 若 JDK 23 与 AGP/Gradle 不兼容：安装 JDK 17/21（Temurin/Android Studio JBR），固定 `JAVA_HOME` 到项目脚本；不要用 JDK 23 硬扛。
8. 记录“实际可用版本组合 + JDK + SDK 路径”到 `mustwake/docs/toolchain.md`；这是后续所有任务的前提。

**Verification**：`.\gradlew.bat --version`；`.\gradlew.bat :composeApp:assembleDebug` → BUILD SUCCESSFUL；`adb install -r` 成功；启动无崩溃。

### T1 — 数据层

- Room：`alarms`（id、hour、minute、repeatDays、label、goalSteps、enabled）、`alarm_events`（id、alarmId、type、steps、timestamp、reason）。
- DataStore：`floatingBottomBarEnabled`、`liquidGlassEnabled`、`defaultGoalSteps`、`snoozeMinutes`、`maxSnoozes`、`shakeCount`。
- Repository 接口 + 默认值（开关 false，goal 50，snooze 5min/3 次，shake 30）。
- 迁移策略：初始 version 1；后续加字段用 Migration，不 destructive。

**Verification**：DAO CRUD、设置读写、默认值单测；`gradlew test`。

### T2 — 调度与权限

- `AlarmScheduler`：`setAlarmClock(AlarmClockInfo(triggerAt, showIntent), pendingIntent)`；每闹钟唯一 requestCode；`FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`。
- `scheduleSnooze(alarmId, steps, snoozeCount)`：复用 `setAlarmClock` 重新注册 5 分钟后的响铃；把 snooze deadline 持久化到会话/数据库；进程死亡后仍能恢复。T4 的 snooze 依赖此接口。
- `canScheduleExactAlarms()` 检查；未授权时引导系统设置；不可用时降级 `setAndAllowWhileIdle` 并显示“可能延迟”。
- `AlarmReceiver`：到点启动 `AlarmService`（`startForegroundService`），并行发全屏通知；写入 `scheduled` 事件。
- `BootReceiver` / `TimeChangeReceiver`：重算并重注册；启动时清理过期/僵尸会话。
- manifest：`SCHEDULE_EXACT_ALARM`、`USE_FULL_SCREEN_INTENT`、`POST_NOTIFICATIONS`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_HEALTH`、`FOREGROUND_SERVICE_SPECIAL_USE`、`ACTIVITY_RECOGNITION`、`WAKE_LOCK`、`VIBRATE`、`RECEIVE_BOOT_COMPLETED`。

**Verification**：`adb shell dumpsys alarm | Select-String com.mustwake.alarm` 看到 alarm；设 2 分钟闹钟，receiver 触发日志；snooze 5 分钟后再次响铃（含进程被杀后重启）；重启后仍注册。

### T3 — 响铃服务

- `AlarmService`：5 秒内 `startForeground`；持有 `SensorManager`、`MediaPlayer`/`Ringtone`、`Vibrator`、`WakeLock`；通知含“停止条件/已走步数”；写入 session start/end 事件。
- **FGS 类型运行时选择**：`ACTIVITY_RECOGNITION` 已授予 → `health`；被拒/未授予 → `specialUse`（manifest 声明 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`，说明“alarm step tracking”），或按平台能力用 `shortService`。捕获 `startForeground` 的 `SecurityException`，立即用降级类型重试，不能因为类型失败导致完全不响。
- `RingingActivity`：全屏、锁屏上方显示；展示时间、进度、剩余步数、snooze、紧急停止。
- **全屏通知**：独立高重要性 channel；用 `NotificationManager.canUseFullScreenIntent()` 检查；未授权时引导 `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`；降级为 heads-up + `showIntent` 点击进入响铃页，保证锁屏可见入口。
- 音频：alarm stream；AudioFocus；DND 实际行为记录。
- 状态机：`ringing / snoozed / completed / emergency / degraded-complete / missed`。

**Verification**：锁屏 1 分钟闹钟；响铃、通知、全屏页；撤销全屏权限后仍有 heads-up + 可进入响铃页；撤销 `ACTIVITY_RECOGNITION` 后仍能响铃并走摇晃/紧急路径；手动停止后服务清理。

### T4 — 步数与停止策略

- `StepSensorRepository`：
  - `TYPE_STEP_COUNTER`：响铃时记 `baseline`，`current - baseline`；达到 goal 发完成事件。
  - 缺失时 `TYPE_STEP_DETECTOR`：响铃后累计事件。
  - 注册 `SensorEventListener` 在 `AlarmService` 内；`ACTIVITY_RECOGNITION` 被拒 → 走 specialUse 服务 + 摇晃降级，不能因 health FGS 启动失败而中断响铃。
- `StopPolicy`（纯 Kotlin，可单测）：输入 currentSteps、goal、snoozeCount、emergency、degraded；输出 stop reason。
- Snooze：调用 T2 的 `scheduleSnooze`；暂停响铃、保留累计步数；恢复继续；最多 3 次；deadline 持久化，进程死亡可恢复。
- 紧急停止：长按 10 秒；记录 `emergency`。
- 摇晃降级：加速度阈值判定的 30 次摇晃 + 长按确认；记录 `degraded`。
- Debug fake source：`BuildConfig.DEBUG` 下可注入步数，便于模拟器。

**Verification**：单测覆盖 baseline、snooze 累计/3 次上限/进程死亡恢复、紧急、降级；真机走 20 步停止；撤销权限后仍响铃并可降级停止。

### T5 — MIUIX 界面

- `MiuixTheme` + 深/浅色；`MiuixNav` 或简单状态导航。
- 页面：列表、编辑、响铃、设置、历史、权限引导。
- 目标步数用 Miuix 数字/步进组件；时间选择用 Miuix 组件或 Android TimePicker 包装。
- 空状态、加载、错误状态。
- 不接玻璃前的普通卡片/底栏；保证可交互与可读性。

**Verification**：Compose UI 测试覆盖导航与保存；截图对照 MIUIX 观感。

### T6 — 两个开关 + 液态玻璃

- 设置开关持久化；默认 false。
- `FloatingBottomBar`：开启显示；关闭走普通导航；不依赖玻璃。
- `GlassSurface`：基于 backdrop 2.0.1 自绘玻璃容器（底栏、卡片、对话框）；关闭时用半透明/纯色。
- 独立矩阵：底栏开/关 × 玻璃开/关；性能降级：`Build.VERSION`/帧率/设备能力不足时回退。
- 玻璃不拦截触控；文字对比度检查。

**Verification**：4 种组合逐一手动验证；重启后保持；低端/不支持设备不崩溃。

### T7 — 完整版

- 多闹钟：列表/编辑/删除/启停；每个独立 goal。
- 历史：完成/紧急/降级/错过；分页或最近 N 条。
- 权限引导：逐项解释 + 跳系统设置；拒绝后的降级说明。
- HyperOS：电池优化/自启动引导页；不承诺自动授予。
- 重启恢复：未来闹钟重注册；响铃会话窗口内恢复，否则 missed。
- **missed 写入责任**：`AlarmReceiver` 记录 `scheduled`；`AlarmService` 记录 session start/end；`BootReceiver` 与应用启动时 sweep 超过窗口的 `ringing`/`snoozed` 会话并写 `missed`；加单元测试，确保不会永远停在“响铃中”。
- 摇晃降级完整实现与测试。

**Verification**：spec C/E 场景；两闹钟不同 goal；重启手机；人为制造进程死亡后 missed 有记录。

### T8 — 真机验收

- 连接小米/红米（USB 调试）；`adb devices` 确认。
- 按 spec A–F 逐项执行，记录 PASS/FAIL + 证据（截图、logcat、`dumpsys alarm`、历史）。
- 必测边界：撤销 `USE_FULL_SCREEN_INTENT` 后仍能通过 heads-up/showIntent 进入响铃页；撤销 `ACTIVITY_RECOGNITION` 后仍能响铃并走摇晃/紧急；snooze 期间杀进程后仍能在 deadline 重响；进程死亡后的 `missed` 写入；不开启 Root 增强时 A–E 仍通过，开启后 F 通过。
- 修复后重跑失败项；不通过不得宣称完成。

### T9 — Root 辅助（可选，无 daemon）

- `RootHelper`：检查 `su -c id` 是否可用（Magisk/KernelSU），只在用户主动开启“Root 增强”时调用。
- 设置页展示 **allowlist 命令清单**，逐条执行并显示 stdout/exit code：
  - `pm grant com.mustwake.alarm android.permission.ACTIVITY_RECOGNITION`
  - `pm grant com.mustwake.alarm android.permission.POST_NOTIFICATIONS`
  - `cmd appops set com.mustwake.alarm SCHEDULE_EXACT_ALARM allow`（appop 名以设备实测为准）
  - `cmd appops set com.mustwake.alarm USE_FULL_SCREEN_INTENT allow`（同上）
  - `dumpsys deviceidle whitelist +com.mustwake.alarm`
  - HyperOS 私有设置仅在实测有效时提供，并标注“设备相关”
- 执行后回读验证：`canScheduleExactAlarms()`、`canUseFullScreenIntent()`、`isIgnoringBatteryOptimizations()`。
- 撤销：白名单 `-`、appops `default`，并恢复 App 内开关；所有命令可审计。
- 安全边界：不接受任意 shell 字符串；不建 root socket；不常驻进程；不做轮询。失败/拒绝一律回退标准路径。

**Verification**：拒绝 `su` 时 A–E 仍通过；开启后 F 通过；撤销后恢复默认。

### T10 — 文档与收尾

- `README.md`：构建、安装、权限、已知限制、验收命令。
- `docs/toolchain.md`：实际版本组合与 JDK/SDK 路径。
- 清理临时 `.omc` state；保留 spec/plan/验收证据。
- 总结：变更、证据、未完成/风险。

## Risks and mitigations

| 风险 | 影响 | 缓解 |
|---|---|---|
| compileSdk 37 未安装 / 下载失败 | T0 阻塞 | 硬门禁：装 37，或找到实测 AAR minCompileSdk ≤ 36 的 Miuix+backdrop 组合；两者都不可行就停下来问用户，**禁止放弃 Miuix** |
| 无项目/无全局 Gradle | T0 起不来 | T0 第 0 步下载固定 Gradle 9.x、生成 wrapper，再谈 SDK 37 |
| JDK 23 与 AGP 9/Gradle 不兼容 | 构建失败 | 安装 JDK 17/21；固定 `JAVA_HOME`；写进 `gradle.properties`/脚本 |
| `ACTIVITY_RECOGNITION` 被拒导致 health FGS 启动失败 | 完全不响铃 | 运行时选择 `health`/`specialUse`，捕获 `SecurityException` 后降级重试；验收覆盖撤销权限 |
| `USE_FULL_SCREEN_INTENT` 被撤销 | 锁屏看不到响铃页 | `canUseFullScreenIntent()` 检查 + 深链授权 + heads-up/showIntent 降级；T8 覆盖 |
| snooze 只存在内存 | 进程死亡后不再响 | `scheduleSnooze` 重新 `setAlarmClock`，deadline 持久化；T4 单测恢复 |
| 僵尸响铃会话 | 历史永远停在“响铃中” | Receiver/Service/Boot/App-start 写 session 事件并 sweep 为 `missed`；单元测试 |
| Miuix 0.9.4 实验性 API 变动 | 编译/行为不稳 | 锁 0.9.4；不升级；封装薄适配层 |
| backdrop 2.0.1 无高层组件 | UI 工作量大 | T6 单独估时；先做玻璃容器最小集（底栏/卡片/对话框） |
| HyperOS 杀后台/拦全屏 | 真机不响 | 电池优化/自启动引导；真机 T8 覆盖；记录实际行为 |
| Android 14 传感器后台限制 | 计步停更 | FGS health/specialUse + ACTIVITY_RECOGNITION；真机锁屏测试 |
| 无设备连接 | 无法验收 | 用户连接设备；否则只能到构建/模拟器阶段并明确未验证 |
| 权限被拒 | 功能不可用 | 引导 + 降级（摇晃/紧急）；历史记录原因 |
| 音频/DND 行为不确定 | 闹钟不响/太轻 | 真机测试；alarm stream；必要时引导用户关闭 DND 限制 |
| 用户 force-stop 应用 | 系统不投递闹钟，AlarmManager 闹钟被清空 | 不支持该场景；README 明确说明；验收前正常启动一次，不把 force-stop 后响铃列为缺陷 |
| Root 命令因 ROM/SELinux 差异失败 | 增强不生效 | allowlist + 逐条回读验证；失败不阻塞；不做任意 shell/socket |
| Root 功能的分发/安全风险 | 不能上 Play；误用风险 | 默认关闭、显式授权、命令可审计、可撤销；README 标注个人 sideload |

## Verification plan

1. 单元测试：`.\gradlew.bat test`（StopPolicy、snooze、DAO、设置）。
2. 构建：`.\gradlew.bat :composeApp:assembleDebug`。
3. 安装：`adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk`。
4. 调度证据：`adb shell dumpsys alarm | Select-String com.mustwake.alarm`。
5. 日志：`adb logcat -d -s MustWake:* AndroidRuntime:E`。
6. 真机清单：spec A–F；每项 PASS/FAIL + 证据路径。
7. Root 辅助：分别在拒绝 `su` 与开启增强两种状态跑 F；撤销后复查默认值。
8. 失败处理：同一错误 3 次 → 停止并报告根因；不跳过、不弱化测试。

## Assumptions / open decisions

- 工作名 MustWake，包名 `com.mustwake.alarm`（可改）。
- KMP `composeApp` 仅 Android target；如果 T0 证明 Android-only + JetBrains Compose 更稳，可切换，但需记录理由。
- compileSdk 37 + Miuix 0.9.4 + backdrop 2.0.1；若 T0 硬门禁失败，停止并询问用户，禁止放弃 Miuix。
- Root 辅助可选、默认关闭、无 daemon；需要 root 的设备上用于授权/白名单/appops，失败回退标准路径。
- 默认步数 50；snooze 5 分钟/3 次；紧急 10 秒；摇晃 30 次；开关默认关。
- 玻璃范围：底栏 + 卡片 + 对话框；设置/历史页是否玻璃化在 T6 决定。
- 铃声用系统默认；渐强、内置音源在 T3 决定。
- 历史保留 200 条/90 天；在 T7 可调。

## Critic verdict

Independent critic pass found 6 material findings; all incorporated:

| # | Severity | Finding | Plan fix |
|---|---|---|---|
| 1 | P0 | compileSdk 37 fallback would abandon Miuix and violate spec | T0 hard gate: install 37 or verified pair with minCompileSdk ≤ 36; otherwise stop and ask; no Miuix-less fallback |
| 2 | P0 | health FGS crashes when ACTIVITY_RECOGNITION denied | T3/T4: runtime health/specialUse selection, catch SecurityException, retry with fallback type; acceptance covers revoke |
| 3 | P1 | Full-screen intent under-specified / no fallback | T3: FSI channel, canUseFullScreenIntent, deep link, heads-up/showIntent fallback; T8 tests revoked |
| 4 | P1 | Snooze not rescheduled / lost on process death | T2: scheduleSnooze + persisted deadline; T4 depends on it |
| 5 | P1 | No project/wrapper; T0 assumed Gradle exists | T0 step 0: download pinned Gradle 9.x, generate wrapper, record JDK/AGP/Gradle matrix |
| 6 | P2 | missed events have no writer | T2/T3/T7: receiver/service session events + boot/app-start sweep to missed + unit test |

Verdict after revision: **plan is executable as written**, with T0 as the only hard gate and the FGS/FSI/snooze/missed paths explicitly covered by acceptance.

