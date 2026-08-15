# Workbench Architecture

## Current state

Phase 0 交付根编排构建、目标中立的 platform-kit、Manifest 校验、结构 Render 事务、Android+iOS 模板与认证入口。Phase 1 子系统①已落地：task-board 持有规则/命令/Store/端口（单看板多列 Kanban、首末固定列、归档/恢复、稠密 rank）；local-data-sql 用 SQLDelight（android+ios，`.sqm` 迁移与版本校验）实现端口；local-data-web 用 IndexedDB（仅 wasmJs）实现同一端口；Android/iOS 应用由持久化 Store 驱动。web 未选时 wasm target 与 web 存储模块经条件 token 缺席（不变量 3），Web 组合由 `certification/web-enabled.yaml` 夹具认证。Desktop、Web UI、CLI 导出与 Android/iOS 完整 Kanban UI 仍未实现。

## Phase 0 composition

```text
project.yaml
    ↓
tooling/generator → products/daily-board
        ↑                    ↓
templates/           Android and iOS certification
        ↑
platform-kit supplies build policy without selecting Product topology
```

- The Workbench root orchestrates generation and certification.
- `tooling/generator` validates the Manifest, resolves the template, renders into a sibling temporary directory, verifies structure, and publishes atomically.
- `platform-kit/` supplies compiler, build, quality, and test policy through an included build.
- `templates/daily-board-base/` owns the certified Android+iOS template.
- `products/daily-board/` is the independently buildable reference Product.

The approved detailed design is [KMP Multi-Project Workbench Design](superpowers/specs/2026-08-14-kmp-multi-project-workbench-design.md). [CONTEXT.md](../CONTEXT.md) owns domain definitions and invariants.

## Dependency direction

The Manifest selects current Product capabilities. The generator consumes the Manifest and templates. Generated Product builds may consume a locked platform-kit snapshot, but platform-kit never consumes Product source or decides which platforms exist. Platform applications consume narrow shared business interfaces through platform-owned Adapters; shared business code never imports platform UI.

## Lifecycle

Render and Certify are separate operations. Render proves a structurally valid, atomic file publication. Certify builds and tests the already rendered Product on each selected platform. Neither command may imply the guarantee of the other.

## Extension rules

A new Module requires an independently testable responsibility and a real consumer boundary. A new template requires proof from a maintained Product and clean certification. A new platform adds an independent application entry, Adapter rules, generator support, and certification evidence without widening platform-kit into a topology owner.
