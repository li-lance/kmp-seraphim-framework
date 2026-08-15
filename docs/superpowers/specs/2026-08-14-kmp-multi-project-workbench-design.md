# KMP Multi-Project Workbench Design

**Status:** Approved design, revised after architecture audit on 2026-08-14

**Repository:** `kmp-seraphim-framework`

**Reference product:** `daily-board`

## 1. Objective

Replace the discarded legacy framework with a manifest-driven workbench that can create and maintain multiple Kotlin Multiplatform products.

The first product is a personal daily task board supporting:

- Android with Jetpack Compose;
- iOS with SwiftUI;
- Desktop with Compose Desktop;
- Web with an independently implemented TypeScript UI backed by Kotlin/Wasm;
- an optional Ktor/JVM and PostgreSQL backend;
- local-first behavior before account and synchronization features exist.

KMP shares business rules, application behavior, data behavior, and synchronization logic. UI code is never shared between platform applications.

## 2. Non-Goals

- Preserve compatibility with the discarded repository.
- Share Compose UI with iOS or Web.
- Generate arbitrary Gradle or shell code from the manifest.
- Build a universal framework before real products demonstrate reuse.
- Require a backend for the local task board.
- Treat OpenAPI as a complete synchronization specification.

## 3. Design Principles

1. **Native UI ownership:** every platform owns navigation, lifecycle, accessibility, theme, permissions, notifications, and presentation state.
2. **Real reuse before extraction:** reusable templates and cross-product Module interfaces are promoted only after a reference product or second product proves them.
3. **Official DSL first:** generated Gradle files use official Kotlin and Android target DSLs; platform-kit does not duplicate target selection with a custom abstraction.
4. **Deterministic generation:** the same manifest, template catalog, and toolchain lock produce the same file tree.
5. **Explicit lifecycle:** render, certify, migrate, and export are separate commands with separate guarantees.
6. **Replaceable adapters:** storage, transport, server, and Web/Wasm integration implement narrow product interfaces.
7. **Compatibility intersection:** versions are selected from the mutually supported Kotlin, AGP, Gradle, JDK, Xcode, and Node.js intersection, not by independently choosing each latest release.

## 4. Repository Architecture

```text
kmp-seraphim-framework/
├── platform-kit/              # Included-build toolchain, quality, and test policy
├── tooling/
│   ├── manifest/              # Schema, parsing, validation
│   ├── generator/             # Resolution, rendering, verification
│   └── cli/                   # Phase 1 standalone entry point
├── templates/                 # Proven template catalog
├── products/
│   └── daily-board/           # Maintained reference product
├── docs/
│   ├── adr/
│   └── superpowers/
├── project.yaml               # Workbench reference-product manifest
├── CONTEXT.md
└── README.md
```

The workbench root is an orchestration build. Each generated product is its own Gradle build and may be opened independently.

## 5. Platform Kit

`platform-kit` owns build policy, not product topology.

It may provide focused convention plugins for:

- Kotlin compiler and JDK policy;
- Android application defaults;
- quality checks;
- common test configuration;
- Ktor/JVM application defaults when Phase 2 begins.

It must not provide a generic KMP plugin that unconditionally adds Android. Generated shared Module build files directly declare their selected official targets:

```kotlin
plugins {
    kotlin("multiplatform")
    id("seraphim.kotlin-policy")
    id("com.android.kotlin.multiplatform.library") // only when Android is selected
}

kotlin {
    android { /* Android-only configuration */ }
    iosArm64()
    iosSimulatorArm64()
    jvm("desktop")
    wasmJs { browser() }
}
```

Only targets selected by the manifest are emitted. Product identifiers and product dependencies remain in product build files.

## 6. Toolchain Policy

The Phase 0 compatibility lock is:

| Tool | Version |
| --- | --- |
| Kotlin | 2.4.10 |
| Android Gradle Plugin | 9.2.1 |
| Gradle | 9.4.1 |
| JDK | 17 |
| Android compileSdk/targetSdk | 37 |
| Android minSdk | 26 |
| Xcode | 26.4.x |

One checked-in toolchain catalog is the source of truth for both the root build and platform-kit. Direct included-build dependencies must read the same values rather than repeat literals.

An automated upgrade is accepted only when the entire selected tuple is listed as mutually supported and all affected certification jobs pass.

## 7. Product Manifest

`project.yaml` is declarative and schema-versioned:

```yaml
schema: 1
product:
  id: daily-board
  package: com.seraphim.dailyboard
platforms:
  android: true
  ios: true
  desktop: true
  web: true
server:
  ktor: true
data:
  strategy: offline-first
features:
  authentication: true
  sync: true
web:
  ui: external-typescript
```

The manifest records desired current state. Delivery labels such as `phase-2` belong in plans or issues and are invalid manifest values.

Schema validation covers:

- schema version;
- product and package naming;
- selected platform set;
- data strategy;
- capability requirements and incompatibilities;
- unsupported Phase-specific combinations.

Manifest validation completes before product files are written.

## 8. Generation Lifecycle

### 8.1 Render

`render` provides a structural transaction:

1. Parse the manifest.
2. Validate schema and supported combinations.
3. Resolve a deterministic template plan.
4. Render into a sibling temporary directory.
5. Verify required files, paths, tokens, and generated metadata.
6. Atomically move the temporary directory to the destination.

The destination must be absent or empty. A failure leaves no partial product at the destination. Render never claims that platform builds passed.

### 8.2 Certify

`certify` operates on an already rendered product and runs the checks appropriate to its selected platforms:

- common tests;
- Android host tests and assemble;
- iOS framework link and Xcode build;
- Desktop tests and package;
- Wasm production build and browser contract tests;
- Ktor tests and database migration tests.

CI certification uses a fresh generated directory. A template combination is documented as supported only after certification passes.

### 8.3 Migrate

`migrate` is a future explicit command. It produces a change report before modifying template-owned infrastructure and never silently replaces product source code.

## 9. Standalone Export

Phase 1 adds `seraphim new` using the same generator engine as the Gradle entry point.

The first standalone implementation vendors the locked platform-kit snapshot into:

```text
.workbench/platform-kit/
```

The exported product includes `workbench.lock`:

```yaml
schema: 1
workbench: 1.0.0
templateCatalog: 1.0.0
platformKit: 1.0.0
toolchain:
  kotlin: 2.4.10
  agp: 9.2.1
  gradle: 9.4.1
```

Publishing platform-kit as an external plugin artifact is deferred until a second real product demonstrates sufficient reuse.

## 10. Product Architecture

The full reference product has this logical shape:

```text
daily-board/
├── apps/
│   ├── android/
│   ├── ios/
│   ├── desktop/
│   └── web/
├── server/
│   └── ktor/
└── shared/
    ├── task-board/            # Rules, use cases, state, and ports
    ├── local-data/            # Created when storage adapters become real
    ├── sync/                  # Created only in Phase 2
    └── web-adapter/           # Narrow Kotlin/Wasm to TypeScript interface
```

The workbench does not generate `domain`, `application`, `data`, and `sync` Module shells merely to mirror technical layers. A new Module is justified when it has an independently testable responsibility and a real Seam, such as alternative adapters or different target constraints.

For Phase 0, `shared:task-board` contains the smallest real vertical slice consumed by Android and iOS. Phase 1 may split local-data only when persistence implementations create a meaningful interface.

## 11. Platform UI Interfaces

Shared Kotlin exposes commands, immutable snapshots, typed failures, and deliberately selected event streams. It does not expose platform view-model types.

- Android adapters map shared snapshots to Compose state.
- iOS adapters map shared snapshots to Swift value types and lifecycle-aware observation.
- Desktop adapters map shared snapshots to Compose Desktop state.
- Web adapters expose only JavaScript-compatible primitives, JSON, functions, opaque handles, and explicit disposal.

Kotlin collections, exceptions, coroutine implementation types, repository implementations, and database records are not exported to Swift or TypeScript.

## 12. Web/Wasm Policy

Web/Wasm is a supported product target but remains isolated because Kotlin/Wasm and generated TypeScript declarations are pre-stable.

The TypeScript UI consumes a checked contract rather than Kotlin implementation details. Browser and Node contract tests verify the generated JavaScript interface. A later Kotlin/JS or pure TypeScript implementation may replace the Wasm adapter without changing the Web UI contract.

## 13. Android KMP Policy

Shared Module Android targets use `com.android.kotlin.multiplatform.library` and `kotlin { android { ... } }`.

- Android application entry points remain separate `com.android.application` Module entries.
- Android-KMP host tests use `androidHostTest` and are enabled explicitly with `withHostTest`.
- Device tests use `androidDeviceTest` and are enabled only when required.
- The shared Android target has a single variant; feature flavors remain in separate standard Android Module adapters if ever required.

## 14. Local-First Data Flow

In Phase 1 each command follows this flow:

1. A platform UI invokes a task-board command.
2. Shared rules validate the command.
3. The local repository transaction commits business data.
4. The shared snapshot advances only after commit.
5. The platform adapter publishes native presentation state.

The product remains fully usable without an account, backend, or network.

## 15. Synchronization Protocol

Phase 2 adds an operation log, transactional outbox, server change stream, cursor, and conflict records.

Each operation contains:

- stable operation identifier;
- aggregate identifier;
- base server revision;
- payload schema version;
- payload;
- client metadata used for diagnostics, not authoritative ordering.

The server assigns accepted revisions. Device wall-clock time does not decide last-write-wins ordering.

The synchronization interface defines:

- idempotent operation acceptance;
- ordering and revision rules;
- cursor advancement preconditions;
- retry and partial-batch behavior;
- conflict classification;
- rank normalization behavior;
- protocol compatibility failures.

OpenAPI describes transport shapes. A separate sync protocol document and shared conformance suite define behavior.

## 16. Conflict Policy

- Concurrent changes to different fields merge by field.
- Concurrent scalar changes use server revision ordering and retain audit metadata.
- Delete wins over update; deleted records remain restorable.
- Reordering uses stable ranks with idempotent server normalization.
- Unsafe merges create a queryable conflict record for explicit resolution.

The client applies returned changes, records conflicts, confirms outbox operations, and advances the cursor in one local transaction.

## 17. Error Handling

- Validation failure does not enter the outbox.
- Storage failure rolls back and retains the last committed snapshot.
- Network failure retains pending operations and retries with bounded exponential backoff and jitter.
- Authentication failure pauses synchronization without deleting local data.
- Protocol incompatibility stops the affected batch and records actionable diagnostics.
- Unexpected platform failures are captured at platform entry points.

## 18. Testing and Certification

Tests are organized by responsibility:

- manifest schema and resolver tests;
- deterministic rendering and atomic-publication tests;
- template contract tests;
- task-board rule and command tests;
- persistence adapter contract tests;
- Web adapter contract tests;
- synchronization conformance tests;
- platform smoke tests.

CI maintains canonical supported vectors instead of every Cartesian combination:

1. Android+iOS local-only default product.
2. One minimal product per supported platform.
3. Full daily-board local product.
4. Full daily-board offline-first product with Ktor.
5. Standalone exported product outside the workbench path.

## 19. Delivery Phases

### Phase 0: Workbench foundation

- Establish the clean repository and locked compatibility tuple.
- Implement target-neutral platform policy.
- Implement schema v1, resolver, render, and structural verification.
- Generate and certify an Android+iOS local-only daily-board slice.

### Phase 1: Complete local product

- Implement task and board workflows and local persistence.
- Add Desktop and Web/Wasm adapters and native UI implementations.
- Add CLI and standalone export with vendored platform-kit and workbench.lock.

### Phase 2: Account and synchronization

- Add Ktor, PostgreSQL, authentication, migrations, and OpenAPI transport models.
- Define the synchronization protocol and conformance suite.
- Add transactional outbox, cursor sync, conflicts, retries, and multi-device tests.

### Phase 3: Template productization

- Promote proven capabilities into a versioned template catalog.
- Add explicit migration reports.
- Create a second real product and extract only demonstrated cross-product reuse.

## 20. Acceptance Criteria

The design is successful when:

1. A contributor can render a product without copying convention logic by hand.
2. Unselected platforms are absent from the generated Module graph.
3. Render failure leaves no partial destination.
4. Certification clearly reports which platform builds passed.
5. Android, iOS, Desktop, and Web UIs contain no shared UI source.
6. The local daily board works without a backend.
7. A standalone export builds outside the workbench directory.
8. Generated products record exact workbench and toolchain provenance.
9. Wasm changes remain local to the Web adapter.
10. Sync retries are idempotent and a cursor never advances before durable local application.

## 21. Authoritative References

- [KMP compatibility guide](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html)
- [Android-KMP library plugin](https://developer.android.com/kotlin/multiplatform/plugin)
- [KMP platform stability](https://kotlinlang.org/docs/multiplatform/supported-platforms.html)
- [Kotlin/Wasm JavaScript interop](https://kotlinlang.org/docs/wasm-js-interop.html)
- [Jetpack Compose dependency setup](https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler)
