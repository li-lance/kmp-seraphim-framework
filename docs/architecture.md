# Workbench Architecture

## Current state

The repository contains the domain context, an approved multi-project Workbench design, and implementation plans. It does not yet contain the planned Gradle builds, generator, templates, or reference Product. The [Phase 0 plan](superpowers/plans/2026-08-14-kmp-workbench-phase-0.md) is the executable source for creating that baseline; planned files are not current implementation.

## Target composition

The approved architecture separates five responsibilities:

```text
project.yaml
    ↓
tooling/manifest → tooling/generator → generated Product
                         ↑                  ↓
templates/ ─────────────┘             platform certification
                         ↑
platform-kit/ supplies build policy without selecting Product topology
```

- The Workbench root orchestrates generation and certification.
- `tooling/` parses the Manifest, resolves a deterministic template plan, renders into a temporary directory, verifies structure, and publishes atomically.
- `platform-kit/` supplies compiler, build, quality, and test policy through an included build.
- `templates/` contains only combinations proven by real Products and clean certification.
- `products/` contains independently buildable reference Products.

The approved detailed design is [KMP Multi-Project Workbench Design](superpowers/specs/2026-08-14-kmp-multi-project-workbench-design.md). [CONTEXT.md](../CONTEXT.md) owns domain definitions and invariants.

## Dependency direction

The Manifest selects current Product capabilities. The generator consumes the Manifest and templates. Generated Product builds may consume a locked platform-kit snapshot, but platform-kit never consumes Product source or decides which platforms exist. Platform applications consume narrow shared business interfaces through platform-owned Adapters; shared business code never imports platform UI.

## Lifecycle

Render and Certify are separate operations. Render proves a structurally valid, atomic file publication. Certify builds and tests the already rendered Product on each selected platform. Neither command may imply the guarantee of the other.

## Extension rules

A new Module requires an independently testable responsibility and a real consumer boundary. A new template requires proof from a maintained Product and clean certification. A new platform adds an independent application entry, Adapter rules, generator support, and certification evidence without widening platform-kit into a topology owner.
