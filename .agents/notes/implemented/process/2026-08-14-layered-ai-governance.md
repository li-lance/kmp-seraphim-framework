# Agent Note: Layered AI governance

Status: implemented

## Problem

The Workbench has domain and design documents but no stable instruction entry for coding agents, no scoped task routing, and no executable way to distinguish verified behavior from planned behavior. Separate Codex and Claude instructions would allow the same repository rule to drift.

## Decision

`AGENTS.md` is the authoritative instruction entry, and root `CLAUDE.md` links to it. Root instructions route work to scoped instructions and link to the invariants owned by `CONTEXT.md`; they do not copy or own those invariants. Architecture facts, accepted runtime decisions, governance rationale, reusable procedures, and task plans remain in separate owners.

`scripts/check.sh` exposes focused and full verification. The governance validator reports absent future implementation surfaces as unavailable. When a source surface appears, its nearest scoped `AGENTS.md` becomes mandatory in the same change.

## Alternatives considered

**One root instruction file:** This begins simply but forces every agent session to load unrelated product, platform, generator, and template rules as the repository grows.

**Separate Codex and Claude rules:** Native-looking files for each tool duplicate standing orders and create an avoidable consistency problem.

**Manifest-generated governance:** The repository has only one planned reference Product and no repeated governance metadata from which to design a stable schema.

## Consequences

Agents receive smaller, relevant instruction sets and use the same executable checks as CI. New implementation directories must ship scoped instructions with their first source. Maintainers must preserve one owner per fact and update validators when a prose invariant becomes mechanically enforceable. Governance manifest generation remains deferred until another real Product demonstrates repetition.
