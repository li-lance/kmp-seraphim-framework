# Tooling Instructions

These rules supplement the repository-wide [instructions](../AGENTS.md). Read [Workbench architecture](../docs/architecture.md) before changing the Manifest or generator.

- Validate the complete Manifest before writing Product files.
- Keep parsing, validation, resolution, rendering, structural verification, and publication as independently testable responsibilities.
- Render into a sibling temporary directory and publish atomically only after structural verification.
- Never overwrite a non-empty destination or claim that Render certified a platform build.
- Reject unsupported combinations explicitly; do not silently drop a requested platform or capability.

From the repository root, run `./scripts/check.sh focused tooling` and `./gradlew :tooling:generator:test` for tooling changes.
