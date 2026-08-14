# Development Workflow

## Prerequisites

The governance baseline requires Git, a POSIX shell, and JDK 17 or newer. The KMP toolchain versions and platform prerequisites remain locked by the approved [workbench design](superpowers/specs/2026-08-14-kmp-multi-project-workbench-design.md#6-toolchain-policy).

## Start a change

Read the root [repository instructions](../AGENTS.md), then the nearest `AGENTS.md` for every directory being changed. Read [CONTEXT.md](../CONTEXT.md) for domain terms and [architecture.md](architecture.md) for dependency direction. Use the approved design and implementation plan for work that has not yet shipped.

## Local verification

During iteration, pass the affected paths to the focused governance check:

```sh
./scripts/check.sh focused docs tooling
```

Before completing a governance or repository-wide change, run:

```sh
./scripts/check.sh full
git diff --check
```

Run Module tests, Render, and Certify commands required by the nearest scoped instructions. Report unavailable platforms separately from passing checks.

## CI responsibility

CI calls `./scripts/check.sh full` and owns the complete matrix for currently supported hosts and platforms. Workflow YAML invokes repository commands; it does not duplicate validation logic.

## Decision records

Use an ADR for an accepted decision that constrains shipped runtime or generated-product architecture. Use an Agent Note for a proposal or for an implemented or rejected governance, process, or testing decision. Update an existing owner instead of creating a second record for the same rationale.
