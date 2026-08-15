# ADR: Root plugin classloader hoisting

Status: accepted

Date: 2026-08-15

## Context

During Phase 0 implementation (commit `f8101d4`), both the generated Product build and the Workbench root build failed on Kotlin Gradle Plugin (KGP) cross-classloader conflicts:

- In the Product build, sibling projects each resolved the Kotlin and Android plugins from their own classloader, so KGP's shared `KotlinNativeBundleBuildService` was registered with incompatible classes across siblings and the build failed.
- In the Workbench root build, the configuration cache rejected a type mismatch between `BuildFusService` and `FlowActionBuildFusService` loaded from different plugin classloaders (same upstream class of issue as gradle/gradle#38607).

## Decision

Every plugin shared by more than one project in a build is declared in that build's root `plugins` block with `apply false`, so all sibling projects resolve plugin classes from one classloader. Modules still apply the plugins themselves; the root declaration only pins the resolution scope and version.

This decision is baked into the certified template root [templates/daily-board-base/build.gradle.kts](../../templates/daily-board-base/build.gradle.kts), which declares `kotlin-multiplatform`, `kotlin-compose`, `android-application`, and `android-kmp-library` with `apply false`, and therefore into every Rendered Product. The Workbench root build does the same for `kotlin-jvm`.

## Alternatives considered

- Disable the configuration cache: hides the `BuildFusService` mismatch instead of resolving the classloader split, and gives up build performance and cache-correctness feedback for every Product build.
- Per-module plugin resolution without root hoisting: this was the failing state; each sibling project loads its own copy of the plugin classes and KGP's build services collide across classloaders.

## Consequences

- Plugin versions resolve once at the root through the same version catalog, so a template or Product cannot drift into per-module plugin versions.
- Any new plugin applied by more than one module in a build must be hoisted into that build's root `plugins` block with `apply false` in the same change.
- A related constraint from the same commit: `withHostTest {}` registers the `androidHostTest` source set during script execution, after Kotlin DSL static accessors are generated. Build scripts must therefore configure it with `named("androidHostTest") { dependencies { ... } }`; the static accessor `androidHostTest.dependencies { ... }` does not compile.
