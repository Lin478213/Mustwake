# MustWake 交接文档

> 交接时间：2026-10-19 14:03（UTC+8）
> 交接状态：**T0 完成，T1 未开始**
> 工作区：`E:\MustWake`
> 项目：`E:\MustWake\mustwake`
> 工具链：`E:\MustWake\toolchain`

---

## 1. 一页结论

- 目标：Android 步数闹钟。到点响铃后，必须走到目标步数才能停止；MIUIX 风格；设置里两个独立开关控制悬浮底栏与液态玻璃。
- 当前只完成了 **T0 工具链 + 空 App**：本地 JDK/Gradle/Android SDK 已装好，KMP + Miuix 工程可编译，APK 已产出。
- 下一步是 **T1 数据层**：Room（闹钟/历史）+ DataStore（设置）+ 默认值 + 单测。**不要先做 UI 或闹钟服务。**
- 当前没有连接 Android 设备，APK 尚未安装/运行；真机验收是 T8 的事。
- 必读顺序：本文 → `mustwake/docs/toolchain.md` → `.omc/specs/deep-interview-step-alarm.md` → `.omc/plans/step-alarm.md` → `mustwake/README.md`。

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
├─ docs\toolchain.md
├─ shared\
│  ├─ build.gradle.kts
│  └─ src\
│     ├─ commonMain\kotlin\com\mustwake\alarm\App.kt
│     └─ androidMain\AndroidManifest.xml
└─ androidApp\
   ├─ build.gradle.kts
   └─ src\main\
      ├─ AndroidManifest.xml
      └─ kotlin\com\mustwake\alarm\MainActivity.kt
```

### 4.2 当前代码

- `App.kt`：Miuix 主题 + 居中文本 “MustWake”。仅骨架。
- `MainActivity.kt`：`ComponentActivity` + `setContent { App() }`。
- `AndroidManifest.xml`：只有 launcher activity；**没有权限、没有 Service、没有 Receiver**。
- 没有 Application 类、没有 DI、没有 Room/DataStore、没有测试源集。

### 4.3 构建产物

- APK：`E:\MustWake\mustwake\androidApp\build\outputs\apk\debug\androidApp-debug.apk`
- 大小：15,234,367 bytes
- SHA256：`D2F3388C4B522665C7252909BA288728A949A54C3A8249BA847BD69351A3A541`
- 包名：`com.mustwake.alarm`
- minSdk 26 / targetSdk 36 / compileSdk 37
- 构建日志：`build-selfcontained-log.txt`、`build-miuix-log.txt`

### 4.4 Git

`E:\MustWake` 和 `E:\MustWake\mustwake` **都不是 git 仓库**。不要擅自 `git init` 或提交；如需版本控制先问用户。

---

## 5. 下一任务：T1 数据层（现在从这里开始）

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

- [ ] `.\gradlew.bat :androidApp:assembleDebug` 成功。
- [ ] `.\gradlew.bat test` 成功（至少 settings 默认值/校验测试）。
- [ ] Room schema 导出文件存在。
- [ ] 没有 UI/Service/权限改动（除非编译必需）。
- [ ] 在 `docs/` 或 `HANDOFF.md` 更新 T1 状态、依赖版本、测试命令。
- [ ] 报告：改了哪些文件、验证命令与输出、遗留风险。

---

## 6. 后续任务（按 plan 执行）

完整任务图在 `.omc/plans/step-alarm.md`，摘要：

| ID | 任务 | 关键点 |
|---|---|---|
| T0 | 工具链 + 空 App | **已完成** |
| T1 | 数据层 | Room + DataStore + 测试；当前任务 |
| T2 | 调度与权限 | `setAlarmClock`、`scheduleSnooze`、Boot/Time 重注册、精确闹钟/全屏权限 |
| T3 | 响铃服务 | health/specialUse 运行时选择、FSI channel + 降级、音频/振动、session 事件 |
| T4 | 步数与停止策略 | STEP_COUNTER 基线、STEP_DETECTOR 降级、StopPolicy 单测、snooze deadline 恢复 |
| T5 | MIUIX 界面骨架 | 列表/编辑/响铃/设置/历史/引导 |
| T6 | 两个开关 + 液态玻璃 | 独立矩阵、默认关闭、持久化、性能降级 |
| T7 | 完整版 | 多闹钟、历史、权限引导、HyperOS 引导、摇晃降级、missed sweep |
| T8 | 真机验收 | spec A–F；FSI/权限撤销、snooze 杀进程、重启、missed |
| T9 | Root 辅助（可选） | allowlist 命令、可撤销；**不做 daemon/轮询/root socket** |
| T10 | 文档与收尾 | README、toolchain、已知限制、状态清理 |

不要跳过 T1 直接做 T3/T5；数据层接口会决定后面所有页面和服务。

---

## 7. 验收与真机

完整验收在 spec 的 A–F。当前状态：

- A 构建安装：构建 PASS；安装未做（无设备）。
- B 核心闭环：未实现。
- C snooze/紧急/降级：未实现。
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

- Compose 依赖访问器废弃（`compose.runtime` 等）；T1 可顺手清理，但不要为了警告改版本。
- Gradle 10 不兼容提示；固定 9.8.1。
- `commonTest` 未挂到 compilation；T1 处理。

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

- [ ] 读本文 + `mustwake/docs/toolchain.md` + spec + plan。
- [ ] `cd E:\MustWake\mustwake`
- [ ] `.\gradlew.bat :androidApp:assembleDebug` 确认基线可构建。
- [ ] 开始 T1：Room + DataStore + 默认值 + 单测。
- [ ] 跑 `.\gradlew.bat test`。
- [ ] 更新 `HANDOFF.md` 状态、`docs/toolchain.md`（如依赖变化）。
- [ ] 汇报。

---

## 11. 参考文件

- spec：`.omc/specs/deep-interview-step-alarm.md`
- plan：`.omc/plans/step-alarm.md`
- 工具链说明：`mustwake/docs/toolchain.md`
- 工程 README：`mustwake/README.md`
- 工具链根说明：`toolchain/README.md`
- APK：`mustwake/androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- 状态文件：`.omc/state/deep-interview-state.json`（deep-interview 已完成，勿当运行状态改）

