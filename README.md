# KMP Seraphim Workbench

用于创建和维护多个 Kotlin Multiplatform 产品的工作台。首个参考产品是个人日常任务看板，覆盖 Android、iOS、Desktop 和 Web/Wasm，各平台 UI 独立实现，KMP 只共享业务规则、应用行为、数据与同步逻辑。

当前仓库处于设计基线阶段，尚未开始生成 Gradle Module。

## 文档

- [项目语境](CONTEXT.md)
- [工作台设计规范](docs/superpowers/specs/2026-08-14-kmp-multi-project-workbench-design.md)
- [Phase 0 实施计划](docs/superpowers/plans/2026-08-14-kmp-workbench-phase-0.md)

## 当前决策

- Android 使用 Jetpack Compose。
- iOS 使用 SwiftUI。
- Desktop 使用 Compose Desktop。
- Web 使用独立 TypeScript UI，通过窄接口调用 Kotlin/Wasm。
- 默认后端使用 Ktor/JVM 和 PostgreSQL，但同步协议不绑定后端实现。
- Phase 0 只建立工作台、生成器和 Android+iOS 最小纵向切片。

