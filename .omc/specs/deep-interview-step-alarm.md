# MustWake — 步数闹钟规格（deep-interview）

## Metadata

- 来源想法：创建一个闹钟软件，通过手机传感器获取步数，必须走到目标步数才能停止响铃；MIUIX 风格；设置里加开关启用悬浮底栏与液态玻璃。
- 阈值：0.20（source: default）
- 最终歧义：0.145（14.5%）
- 轮次：8
- 组件：闹钟核心、步数追踪、MIUIX 界面、悬浮底栏、液态玻璃、持久化、Android 平台/后台、构建与验证
- 上游事实（已核对 README）：
  - Miuix 是 Compose Multiplatform UI 库，实验性，Kotlin 2.4.x / Compose 1.12.x；模块含 `miuix-ui`、`miuix-preference`、`miuix-icons`、`miuix-blur`、`miuix-shader`、`miuix-nav`。
  - Liquid Glass 是 `io.github.kyant0:backdrop`，Compose Multiplatform；不含高层组件，底栏/按钮/玻璃表面需要自己组合；示例工程 minSdk 23、compileSdk 37、AGP 9.3.2。

## Goal

做一个 **Android 本地闹钟**：到点后必须通过真实步行达到该闹钟设定的步数，才能停止响铃；界面使用 MIUIX 风格；设置中提供两个**独立**开关，分别启用悬浮底栏和液态玻璃效果。完整版本还要支持 snooze、紧急停止、多闹钟、历史记录、权限引导和传感器降级。

**成功的样子**：用户在小米/红米 Android 14+ 真机上设置一个 2 分钟后的闹钟，锁屏后闹钟响起；用户走够 20 步后响铃停止，历史记为完成；两个开关可独立切换并持久化。

## Users and situations

- 主要用户：需要“必须起身走动才能关闹钟”的重度赖床者。
- 主设备：小米/红米，HyperOS，Android 14+；最低兼容 Android 8（minSdk 26）。
- 典型场景：睡前设闹钟 → 早上响铃 → 锁屏/后台计步 → 走到目标 → 停止；迟到/身体不适时可用 snooze 或紧急停止。
- 非目标用户：需要云端同步、社交排名、iOS、Wear OS 的用户。

## Topology

1. 闹钟核心（alarm-core）
2. 步数追踪（step-tracking）
3. MIUIX 界面（miuix-ui）
4. 悬浮底栏开关（floating-bottom-bar）
5. 液态玻璃开关（liquid-glass）
6. 本地持久化（persistence）
7. Android 平台与后台（android-platform）
8. 构建与验证（build-verification）

## Functional requirements

### 1. 闹钟核心

- 新建/编辑/删除/启用/停用闹钟；支持多个闹钟。
- 每个闹钟包含：时间、重复星期、标签、目标步数（默认 50，范围 10–500）、是否启用。
- 使用 `AlarmManager.setAlarmClock()` 触发，保证系统闹钟语义和可靠性；到点启动响铃前台服务 + 全屏响铃 Activity。
- 响铃：默认系统闹钟铃声 + 振动；音量走 alarm stream；具体铃声/渐强在 plan 阶段定。
- 停止条件：达到目标步数；或 snooze；或紧急停止；或传感器降级路径完成。
- Snooze：默认 5 分钟，最多 3 次；snooze 期间不重置步数，恢复响铃后继续累计；达到 3 次后禁用 snooze 按钮。snooze deadline 必须持久化，并用 `setAlarmClock` 重新注册；进程死亡后仍能在 deadline 重响，否则记 `missed`。
- 紧急停止：长按 10 秒；停止响铃，历史记录为“紧急停止/未完成”；不视为完成。
- 响铃会话状态机：`scheduled → ringing → snoozed → ringing → completed | emergency | degraded-complete | missed | cancelled`；`scheduled`、session start/end、`missed` 都由 Receiver/Service/Boot/App-start 写入。

### 2. 步数追踪

- 首选 `Sensor.TYPE_STEP_COUNTER`：响铃瞬间记录累计基线 `baseline`，目标为 `baseline + goal`；使用差值判断，避免把响铃前的步数算进去。
- 若设备无 `STEP_COUNTER` 或传感器不可用，降级到 `Sensor.TYPE_STEP_DETECTOR`：响铃后累计事件数，达到 goal 停止。
- 计步在锁屏、后台、屏幕关闭时仍要工作：响铃期间启动前台服务。`ACTIVITY_RECOGNITION` 已授予时用 `foregroundServiceType=health`；被拒/未授予时改用 `specialUse`（manifest 声明 subtype 说明“alarm step tracking”），捕获 `startForeground` 的 `SecurityException` 并降级重试；不能因类型失败导致完全不响。通知文案显示“闹钟响铃中 · 已走 X/Y 步”。
- 权限：`ACTIVITY_RECOGNITION`（运行时）；被拒绝时进入降级/引导流程。
- 传感器不可用或权限被拒：降级为“连续摇晃 30 次 + 长按确认”；历史记录为“降级完成”，并显示原因。
- 调试模式：提供可注入的 fake step source，用于模拟器和 UI 测试；正式构建默认关闭。
- 不把“摇晃手机”当作正常计步，只在降级路径使用。

### 3. MIUIX 界面

- 技术栈：Kotlin + Compose Multiplatform（Android target），MIUIX 作为 UI 基础；实验性 API 需锁版本。
- 页面：
  - 闹钟列表（首页）：闹钟卡片、开关、下次响铃时间、空状态。
  - 闹钟编辑：时间选择、重复星期、标签、目标步数、保存/删除。
  - 响铃页：大时间、步数进度环/进度条、剩余步数、snooze 按钮、紧急停止入口。
  - 设置页：两个独立开关、权限状态、关于。
  - 历史页：完成/紧急停止/降级/错过事件。
  - 权限引导页：逐项解释并跳转系统设置。
- 使用 `MiuixTheme`；深色/浅色跟随系统；可选用 Monet 动态色。
- 所有关键操作有可读的 content description；响铃页支持大字号。

### 4. 悬浮底栏开关

- 设置项：`floatingBottomBarEnabled`，默认 **关闭**。
- 开启：主页面显示悬浮底栏（MIUIX 组件优先；若 Miuix 无合适组件，用 Miuix 容器 + Liquid Glass/半透明背景自绘）。
- 关闭：使用普通导航（顶部/普通底栏），不显示悬浮底栏。
- 底栏只影响导航呈现，不影响功能；状态持久化。

### 5. 液态玻璃开关

- 设置项：`liquidGlassEnabled`，默认 **关闭**。
- 开启：对悬浮底栏、卡片、对话框应用 liquid glass（`io.github.kyant0:backdrop`；无高层组件，需要自绘玻璃容器）。
- **独立于底栏**：关闭悬浮底栏时，卡片/对话框仍可保留玻璃效果；关闭玻璃时底栏仍可用（改用不透明/半透明背景）。
- 性能/兼容降级：设备不支持或帧率过低时，自动退回半透明/纯色，不崩溃；设置页仍显示开关状态。
- 玻璃不遮挡文字与触控；对比度满足可读性。

### 6. 本地持久化

- 闹钟、历史、响铃会话：本地数据库（Room）或等价的类型安全存储；设置在 DataStore。
- 设置项至少：两个开关、默认步数、snooze 次数上限、摇晃阈值。
- 重启后闹钟恢复：`BOOT_COMPLETED` receiver 重新注册未来闹钟；响铃中的会话在进程恢复时按时间窗口恢复或标记为 missed。
- 历史保留：默认最近 200 条或 90 天，二者取先到；不云同步。

### 7. Android 平台与后台

- `minSdk 26`，`compileSdk 37`，`targetSdk 36`（Miuix 0.9.4/backdrop 2.0.1 的 AAR 要求 `minCompileSdk=37`；本机需安装 37）。
- 精确闹钟：使用 `AlarmManager.setAlarmClock(AlarmClockInfo, PendingIntent)`，这是系统认可的“闹钟”语义：精确、Doze 下可唤醒、系统状态栏显示下一个闹钟；比 `setExact` 更适合本应用。
- 精确闹钟权限：Android 12+（API 31）声明并引导授予 `SCHEDULE_EXACT_ALARM`；Android 13+（API 33）若按“闹钟类应用”定位，可评估 `USE_EXACT_ALARM`（自动授予，但受 Google Play 政策限制）。两者都不可用时，降级到 `setAndAllowWhileIdle` 并在 UI 明确提示“可能延迟”。
- `PendingIntent` 用 `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`，每个闹钟唯一 requestCode；`AlarmClockInfo` 的 showIntent 指向响铃页，供系统“下一个闹钟”点击。
- 重启/时间变更：`BOOT_COMPLETED`、`TIME_SET`、`TIMEZONE_CHANGED` 后重算并重新注册所有未来闹钟；系统重启会清空 AlarmManager。
- 全屏响铃：`USE_FULL_SCREEN_INTENT`；用 `NotificationManager.canUseFullScreenIntent()` 检查，未授权时深链 `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`，并降级为高重要性 heads-up + `showIntent` 点击进入响铃页；`POST_NOTIFICATIONS` 同样要引导。
- 前台服务：`FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_HEALTH`；从 alarm receiver 启动后 5 秒内 `startForeground`。`ACTIVITY_RECOGNITION` 被拒时 health 类型会抛 `SecurityException`，必须改用 `specialUse`（声明 subtype）或 `shortService` 并捕获重试；不能因为 FGS 类型失败而不响。
- 计步权限：`ACTIVITY_RECOGNITION`；`WAKE_LOCK`、`VIBRATE`、`RECEIVE_BOOT_COMPLETED`。
- HyperOS：提供电池优化/自启动引导页；不承诺程序化授予；权限被拒时说明后果并走降级路径。OEM 可能拦截后台启动或全屏通知，真机验收必须覆盖。
- 音频：使用 alarm stream；DND/静音行为按系统策略，真机验收时记录实际表现。
- **force-stop 限制**：用户手动强制停止应用后，Android 不投递该应用的闹钟；不把“force-stop 后仍响”列为支持场景，README 明确说明。

### 7.1 调度方案对比（为什么是 setAlarmClock）

| 方案 | 判定 | 原因 |
|---|---|---|
| `AlarmManager.setAlarmClock()` | **首选** | 系统闹钟语义、精确、Doze 可唤醒、状态栏显示下一个闹钟；`AlarmClockInfo.showIntent` 可直达响铃页。 |
| `setExactAndAllowWhileIdle()` | 降级 | 精确但无闹钟语义；精确闹钟权限被拒时同样受限；仅在无法使用 `setAlarmClock` 时考虑。 |
| `setAndAllowWhileIdle()` | 最后降级 | 不精确，可能延迟数分钟；必须明确提示用户“可能延迟”。 |
| `WorkManager` / `JobScheduler` | 不采用 | 面向可延迟任务，没有精确到分钟保证；闹钟会迟到。 |
| 长期前台服务常驻 | 不采用 | 耗电、Play 政策风险，且进程仍可能被杀；不能替代系统调度。 |
| 交给系统时钟 `ACTION_SET_ALARM` | 不采用 | 会失去自定义的“步数才能停”闭环。 |
| 服务端推送唤醒 | 不采用 | 依赖网络与后端，离线不可用，延迟不可控。 |
| Health Connect / Google Fit 步数 | 不采用 | 非实时、同步延迟，无法驱动响铃中的实时停止判定。 |

结论：**没有比 `setAlarmClock` 更适合的 API**；“更好”来自组合与降级，而不是换调度器：`setAlarmClock` + `FOREGROUND_SERVICE_HEALTH` + 全屏通知 + `BOOT_COMPLETED`/时间变更重注册 + HyperOS 白名单引导 + 真机测试矩阵。

### 7.2 Root 辅助（可选，不做 daemon）

用户设备已 root（Magisk/KernelSU）。Root 只用于**辅助标准路径**，不替代 `setAlarmClock`，不写常驻 daemon，不做用户态轮询。

- 入口：设置页“Root 增强”，默认关闭；只有用户主动开启并看到命令清单后才请求 `su`。
- 能力（命令以设备实测为准，全部走 allowlist，不接受任意 shell）：
  - 授予运行时权限：`pm grant`（`ACTIVITY_RECOGNITION`、`POST_NOTIFICATIONS`）。
  - 精确闹钟/全屏 intent 的 appops 放行（如 `SCHEDULE_EXACT_ALARM`、`USE_FULL_SCREEN_INTENT`，按设备实际 appop 名验证）。
  - 电池优化白名单：`dumpsys deviceidle whitelist +com.mustwake.alarm`。
  - HyperOS 的省电/自启动限制：优先跳系统页面；OEM 私有 settings 仅在实测有效时提供，并显示“设备相关”。
- 每次执行显示命令与结果；失败不阻塞标准路径，回退到普通权限引导。
- 提供“撤销 Root 增强”：撤销白名单/appops（`-` / `default`），并恢复默认设置。
- 安全：不做 root socket、不常驻进程、不开无认证 IPC；所有命令在 UI 可审计。
- 分发：root 增强不能上 Google Play；README 明确说明，个人 sideload 使用。

验收见 F；Root 不可用时 A–E 必须仍然全部通过。

### 8. 完整版附加

- 多闹钟、历史记录、权限引导、传感器降级、snooze、紧急停止均属于第一版范围。
- 不做：iOS/桌面/Web、云同步/账号、社交/排行榜、Wear OS、摄像头/二维码反作弊、付费。

## Constraints / non-goals

- Android 单平台；小米/红米 Android 14+ 为主，minSdk 26。
- 本地优先，无后端、无登录、无网络依赖。
- Miuix 为实验性库：锁定版本，避免 API 漂移；升级需单独验证。
- Liquid Glass 不提供现成组件：玻璃容器、底栏、按钮需要自绘，工作量计入计划。
- 不使用 Claude Code 专属工具；实现与验证走 DSH 的 `pwsh`、`read`/`write`/`edit`、`subagent`。
- 当前 adb 无设备；真机验收需要用户连接手机并开启 USB 调试。
- 本机只有 JDK 23、SDK 34/35/36、无全局 Gradle；需要 Gradle wrapper、安装 compileSdk 37，并处理 AGP/JDK 兼容。
- Root 增强是可选项，不做常驻 daemon/轮询/root socket；root-only 功能不能上 Google Play，README 说明为个人 sideload 场景。

## Acceptance criteria

按真机（小米/红米 Android 14+）逐项 PASS/FAIL：

### A. 构建与安装
- [ ] `gradlew assembleDebug` 成功，产出 APK。
- [ ] `adb install -r` 成功；冷启动无崩溃。

### B. 核心闭环
- [ ] 设置 2 分钟后的闹钟，目标 20 步；锁屏等待。
- [ ] 到点响铃（声音+振动），全屏响铃页出现。
- [ ] 屏幕关闭/后台时步数仍更新；进度显示 X/20。
- [ ] 走够 20 步后响铃停止；历史记录“完成”，步数=20。
- [ ] 撤销 `USE_FULL_SCREEN_INTENT` 后，锁屏仍出现高重要性 heads-up；点击 `showIntent` 可进响铃页。

### C. Snooze / 紧急停止 / 降级
- [ ] 未达标时 snooze；5 分钟后再次响铃，步数继续累计；第 4 次 snooze 被禁用。
- [ ] snooze 期间杀掉进程，deadline 到达时仍能重响（或明确记 missed，不能静默丢失）。
- [ ] 长按 10 秒紧急停止；响铃停止；历史记录“紧急停止/未完成”。
- [ ] 撤销 `ACTIVITY_RECOGNITION` 或用无计步器设备；**闹钟仍然响铃**（FGS 降级为 specialUse），出现摇晃降级；连续摇晃 30 次 + 长按确认后停止；历史记录“降级完成”；不能因 health FGS 启动失败而崩溃。

### D. 两个开关
- [ ] 设置中两个开关独立；默认关闭。
- [ ] 开悬浮底栏：底栏显示；关：不显示，导航仍可用。
- [ ] 开液态玻璃：底栏/卡片/对话框有玻璃效果；关：不崩溃，改为半透明/纯色。
- [ ] 关底栏但开玻璃：卡片/对话框仍有玻璃；关玻璃但开底栏：底栏仍可用。
- [ ] 两个开关重启后保持。

### E. 完整版
- [ ] 两个不同目标步数的闹钟都能触发。
- [ ] 历史页显示完成/紧急/降级/错过事件。
- [ ] 进程死亡或错过响铃窗口后，App 启动/Boot 时把僵尸会话写成 `missed`，不会永远停在“响铃中”。
- [ ] 权限引导页能跳到系统设置；拒绝权限时有解释和降级路径。
- [ ] 重启手机后未来闹钟仍触发。
- [ ] logcat 无崩溃、无 ANR；关键路径有日志。

### F. Root 辅助（可选）
- [ ] 不开启 Root 增强时，不弹 `su`；A–E 仍全部通过。
- [ ] 主动开启后，UI 显示将执行的 allowlist 命令；`su` 授权后逐条显示结果。
- [ ] 授权/白名单生效后，深度休眠 + 重启场景下闹钟仍触发；不依赖 daemon 或轮询。
- [ ] 拒绝 `su` 或命令失败：回退标准路径，不崩溃、不阻塞响铃。
- [ ] “撤销 Root 增强”能恢复白名单/appops 默认值。

## Assumptions

| # | 假设 | 依据 | 可逆？ | 如何确认 |
|---|---|---|---|---|
| 1 | 工作名 MustWake，包名 `com.mustwake.alarm` | cwd 为 `E:\MustWake` | 是 | 用户确认 |
| 2 | 目标步数默认 50，范围 10–500 | 常见起床步数 | 是 | 设置页可改 |
| 3 | snooze 5 分钟、最多 3 次、步数累计 | Round 3 推荐方案 | 是 | 设置项 |
| 4 | 紧急停止长按 10 秒 | Round 6 | 是 | 可调常量 |
| 5 | 摇晃降级 30 次 | 合理默认 | 是 | 可调常量 |
| 6 | 两开关默认关闭 | “可以启用”语义 | 是 | 设置默认值 |
| 7 | 玻璃作用于底栏 + 卡片/对话框 | Round 5 独立开关 | 是 | 可缩小范围 |
| 8 | 本地存储，Room + DataStore | 完整版历史需求 | 是 | plan 可改 DataStore JSON |
| 9 | compileSdk 37 | Miuix 0.9.4 / backdrop 2.0.1 的 AAR `minCompileSdk=37`（已实测） | 是 | plan T0 安装并验证 |

## Open questions

1. 应用名/包名是否用 MustWake / `com.mustwake.alarm`？
2. ~~compileSdk 36 + 锁旧版，还是装 SDK 37？~~ 已决定：**compileSdk 37 + Miuix 0.9.4 + backdrop 2.0.1**（见 plan T0）。
3. 关闭悬浮底栏时，导航用顶部栏还是普通底栏？
4. ~~液态玻璃是否覆盖设置页/历史页？~~ 第一版只覆盖**底栏 + 卡片 + 对话框**；设置/历史页后置。
5. 闹钟铃声用系统默认还是内置音源？是否需要渐强？
6. ~~摇晃阈值？~~ 接受 **30 次 + 长按确认**。
7. ~~历史保留？~~ 默认 **200 条 / 90 天**。
8. JDK 23 与 AGP/Gradle 的兼容方案：T0 spike 决定，优先安装 JDK 17/21。

## Decision log

| Round | 问题 | 回答 | 对歧义的影响 |
|---|---|---|---|
| 0 | 8 个顶层组件拆分 | 确认全部 8 个 | 拓扑锁定 |
| 1 | 平台与 minSdk | 小米/红米 Android 14+，minSdk 26 | Constraints ↑ |
| 2 | 成功标准 | 完整版（snooze/紧急/多闹钟/历史/引导/降级） | Success ↑，范围扩大 |
| 3 | 停止规则 | 默认 snooze 累计、最多 3 次，后续可配置 | Goal ↑ |
| 4 | 步数来源 | STEP_COUNTER + 响铃基线，缺失降级 STEP_DETECTOR | Goal ↑ |
| 5 | 两个开关 | 独立：底栏控制导航，玻璃控制底栏+卡片/对话框 | Goal ↑ |
| 6 | 停止例外 | 传感器降级摇晃；正常时长按 10 秒紧急停止，记录未完成 | Success ↑ |
| 7 | 验证环境 | 小米/红米真机，adb 安装，锁屏后台实测 | Constraints/Success ↑ |
| Root | 保活/唤醒方案 | 选择 root 辅助标准路径；不做常驻 daemon/轮询/root socket | 可靠性 ↑，新增安全/分发约束 |
| Critic | 独立复核 | 6 findings：SDK 37 硬门禁、health FGS 权限、FSI 降级、snooze 持久化、wrapper 引导、missed 写入 | 已并入 plan |

## Handoff

- 最终歧义 14.5% ≤ 20%，spec 与 plan 已完成。
- plan：`.omc/plans/step-alarm.md`；独立 critic 的 6 条发现已并入（T0 硬门禁、T2 snooze、T3 FGS/FSI、T7 missed）。
- 下一步：执行 T0 工具链 spike（Gradle wrapper → SDK 37 → 空 App 构建）。T0 不通过就停下询问，不降级放弃 Miuix。
- 也可把 spec 作为 `autopilot` 的 Phase 0 输出直接执行。
