# AI Governance Design

**Status:** Approved design on 2026-08-14

**Reference:** `deepseek-ai/deepseek-harness` at `47f943859b`

## 1. Objective

Establish a strict, layered AI governance system for KMP Seraphim Workbench that remains useful as the repository grows across products, platforms, and delivery phases. Codex and Claude must discover the same authoritative instructions, follow the same change-routing rules, and invoke the same repository-owned verification commands.

The design adapts the useful structure of DeepSeek Harness rather than copying its TypeScript-specific rules. Repository instructions remain concise navigation and standing orders; architecture facts, decision rationale, reusable procedures, and executable checks each have a distinct owner.

## 2. Scope

The first governance implementation covers:

- a root `AGENTS.md` and a compatible `CLAUDE.md` entry;
- scoped instructions for documentation, tooling, platform policy, templates, and products;
- repository-owned decision records under `.agents/notes/`;
- project-specific reusable workflows under `.agents/skills/` only when repeated work justifies them;
- focused and full verification entry points under `scripts/`;
- CI integration for the full governance and build gates;
- explicit handling for checks whose product capability does not exist yet.

The implementation does not introduce a governance DSL, generate instruction files from a manifest, or create empty instructions for future directories.

## 3. Design Principles

1. **One authoritative home per fact.** Other documents link to the owner instead of restating it.
2. **Instructions route work.** `AGENTS.md` files state standing orders, required reading, and verification commands; they do not become architecture manuals or implementation plans.
3. **Rules follow directory scope.** The root contains repository-wide invariants. A subtree file contains only rules specific to that subtree.
4. **Strict baseline from the start.** The repository has no legacy implementation debt, so all existing content enters governance immediately.
5. **Checks match the changed surface.** Local work runs focused evidence; CI owns the complete repository and platform matrix.
6. **Machine-checkable rules are executable.** A prose rule is not treated as enforced when a deterministic script can verify it.
7. **Unavailable is not passing.** A check that depends on an unimplemented phase reports that state explicitly and never exits successfully while claiming verification.
8. **Current architecture and delivery phase are separate.** Long-lived invariants belong in governance and architecture; phase-specific tasks remain in plans.
9. **Extraction follows repetition.** A project skill or governance abstraction is created only after repeated work demonstrates a stable procedure.

## 4. Alternatives Considered

### 4.1 Single root instruction file

A single `AGENTS.md` would be easy to create, but it would accumulate product, platform, generator, template, and documentation details. Every agent session would load unrelated rules, and ownership would become ambiguous as the repository grows.

### 4.2 Layered governance

The selected approach uses a compact root entry, scoped subtree instructions, decision records, reusable skills, and executable checks. It adds a small amount of structure now while keeping agent context relevant and allowing each product or platform area to evolve independently.

### 4.3 Manifest-generated governance

A machine-readable governance manifest could eventually generate instruction inventories and CI matrices. The current repository does not yet contain enough repeated product and platform patterns to define that schema safely. This approach is deferred until at least a second real product provides evidence for stable governance metadata.

## 5. Information Architecture

The target structure is:

```text
AGENTS.md                     Repository standing orders, map, routing, invariants
CLAUDE.md                     Claude-compatible alias for the root instructions
CONTEXT.md                    Domain language, current facts, phase boundaries
docs/
├── AGENTS.md                 Documentation placement and writing rules
├── architecture.md           Workbench composition and extension points
├── development.md            Contributor and agent development workflow
├── adr/                      Decisions that constrain shipped architecture
└── superpowers/              Approved designs and implementation plans
.agents/
├── notes/
│   ├── AGENTS.md             Decision-record lifecycle and format
│   ├── proposed/
│   ├── implemented/
│   └── rejected/
└── skills/                   Proven repository-specific workflows
tooling/AGENTS.md              Manifest, generation, publication constraints
platform-kit/AGENTS.md         Build-policy and topology separation rules
templates/AGENTS.md            Template promotion and certification rules
products/AGENTS.md             Product independence and platform boundaries
scripts/                       Governance and build verification entry points
```

Subtree instruction files are added with their owning directories. The implementation must not create placeholder directories solely to satisfy this diagram. Until a subtree exists, its intended rules remain in the nearest existing owner without pretending that the missing component is implemented.

## 6. Documentation Ownership

| Location | Owns | Excludes |
| --- | --- | --- |
| Root `AGENTS.md` | Cross-repository standing orders, project map, task routing, required checks | Detailed architecture, rationale, task checklists |
| Subtree `AGENTS.md` | Orders that apply only within that directory | Rules already owned at the root |
| `CONTEXT.md` | Domain vocabulary, invariants, delivery sequence | Procedural instructions and decision history |
| `docs/architecture.md` | Current system composition, dependency direction, extension points | Phase task lists and rejected alternatives |
| `docs/adr/` | Accepted decisions constraining shipped runtime or generated-product architecture | Proposals, workflow policy, and work-in-progress plans |
| `.agents/notes/` | Proposed decisions plus implemented or rejected governance, process, and testing decisions, including alternatives and consequences | Accepted runtime architecture decisions and reusable procedures |
| `.agents/skills/` | Repeated repository-specific procedures with stable inputs and outputs | Product contracts and one-off task plans |
| `docs/superpowers/` | User-approved designs and executable implementation plans | Permanent standing orders |
| `scripts/` and CI | Deterministic enforcement and complete verification | Architectural rationale |

Existing `CONTEXT.md` remains the source of domain language and invariants. The existing approved workbench design remains authoritative until its current-state architecture content is promoted into `docs/architecture.md`; the migration must use links and remove duplication rather than copy the same facts into three files.

## 7. Agent Task Flow

Every change follows this path:

1. Identify the intended change and affected directories.
2. Read the root `AGENTS.md`.
3. Read the nearest applicable subtree `AGENTS.md` files.
4. Read the linked context, architecture, decisions, and development instructions required by that route.
5. Determine whether the task changes a durable decision: runtime and generated-product architecture updates an ADR; proposals and governance, process, or testing decisions update an Agent Note.
6. Implement the smallest complete change while preserving repository invariants.
7. Update documentation that owns changed behavior in the same change.
8. Run focused checks selected by the affected surface.
9. Run broader integration checks when shared boundaries, generation, templates, or platform topology change.
10. Let CI run the full governance, build, and platform matrix.

Agents report the commands they actually ran. They do not imply that an unavailable platform or unexecuted check passed.

## 8. Change Routing

### 8.1 Generator and manifest changes

Read `tooling/AGENTS.md`, the manifest contract, and generator architecture. Verify schema rejection, deterministic resolution, temporary rendering, structural verification, atomic publication, and protection of non-empty destinations.

### 8.2 Platform-kit changes

Read `platform-kit/AGENTS.md` and toolchain policy. Verify that convention plugins own build, compiler, quality, and test policy without selecting product platforms or Module topology implicitly.

### 8.3 Template changes

Read `templates/AGENTS.md`. Render into a clean directory and certify every platform combination the template claims to support. A template cannot be promoted solely because its files render.

### 8.4 Product changes

Read `products/AGENTS.md` and the product-local instructions when present. Verify independent buildability, native UI ownership, adapter boundaries, and exclusion of unselected platforms from the Module graph.

### 8.5 Shared business changes

Verify common rules and the adapters for every affected selected platform. Shared code may expose commands, snapshots, typed failures, and deliberate event streams; it must not own platform UI.

### 8.6 Documentation and governance changes

Read `docs/AGENTS.md` or `.agents/notes/AGENTS.md`. Verify links, ownership, file structure, instruction budgets, and Codex/Claude entry consistency. A governance rule that can be deterministic must gain or update an executable check.

## 9. Verification Architecture

The repository provides one stable entry point with focused and full modes:

```sh
./scripts/check.sh focused
./scripts/check.sh full
```

`focused` derives or accepts a change surface and runs the smallest credible evidence. `full` executes every available governance and repository check and is the CI default.

Initial governance checks cover:

- **Agent layout:** required instruction entries exist, scoped files are placed correctly, and the Claude entry resolves to the authoritative instructions.
- **Documentation links:** internal Markdown paths and anchors resolve.
- **Documentation ownership:** governed documents satisfy their allowed structure and do not introduce known duplicate authorities.
- **Decision records:** note lifecycle, location, status, and mandatory sections agree.
- **Project boundaries:** platform UI, shared code, platform-kit, templates, and product topology respect declared invariants.
- **Generated products:** Render and Certify are invoked when implemented and applicable.

The bootstrap implementation uses dependency-free scripts for deterministic filesystem, link, and document-structure rules. `scripts/check.sh` only orchestrates named checks. Gradle topology and manifest semantics become typed Gradle or Kotlin checks when those source surfaces exist; regular-expression approximations must not claim to verify them.

## 10. Verification States and Failure Handling

Each check has one of three outcomes:

- **Passed:** the command executed and verified its declared scope.
- **Failed:** a supported check found a violation or its execution failed.
- **Unavailable:** the repository does not yet implement the capability required to execute the check.

`full` fails when a required, currently supported check fails. An unavailable future-phase check must be listed with its owning phase and activation condition. It cannot emit a success message or count toward verified coverage. When the activation condition becomes true, CI configuration and the check registry must change in the same commit.

Failures identify the owning file, violated rule, and expected remediation. Scripts fail early for malformed governance inputs but aggregate independent violations when doing so produces a more useful repair list.

## 11. Codex and Claude Compatibility

`AGENTS.md` is the single source of truth. Instructions use plain Markdown and repository commands rather than agent-specific directives.

At the root, `CLAUDE.md` is a symbolic link to `AGENTS.md`. The agent-layout check rejects a regular file or a link to any other target. If a supported environment later proves unable to consume the link, changing to a minimal pointer requires a recorded governance decision and an updated check in the same change.

Scoped `CLAUDE.md` aliases are added only where Claude does not reliably inherit the root and nearest `AGENTS.md`; compatibility tests or actual usage must justify them. Codex-specific skills and Claude-specific commands may wrap the same repository procedure, but neither may redefine its architectural rules or verification semantics.

## 12. Decision Records and Skills

Agent Notes govern proposals and governance, process, and testing decisions. Accepted decisions about shipped runtime or generated-product architecture use `docs/adr/` instead, so one rationale never has two active homes. Agent Notes use lifecycle directories:

- `proposed/` for decisions awaiting implementation;
- `implemented/` for decisions reflected by current source and tooling;
- `rejected/` only when retaining the rationale prevents a plausible repeated mistake.

Each note records the problem, decision or proposal, genuine alternatives, consequences or risks, and required verification. An accepted architecture proposal moves its durable result into an ADR and leaves no duplicate implemented Agent Note. Implemented Agent Notes describe present governance, process, or testing reality and stay synchronized with renamed paths and interfaces.

A repository skill is justified when the same non-trivial operation recurs and its inputs, steps, failure modes, and verification have stabilized. Likely future candidates include certifying a generated product, adding a platform, adding a reference product, and promoting a template. The initial governance work does not create speculative versions of these skills.

## 13. CI Policy

Local agents run focused checks after each meaningful change. Changes to shared architecture, generation, templates, platform topology, or governance additionally run the relevant integration check before completion.

CI runs full governance checks on every change. Build and certification jobs are selected from the platforms and capabilities that the repository currently declares supported. Exhaustive host and platform matrices remain CI responsibilities unless a task explicitly diagnoses one of those environments.

CI configuration must call the repository-owned verification entry point rather than duplicate its logic in workflow YAML. This keeps Codex, Claude, human contributors, and CI on one executable contract.

## 14. Rollout

The implementation proceeds in dependency order:

1. Establish root instructions, Claude compatibility, documentation ownership, and the governance decision record.
2. Add documentation and Agent Note rules with structural verification.
3. Add subtree instructions as the corresponding source directories are created by the existing Phase 0 plan.
4. Add focused/full command dispatch and CI integration.
5. Connect Render, Certify, Gradle topology, and platform-boundary checks as those executable surfaces land.
6. Reassess a governance manifest only after a second real product exposes repeated metadata.

Strict governance begins with the files that exist. Temporary exceptions require an explicit owner, reason, and removal condition; broad allowlists and silent skips are not permitted.

## 15. Acceptance Criteria

- Codex and Claude reach the same authoritative root and scoped rules.
- A contributor can determine required reading and checks from the changed directory and task type.
- Architecture facts, decision rationale, procedures, plans, and executable enforcement each have one defined owner.
- Broken internal links, invalid decision-record structure, and drifted Claude instructions fail verification.
- Platform UI sharing, platform-kit topology selection, uncertified template claims, and inclusion of unselected platforms are rejected once their owning code surfaces exist.
- A missing future-phase capability is reported as unavailable, never passed.
- Adding a new product or platform extends scoped instructions and the verification matrix without restructuring the root governance model.

## 16. Risks and Mitigations

**Instruction growth:** Root and subtree files can become manuals. Keep root rules short, link to owning documents, and introduce word budgets when measured growth justifies them.

**Duplicate authorities:** Existing design documents may overlap with the new architecture page. Migrate facts deliberately, leave links, and add narrow duplication checks for stable phrases or headings rather than unreliable general prose comparison.

**False confidence from shallow scripts:** Regex checks can appear strict while missing semantic violations. Use them only for deterministic file and token rules, and replace them with structured or build-aware checks at semantic boundaries.

**Premature process weight:** Mandatory notes and checks can slow local work. Limit decision records to non-trivial decisions, keep focused checks narrow, and leave exhaustive matrices to CI.

**Agent-specific drift:** Codex or Claude helpers may evolve separately. Keep repository commands and `AGENTS.md` authoritative; agent-specific adapters only invoke them.
