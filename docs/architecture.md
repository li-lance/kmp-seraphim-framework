# Workbench Architecture

## Current state

Phase 0 implements the root orchestration build, target-neutral platform-kit, Manifest validation, structural Render transaction, Android+iOS daily-board template, maintained reference Product, and clean certification entry point. Desktop, Web/Wasm, persistence, backend, authentication, synchronization, migration, and standalone CLI export remain outside the implemented baseline.

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
