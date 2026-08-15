# ADR: iOS links a single Kotlin/Native framework

Status: accepted

Date: 2026-08-15

## Context

Phase 1 子系统①（任务与看板工作流 + 本地持久化）原设计让 iOS app 同时链接 `TaskBoardShared.framework` 和 `LocalDataSql.framework` 两个静态 framework，各自内嵌一份 Kotlin/Native 运行时。本机模拟器实测（iPhone 16 Pro / iOS 18.3）应用启动即 SIGABRT：进程加载第二个 framework 时，第二次 `+[KotlinBase load]` 触发 `injectToRuntime()`，Kotlin 运行时断言同一进程只允许注入一次，崩为 `RuntimeAssertFailedPanic`（crash report 佐证）。修复随 commit `f0969ee`（"fix: link a single static framework for iOS to embed one Kotlin runtime"）落地。

## Decision

iOS app 恰好链接**一个**静态 Kotlin/Native framework（`LocalDataSql`），task-board 的类型经 `binaries.framework { export(project(":shared:task-board")); transitiveExport = true }` 并入该 framework（见 [templates/daily-board-base/shared/local-data-sql/build.gradle.kts](../../templates/daily-board-base/shared/local-data-sql/build.gradle.kts)）。独立 `TaskBoardShared` framework 不再产出，Swift 侧只 `import LocalDataSql`，跨框架 cast 消失。由此遵循 Kotlin/Native「一个进程只嵌入一份运行时」的官方约束。

配套约束：

- **API 作用域依赖**：KGP export 校验要求被导出项目及其传递依赖为对应 source set 的 API 依赖；task-board 的 `kotlinx-coroutines-core` 与 local-data-sql 的 `project(":shared:task-board")` 均从 `implementation` 改为 `api`，否则报 `not specified as API-dependencies of a corresponding source set`。
- **app 侧自链 sqlite3**：sqldelight native driver 的 sqlite3 cinterop 在 iOS 无 linkerOpts，且静态 framework 不向消费方传播 linkerOpts（实测 app 链接记录中无 LC_LINKER_OPTION），故 app 在 [templates/daily-board-base/apps/ios/project.yml](../../templates/daily-board-base/apps/ios/project.yml) 设 `OTHER_LDFLAGS: -lsqlite3` 自链系统 sqlite3。

## Alternatives considered

- 保持两个静态 framework 同链：已实测失败——双份 Kotlin 运行时在加载期二次 `injectToRuntime` 断言崩溃，无规避空间。
- 把 task-board 与 local-data-sql 合并为单一模块：可行但放大改动面——web 未选时 task-board 仍需独立存在（wasmJs 条件发射），且 SQLDelight 插件与 wasmJs target 在同一模块无法干净解耦（见 spec §6 模块拆分理由），故保留模块边界、仅合并链接产物。

## Consequences

- iOS 侧只有 `LocalDataSql.framework` 一个链接面；新增被导出模块时必须重复本 ADR 的 export + API 依赖 + linkerOpts 传播三件套，或将其并入现有 framework。
- 被 export 的模块必须把公开 API 依赖声明为 `api`，模块 API 面受 KGP export 校验约束。
- app 的 `OTHER_LDFLAGS` 承载 native 链接参数（当前为 `-lsqlite3`），新增 cinterop 链接需求时在 app 侧同步维护。
- 验证：链接/构建 BUILD SUCCEEDED；模拟器启动无崩溃（进程存活），`daily-board.db` 落盘并种子「开始/结束」两列（端到端跑通）。
