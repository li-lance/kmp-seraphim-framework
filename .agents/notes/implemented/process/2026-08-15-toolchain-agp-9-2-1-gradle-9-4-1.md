# Agent Note: Toolchain upgrade to AGP 9.2.1 and Gradle 9.4.1

Status: implemented

## Problem

AGP 9.1.0 was tested only up to compile SDK 36.1, so every Android build warned that compileSdk 37 is unsupported. AGP 9.2.x is the first line supporting API 37 and requires Gradle ≥ 9.4.1, forcing the locked tuple (Kotlin 2.4.10 / AGP 9.1.0 / Gradle 9.3.1) to move as a whole. Gradle ≥ 9.4 additionally broke the wasmJs certification: under `FAIL_ON_PROJECT_REPOS` it rejects the ivy repositories KGP 2.4.10 temporarily adds to download Node/Yarn (`added by unknown code`), and under `PREFER_SETTINGS` the setup tasks' detached configurations resolve only settings repositories.

## Decision

Upgrade the tuple to Kotlin 2.4.10 / AGP 9.2.1 / Gradle 9.4.1 / JDK 17, and point the wrapper `distributionUrl` at the Tencent mirror (`https://mirrors.cloud.tencent.com/gradle/`). Web-enabled generated products emit `RepositoriesMode.PREFER_SETTINGS` plus two settings-declared, content-filtered ivy tool-distribution repositories (`org.nodejs:node` from `https://nodejs.org/dist`, `com.yarnpkg:yarn` from `https://github.com/yarnpkg/yarn/releases/download`); non-web products keep `FAIL_ON_PROJECT_REPOS`. The renderer gained two web-conditional tokens, `__REPOSITORIES_MODE__` and `__WEB_TOOL_REPOS__`, with web:false rendering byte-identical to the previous settings file.

## Alternatives considered

- `android.suppressUnsupportedCompileSdk=37.0` and stay on AGP 9.1.0 / Gradle 9.3.1: zero risk, but leaves the toolchain on a line never tested with the project's compileSdk.
- Gradle 9.5.1 instead of 9.4.1: same repository-validation behavior; 9.4.1 is AGP 9.2.1's default-tested pairing.
- `PREFER_SETTINGS` without settings-declared tool repos: Node/Yarn downloads fail (`Could not find org.nodejs:node`).
- Settings-declared tool repos while keeping `FAIL_ON_PROJECT_REPOS`: Gradle ≥ 9.4 still rejects the plugin-added repositories at add time.
- Kotlin newer than 2.4.10: no newer stable release exists (2.4.20 was RC-only at decision time).

## Consequences

- The compileSdk 37 warning is gone: AGPBI warning count is 0 under Gradle 9.4.1 with configuration cache disabled.
- `scripts/certify-generated-product.sh` passes end to end on Gradle 9.4.1 + AGP 9.2.1: Android `allTests` + `:apps:android:assembleDebug`, and `wasmJsNodeTest` for both web modules.
- `./gradlew :tooling:generator:test` and `./gradlew -p platform-kit test` pass; `./scripts/check.sh full` passes.
- The mirror-hosted Gradle 9.4.1 distribution was verified SHA-256-identical to the official release before adoption.
- Web products resolve tool distributions only through the two content-filtered ivy repositories; dependency resolution still uses settings-declared google() + mavenCentral().
