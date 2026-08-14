# Template Instructions

These rules supplement the repository-wide [instructions](../AGENTS.md). Templates are promoted only after a maintained Product proves the structure and a clean generated copy is certified.

- Keep template output deterministic for the same Manifest, template catalog, and toolchain lock.
- Use explicit replacement tokens and verify that no unresolved token reaches published output.
- Emit only selected platforms and capabilities.
- Do not place Product-specific roadmap phases or unproven shared abstractions into templates.
- A rendered tree proves structure; only Certify proves supported platform builds.

From the repository root, run `./scripts/check.sh focused templates`, `./gradlew :tooling:generator:test`, and `./gradlew -p <clean-generated-product> <certification-tasks>` for every claimed template combination.
