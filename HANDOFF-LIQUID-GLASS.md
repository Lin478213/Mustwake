# HANDOFF — 液态玻璃 1:1 复刻（AndroidLiquidGlass）执行交接

> 交接时间：2026-10-21。**实现代码已完成，验证/基线未全部收尾**。
> 批准的计划：`~/.claude/plans/crispy-napping-flamingo.md`（一比一复刻 AndroidLiquidGlass + 全局字体/间距 + 单开关统一控制）。
> 上一交接：`HANDOFF-M1A.md`（M1-A1~A3 已完成）。本文只记录本任务状态与接手要点。

## 需求（用户原话要点）

1. 悬浮底栏和液态玻璃按 GitHub `Kyant0/AndroidLiquidGlass` **一比一复刻**（此前手工近似被否定）；
2. **字体和边上间距**也不对 → 全局对齐参考项目；
3. 能用上的组件全用上，**特性由「液态玻璃」一个开关统一控制**（「悬浮底栏」开关移除）；
4. 不走 OMC 工作流；先计划后实施（计划已获批准）。

## 状态总览

| 项 | 状态 |
|---|---|
| 参考仓克隆（`AndroidLiquidGlass/`，kmp 分支，gitignore） | ✅ |
| 依赖：backdrop 2.0.1（API 实测含 Lens/vibrancy/Highlight/Shadow）+ 显式 `kyant-shapes` 1.2.1 | ✅ |
| 文档先行：appearance.md 重写、ui.md 字号阶、CLAUDE.md 不变量、decisions.md、data-layer.md | ✅ |
| `ui/design/liquid/` 组件移植（8 文件，约 1200 行） | ✅ |
| 适配层接线：MwComponents/App/SettingsScreen/AlarmEditScreen（单开关、对话框宿主、字号阶） | ✅ |
| JVM 回归（**156 例 0 失败**，JUnit XML 计数） | ✅ |
| 截图基线 **15 张全部录制**（含 shell_liquid_light 液态壳、settings_liquid_light 控件态、dialog_liquid_light 玻璃对话框） | ✅ |
| `verifyRoborazziDebug` 连续两次全绿（其一 `--rerun-tasks` 强制实跑） | ✅ |
| 模拟器人工核对 | 🔄 部分完成：tab 点击/拖拽拇指切换 ✅（BUG-1 修复后实测）、液态开关/滑块/±按钮渲染 ✅；对话框、按压放大、色差细节待细查 |
| git 提交 | ✅ 已全部本地提交（见下方提交清单） |

## 提交清单（均在本地，**未推送**）

| 提交 | 内容 |
|---|---|
| `93f3b6a` | `feat(ui): rework liquid glass as 1:1 AndroidLiquidGlass port`（主实现，42 文件） |
| `2d80a4d` | `fix(ui): bottom tab thumb frozen on selection change (BUG-1)` |
| （更早）`6eeba11` / `812323c` / `8f42674` | M1-A3 spike / EOL chore / M1-A3 真实场景 |

推送：`git push` 本环境取不到凭据（ksshaskpass 报错），需用户 `! git push origin master` 自推（一次推全部 5 个）。

## 一比一参数与移植映射（实现已落）

- 参数基线（36dp 边距/64-56dp 高度/28dp 图标/12sp 标签、玻璃配方 vibrancy+blur+lens、动效 DampedDragAnimation/InteractiveHighlight 等）见 `mustwake/docs/appearance.md`；常量集中在 `LiquidStyle.kt`。
- 移植文件：`shared/src/androidMain/kotlin/com/mustwake/alarm/ui/design/liquid/`——
  `LiquidBottomTabs/LiquidBottomTab/LiquidButton/LiquidToggle/LiquidSlider/LiquidDialog/DampedDragAnimation/DragGestureInspector/InteractiveHighlight/LiquidStyle`。
- 单开关语义：`liquidGlassEnabled` 开 = 悬浮液态胶囊底栏 + 液态开关/按钮/滑块 + 玻璃对话框；关 = 原 Miuix 全宽底栏/控件。`floatingBottomBarEnabled` 字段保留（备份 schema v1 兼容）但 UI 不再暴露（`docs/data-layer.md` 已标 deprecated）。
- 全局字号阶（`MwText` 映射 `LiquidStyle`）：Display 28sp Medium / Title 24sp Medium / Headline 17sp / Body 16sp / Label 15sp / Caption 12sp；内容边距 16dp 与参考一致，底栏 36dp。
- 对话框：`MwDialogState` + `MwDialogHost` 由 `App` 在**捕获层外**宿主（玻璃折射不自捕获）；REPLACE 导入确认已改走对话框。

## Robolectric 降级（重要，勿删）

JVM/截图测试编译不了 AGSL 的 `layout(color)` 与 `shader` 类型 uniform（实测报错两种）。降级沿用既有机制 `LocalMwAgslHighlightEnabled`（`MwSquircleFallback` 置 false）：
- `lens(...)` 调用全部 `if (agslHighlight)` 跳过；
- `Highlight.Default/Ambient` → `Highlight.Plain`；**注意 `drawBackdrop` 的 `highlight` 参数默认值就是 `Highlight.Default`**，不显式传就会在测试态炸（外层胶囊行、LiquidButton 已显式传）；
- `InteractiveHighlight` 的 AGSL 高光走白蒙层回退。
真机路径不受影响（该恒为 true）。

## Bug 记录

### BUG-1 底栏 tab 选中态不跟随（2026-10-21 模拟器人工核对发现，**已修复并验证**）

- **现象**：点底栏 tab（闹钟/历史/设置）后页面内容切换了，但底栏的选中滑块/高亮停在原 tab 不动。
- **根因**：参考组件 `LiquidBottomTabs(selectedTabIndex: () -> Int)` 约定 getter 必须是**稳定 lambda**（背后读 State；参考 demo 捕获 `rememberSaveable` 状态对象，编译器记忆化后实例稳定）。`MwBottomBar` 原先传 `{ selectedIndex }`（捕获 Int 的不稳定 lambda）——每次选中变化 lambda 就是新实例，导致组件内部 `remember(selectedTabIndex)` / `LaunchedEffect(selectedTabIndex)` 重置，而拇指动画靠 `snapshotFlow { currentIndex }.drop(1)` 驱动的事件链断裂，`dampedDragAnimation` 收不到 animateToValue，拇指冻结。
- **修复**（`MwComponents.kt` 适配层，1:1 移植文件未动）：getter 改为 `rememberUpdatedState` + `remember { { state.value } }` 固定实例。同一隐患在 `MwSwitch`/`MwSwitchRow`/`MwSlider`/`MwSliderRow`（LiquidToggle/LiquidSlider 的 getter）一并修复。
- **验证**（模拟器实测）：点「历史」tab 拇指滑块随选中移动（设置→历史两张截图对比，拇指从右侧滑到中间，选中项蓝色高亮）；回归测试全绿。
- **教训/约定**：往 `ui/design/liquid/` 组件传 getter 时必须用稳定 lambda（`remember { { state.value } }`），直接传 `{ param }` 是错的。

## 未完成事项（按优先序）

1. ~~确认 `settings_liquid_light` 基线已录 + verify ×2~~ **已完成**（14/14 全绿，其一 `--rerun-tasks`）。
2. ~~**`dialog_liquid_light` 截图用例**（玻璃对话框基线）~~ **已完成**（走路线 b：经 App 壳点「替换导入」触发 `MwDialogHost`，零新增依赖；基线 15 张，verify ×2 全绿，JVM 回归 157 例 0 失败）。
   - 新坑：`performScrollTo` 后「替换导入」按钮贴在视口下边缘、被悬浮玻璃底栏盖住，合成触摸点击会被底栏截走（对话框不弹）；改用 `performSemanticsAction(SemanticsActions.OnClick)` 直接调点击语义动作绕开坐标命中。截图基线只管画面，命中测试归模拟器人工核对。
   - 另注：等待对话框出现要用「确认替换」（对话框独有），不能用「替换导入」——按钮与对话框标题同名。
3. **模拟器人工核对（剩余项）**：tab 点击/拖拽与液态开关/滑块/±按钮已实测 ✅；还差——玻璃对话框（设置页点「替换导入」）、按压放大 78/56、色差折射与跟手高光的细部观感、关开关回退无残留、与参考 demo 对照（可选，仓库内 `AndroidLiquidGlass/androidApp` catalog 可装）。
4. **推送**：`git push` 在本环境取不到凭据（ksshaskpass 报错），需用户 `! git push origin master` 自推，一次推全部 5 个本地提交（见提交清单）。
5. 小项：`MwSlider` 非玻璃态是 NumberPicker（与原一致）；`MwSliderRow` 非玻璃态是 ±步进器。响铃页按钮已是液态皮（MwSecondaryButton 自动切换）。若真机（G0）帧率差，调 `mwGlassSupported()` 降级或减 blur。

## 快速验证

```bash
cd /home/lin/Java/MustWake/mustwake
./gradlew test                             # 应全绿（截图用例非 roborazzi 模式自动跳过）
./gradlew :androidApp:recordRoborazziDebug # 重录基线（UI 故意改版后）
./gradlew :androidApp:verifyRoborazziDebug # 连续两次应全绿
./gradlew :androidApp:assembleDebug        # 构建 APK
```

## 关键文件

- 计划：`~/.claude/plans/crispy-napping-flamingo.md`（1:1 参数基线表）
- 规格：`mustwake/docs/appearance.md`（组件参数）、`mustwake/docs/ui.md`（字号阶/结构）
- 移植源：`/home/lin/Java/MustWake/AndroidLiquidGlass/`（kmp 分支；`app/src/commonMain/.../catalog/components|utils|destinations/DialogContent.kt`）
- 适配层：`mustwake/shared/src/androidMain/kotlin/com/mustwake/alarm/ui/design/MwComponents.kt`、`ui/design/liquid/`
- 接线：`App.kt`（单开关 + MwDialogHost）、`SettingsScreen.kt`（外观区 + REPLACE 对话框）、`AlarmEditScreen.kt`（目标步数滑块）
