# 技术选型记录

## UI 框架：原生 Compose Miuix（2026-10-19）

**背景**：用户提出「miuix 使用 flutter 版本」。评估了 pub.dev 的 `flutter_miuix`。

**结论：不迁移 Flutter，保留原生 Compose Miuix，并新增设计系统适配层隔离 Miuix。**

依据：

- `flutter_miuix` 是社区移植（非小米官方），1.0.0 于 2026-08 发布，1.3.0 于 2026-09-29 发布，
  周下载约 400–600；作为整个 App 的 UI 基础成熟度不足。
- 原版 Compose Miuix 0.9.4 已在本工程 T0 实测编译，液态玻璃（backdrop 2.0.1）、Monet、
  悬浮底栏均可实现，Flutter 版没有独有能力。
- Flutter add-to-app 与 AGP 9 仍有未关闭的兼容问题（需要 `newDsl/builtInKotlin` 兼容开关），
  本机无 Flutter SDK，集成会重做构建链。
- 闹钟可靠性在 AlarmManager / 前台服务 / 全屏通知 / 传感器，Flutter 不改善这些，还会增加
  平台通道与响铃页冷启动成本；现有 117 个测试基于原生栈。

**保留的退路**：

- 所有 Miuix import 只允许出现在 `shared/src/androidMain/.../ui/design/MwComponents.kt`；
  页面只依赖 `Mw*` 组件。未来若要换 UI 实现，只改适配层。
- 如果将来要做 iOS/桌面，或团队明确以 Flutter 为主，建议在 T8 真机验收后再评估
  「Flutter 只做非响铃页、响铃页保留原生」的混合方案。

## 液态玻璃：一比一移植 Kyant0/AndroidLiquidGlass（2026-10-20）

- **参照源**：`Kyant0/AndroidLiquidGlass`（GitHub 4.2k stars）`kmp` 分支（活跃主线，2026-08；
  `android` 分支是 1.0.6 冻结旧线，组件集相同无独有内容）。仓库克隆在根目录
  `AndroidLiquidGlass/`（gitignore，作为上游参考）。
- **不内嵌库**：继续用 Maven `io.github.kyant0:backdrop:2.0.1`（缓存已验证含
  Lens/vibrancy/Highlight/Shadow/CombinedBackdrop 全 API，Maven 最新即 2.0.1）+ 显式
  `io.github.kyant0:shapes:1.2.1`（Capsule/RoundedRectangle）；只移植参考的 catalog 组件层
  源码（`ui/design/liquid/`）。
- **开关语义**（用户确认）：`liquidGlassEnabled` 单开关统一控制全部液态特性；
  `floatingBottomBarEnabled` 并入废弃（字段保留做备份 schema v1 兼容）。
- **排版**（用户确认）：字体/间距全局对齐参考（28/24/17/16/15/12sp 字号阶、底栏 36dp 边距）。
- 早先手工近似玻璃（blur 24dp + 55% tint）废弃；动效（拖动拇指/按压/色差/跟手高光）按参考
  原逻辑保留，不做简化。
