# Platform Kit Instructions

These rules supplement the repository-wide [instructions](../AGENTS.md). Read [Workbench architecture](../docs/architecture.md) before changing build policy.

- Platform-kit owns compiler, build, quality, and test policy; it never selects Product platforms or Module topology.
- Convention plugins configure an already selected official Kotlin or Android plugin and must not apply platform plugins implicitly.
- Toolchain values come from the root version catalog; do not duplicate dependency versions.
- Test policy with Gradle TestKit and focused plugin tests before running affected generated-Product certification.

From the repository root, run `./scripts/check.sh focused platform-kit` and `./gradlew -p platform-kit test` for platform-kit changes.
