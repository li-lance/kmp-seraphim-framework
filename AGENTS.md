# Repository Instructions

KMP Seraphim Workbench is a manifest-driven multi-product Kotlin Multiplatform workbench. Read [CONTEXT.md](CONTEXT.md) before changing architecture or product behavior. Read [docs/architecture.md](docs/architecture.md) before adding source modules, targets, templates, or build policy.

## Repository state

The repository currently contains approved designs and implementation plans. Do not describe a planned Module, command, platform, or verification path as implemented until its source and executable evidence exist.

## Task routing

- Documentation changes follow [docs/AGENTS.md](docs/AGENTS.md).
- Governance, process, and testing decisions follow [.agents/notes/AGENTS.md](.agents/notes/AGENTS.md).
- Implementation follows the approved plan in [docs/superpowers/plans/](docs/superpowers/plans/).
- When `platform-kit/`, `tooling/`, `templates/`, or `products/` exists, read its nearest `AGENTS.md` before editing it.

## Standing invariants

- UI is never shared between Android, iOS, Desktop, and Web applications.
- Platform-kit owns build policy and never selects product platforms or Module topology.
- The manifest is declarative and never embeds Gradle or shell code.
- Render validates and publishes structure atomically; Certify proves selected platform builds.
- A generated product never includes an unselected platform in its Gradle Module graph.
- A template becomes supported only after clean generation and certification of every claimed platform combination.
- Architecture facts, decision rationale, reusable procedures, and implementation plans each have one authoritative home.
- Missing or unavailable verification is reported explicitly and never presented as passing.

## Verification

Run focused checks while iterating:

```sh
./scripts/check.sh focused <changed-path>...
```

Run the complete available governance suite before completing a repository-wide or governance change:

```sh
./scripts/check.sh full
```

Report only commands actually run and platforms actually certified. CI owns the exhaustive supported platform matrix.

## Change discipline

- Preserve unrelated and untracked user changes.
- Keep source files focused and expose narrow interfaces between generator, platform policy, templates, products, and adapters.
- Update the document that owns changed behavior in the same change.
- Record non-trivial governance, process, or testing decisions as Agent Notes; record accepted runtime and generated-product architecture decisions as ADRs.
- Do not create empty future source directories, speculative shared modules, or one-use project skills.
- Files end with exactly one trailing newline; run `git diff --check` before committing.
