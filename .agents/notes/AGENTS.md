# Agent Note Instructions

Agent Notes supplement the repository-wide [instructions](../../AGENTS.md). They record proposals and governance, process, or testing decisions whose rationale and rejected alternatives must survive beyond a task.

## Placement

Use `.agents/notes/<lifecycle>/<class>/YYYY-MM-DD-<topic>.md`.

- Lifecycle is `proposed`, `implemented`, or `rejected`.
- Class is `architecture`, `governance`, `process`, or `testing`.
- An accepted runtime or generated-product architecture decision belongs in `docs/adr/`, not an implemented Agent Note.
- An `implemented/architecture/` Agent Note is invalid; promote the accepted decision to one ADR.
- Do not create empty lifecycle or class directories.

## Required format

Every note begins with exactly this four-line structure, including both blank lines:

```text
# Agent Note: <title>

Status: <lifecycle>
```

Required headings are exact level-2 headings outside fenced blocks and appear in this order:

- Proposed: `## Problem`, `## Proposal`, `## Alternatives considered`, `## Acceptance criteria`, `## Risks`.
- Implemented: `## Problem`, `## Decision`, `## Alternatives considered`, `## Consequences`; write the decision and consequences as present reality.
- Rejected: `## Problem`, `## Proposal`, `## Alternatives considered`; the status and body explain the rejection.

Record genuine alternatives only. When an architecture proposal is accepted, move the durable decision into one ADR and remove or reject the proposal so there are not two active rationale owners.

## Verification

Run:

```sh
./scripts/check.sh focused .agents/notes
```
