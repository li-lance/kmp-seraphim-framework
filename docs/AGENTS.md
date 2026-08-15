# Documentation Instructions

These rules supplement the repository-wide [instructions](../AGENTS.md).

## One home per fact

- [CONTEXT.md](../CONTEXT.md) owns domain language, invariants, and delivery sequence.
- [architecture.md](architecture.md) owns current system composition, dependency direction, and extension points.
- `adr/` owns accepted decisions constraining shipped runtime or generated-product architecture.
- `.agents/notes/` owns proposals and implemented or rejected governance, process, and testing decisions.
- `superpowers/specs/` owns approved feature designs; `superpowers/plans/` owns task-by-task implementation plans.
- [development.md](development.md) owns contributor setup, local checks, and CI responsibilities.

Link to an owning document instead of restating its facts. Move a fact when its owner changes and repair every inbound link in the same change.

## Writing rules

- Describe current state in architecture and development references. Keep delivery status and future work in plans.
- Use direct terms from [CONTEXT.md](../CONTEXT.md); do not invent synonyms for Workbench, Product, Manifest, Render, Certify, Adapter, Operation, Cursor, or Conflict record.
- Keep design rationale and genuine alternatives in ADRs or Agent Notes, not code comments or standing instructions.
- Use relative Markdown links for repository documents.
- Keep headings stable when another governed document links to them.
- Do not hand-edit generated references after generators exist; change their source and regenerate them.

## Verification

Run:

```sh
./scripts/check.sh focused docs
```

For instruction, architecture, context, ADR, Agent Note, or validation changes, also run:

```sh
./scripts/check.sh full
```
