# Repository Instructions

KMP Seraphim Workbench is a manifest-driven multi-product Kotlin Multiplatform workbench. Read [CONTEXT.md](CONTEXT.md) before changing architecture or product behavior. Read [docs/architecture.md](docs/architecture.md) before adding source modules, targets, templates, or build policy.

## Repository state

The repository currently contains approved designs and implementation plans. Do not describe a planned Module, command, platform, or verification path as implemented until its source and executable evidence exist.

## Task routing

- Documentation changes follow [docs/AGENTS.md](docs/AGENTS.md).
- Governance, process, and testing decisions follow [.agents/notes/AGENTS.md](.agents/notes/AGENTS.md).
- Implementation follows the approved plan in [docs/superpowers/plans/](docs/superpowers/plans/).
- When `platform-kit/`, `tooling/`, `templates/`, or `products/` exists, read its nearest `AGENTS.md` before editing it.

## Standing orders

- Follow the authoritative [project invariants](CONTEXT.md#invariants); link to their owner instead of copying them into instructions.
- Keep architecture facts, decision rationale, reusable procedures, and implementation plans in their routed authoritative homes.
- Report missing or unavailable verification explicitly and never present it as passing.

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
