# AI Governance Baseline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a strict, layered AI governance baseline that gives Codex, Claude, human contributors, and CI one authoritative instruction and verification path.

**Architecture:** Root and scoped Markdown files own standing instructions while `CONTEXT.md`, architecture documentation, ADRs, Agent Notes, and plans each retain one distinct class of facts. A dependency-free Java 17 validator checks instruction layout, internal links, decision-note structure, and the presence of scoped instructions when implementation surfaces appear; `scripts/check.sh` exposes stable `focused` and `full` modes to agents and CI.

**Tech Stack:** Markdown, POSIX shell, Java 17 source and test programs, Git symbolic links, GitHub Actions.

## Global Constraints

- `AGENTS.md` is the single authoritative agent-instruction source.
- Root `CLAUDE.md` is a symbolic link whose exact target is `AGENTS.md`.
- Root instructions contain repository-wide standing orders and routing, not detailed architecture or task checklists.
- Subtree instructions contain only rules owned by that subtree and are created with the corresponding implementation directory.
- `CONTEXT.md` owns domain language, invariants, and delivery sequence.
- `docs/architecture.md` owns current system composition, dependency direction, and extension points.
- `docs/adr/` owns accepted decisions constraining shipped runtime or generated-product architecture.
- `.agents/notes/` owns proposals and implemented or rejected governance, process, and testing decisions.
- `docs/superpowers/` continues to own approved designs and implementation plans.
- Every governed fact has one authoritative home; other documents link to it.
- Local agents run focused checks; CI runs `./scripts/check.sh full`.
- A future-phase check reports `UNAVAILABLE` with its activation condition and never reports `PASS` without executing evidence.
- The governance baseline must not create empty future source directories or speculative project skills.
- Existing untracked `.idea/` content belongs to the user and must not be staged or modified.
- All created text files end with exactly one newline.

## Planned File Structure

```text
.
├── AGENTS.md
├── CLAUDE.md -> AGENTS.md
├── CONTEXT.md
├── .agents/
│   └── notes/
│       ├── AGENTS.md
│       └── implemented/process/2026-08-14-layered-ai-governance.md
├── .github/workflows/governance.yml
├── docs/
│   ├── AGENTS.md
│   ├── architecture.md
│   ├── development.md
│   └── superpowers/plans/2026-08-14-kmp-workbench-phase-0.md
└── scripts/
    ├── check.sh
    ├── governance/GovernanceCheck.java
    └── tests/GovernanceCheckTest.java
```

The implementation does not create `platform-kit/`, `tooling/`, `templates/`, or `products/`. Task 5 updates the existing Phase 0 plan so each directory receives its scoped `AGENTS.md` in the same task that creates its real source surface.

---

### Task 1: Add the Governance Validator

**Files:**
- Create: `scripts/governance/GovernanceCheck.java`
- Create: `scripts/tests/GovernanceCheckTest.java`
- Create: `scripts/check.sh`

**Interfaces:**
- Produces: `GovernanceCheck.run(Path root, Mode mode, List<String> changedPaths): List<Result>`.
- Produces: CLI `java scripts/governance/GovernanceCheck.java <focused|full> [changed-path ...]`.
- Produces: stable repository command `./scripts/check.sh <focused|full> [changed-path ...]`.
- Consumes: Java 17 or newer and a repository working tree.

- [ ] **Step 1: Write the failing executable tests**

Create `scripts/tests/GovernanceCheckTest.java`:

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class GovernanceCheckTest {
    public static void main(String[] args) throws Exception {
        acceptsValidBaseline();
        rejectsRegularClaudeFile();
        rejectsBrokenMarkdownLink();
        ignoresLinksInsideCodeFences();
        rejectsBrokenMarkdownAnchor();
        rejectsDuplicateContextOwnership();
        rejectsOversizedRootInstructions();
        rejectsMalformedAgentNote();
        requiresScopedInstructionsWhenSurfaceExists();
        System.out.println("GovernanceCheckTest: PASS");
    }

    private static void acceptsValidBaseline() throws Exception {
        Path root = fixture();
        List<GovernanceCheck.Result> results =
            GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of());
        assertNoFailure(results);
        assertState(results, "platform-kit-instructions", GovernanceCheck.State.UNAVAILABLE);
    }

    private static void rejectsRegularClaudeFile() throws Exception {
        Path root = fixture();
        Files.delete(root.resolve("CLAUDE.md"));
        Files.writeString(root.resolve("CLAUDE.md"), "Read AGENTS.md\n");
        assertState(
            GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()),
            "agent-layout",
            GovernanceCheck.State.FAIL
        );
    }

    private static void rejectsBrokenMarkdownLink() throws Exception {
        Path root = fixture();
        Files.writeString(root.resolve("docs/development.md"), "# Development\n\n[missing](missing.md)\n");
        assertState(
            GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()),
            "markdown-links",
            GovernanceCheck.State.FAIL
        );
    }

    private static void ignoresLinksInsideCodeFences() throws Exception {
        Path root = fixture();
        Files.writeString(
            root.resolve("docs/development.md"),
            "# Development\n\n```markdown\n[fixture](missing.md)\n```\n"
        );
        assertNoFailure(GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()));
    }

    private static void rejectsBrokenMarkdownAnchor() throws Exception {
        Path root = fixture();
        Files.writeString(
            root.resolve("docs/development.md"),
            "# Development\n\n[missing section](architecture.md#missing-section)\n"
        );
        assertState(
            GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()),
            "markdown-links",
            GovernanceCheck.State.FAIL
        );
    }

    private static void rejectsDuplicateContextOwnership() throws Exception {
        Path root = fixture();
        Files.writeString(
            root.resolve("docs/architecture.md"),
            "# Architecture\n\n## Domain Language\n\nDuplicate owner.\n"
        );
        assertState(
            GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()),
            "document-ownership",
            GovernanceCheck.State.FAIL
        );
    }

    private static void rejectsOversizedRootInstructions() throws Exception {
        Path root = fixture();
        Files.writeString(root.resolve("AGENTS.md"), "word ".repeat(1201));
        assertState(
            GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()),
            "instruction-budgets",
            GovernanceCheck.State.FAIL
        );
    }

    private static void rejectsMalformedAgentNote() throws Exception {
        Path root = fixture();
        Path note = root.resolve(".agents/notes/implemented/process/2026-08-14-example.md");
        Files.createDirectories(note.getParent());
        Files.writeString(note, "# Agent Note: Example\n\nStatus: proposed\n\n## Problem\nMismatch.\n");
        assertState(
            GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()),
            "agent-notes",
            GovernanceCheck.State.FAIL
        );
    }

    private static void requiresScopedInstructionsWhenSurfaceExists() throws Exception {
        Path root = fixture();
        Files.createDirectories(root.resolve("tooling/generator/src"));
        assertState(
            GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()),
            "tooling-instructions",
            GovernanceCheck.State.FAIL
        );
    }

    private static Path fixture() throws IOException {
        Path root = Files.createTempDirectory("governance-check-");
        write(root, "AGENTS.md", "# Repository Instructions\n\n[Context](CONTEXT.md)\n");
        Files.createSymbolicLink(root.resolve("CLAUDE.md"), Path.of("AGENTS.md"));
        write(root, "CONTEXT.md", "# Project Context\n");
        write(root, "docs/AGENTS.md", "# Documentation Instructions\n");
        write(root, "docs/architecture.md", "# Architecture\n");
        write(root, "docs/development.md", "# Development\n");
        write(root, ".agents/notes/AGENTS.md", "# Agent Note Instructions\n");
        return root;
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path target = root.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }

    private static void assertNoFailure(List<GovernanceCheck.Result> results) {
        boolean failed = results.stream().anyMatch(result -> result.state() == GovernanceCheck.State.FAIL);
        if (failed) {
            throw new AssertionError("Expected no failures: " + results);
        }
    }

    private static void assertState(
        List<GovernanceCheck.Result> results,
        String name,
        GovernanceCheck.State expected
    ) {
        GovernanceCheck.Result result = results.stream()
            .filter(candidate -> candidate.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing result: " + name));
        if (result.state() != expected) {
            throw new AssertionError(name + " expected " + expected + " but was " + result);
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
test_classes="$(mktemp -d)"
javac -d "$test_classes" scripts/tests/GovernanceCheckTest.java
```

Expected: compilation fails with `cannot find symbol` for `GovernanceCheck`.

- [ ] **Step 3: Implement the validator**

Create `scripts/governance/GovernanceCheck.java`:

```java
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class GovernanceCheck {
    enum Mode { FOCUSED, FULL }
    enum State { PASS, FAIL, UNAVAILABLE }
    record Result(String name, State state, String detail) {}

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^]]*]\\(([^)]+)\\)");
    private static final List<String> REQUIRED_DOCUMENTS = List.of(
        "AGENTS.md",
        "CONTEXT.md",
        "docs/AGENTS.md",
        "docs/architecture.md",
        "docs/development.md",
        ".agents/notes/AGENTS.md"
    );

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || !(args[0].equals("focused") || args[0].equals("full"))) {
            System.err.println("usage: GovernanceCheck <focused|full> [changed-path ...]");
            System.exit(2);
        }
        Mode mode = args[0].equals("focused") ? Mode.FOCUSED : Mode.FULL;
        List<String> changedPaths = List.of(args).subList(1, args.length);
        List<Result> results = run(Path.of(".").toAbsolutePath().normalize(), mode, changedPaths);
        results.forEach(result -> System.out.printf(
            "%s %-28s %s%n",
            result.state(),
            result.name(),
            result.detail()
        ));
        if (results.stream().anyMatch(result -> result.state() == State.FAIL)) {
            System.exit(1);
        }
    }

    static List<Result> run(Path root, Mode mode, List<String> changedPaths) throws IOException {
        List<Result> results = new ArrayList<>();
        results.add(checkAgentLayout(root));
        results.add(checkRequiredDocuments(root));
        results.add(checkMarkdownLinks(root));
        results.add(checkDocumentOwnership(root));
        results.add(checkInstructionBudgets(root));
        results.add(checkAgentNotes(root));

        List<Surface> surfaces = List.of(
            new Surface("platform-kit", "platform-kit/src", "platform-kit/AGENTS.md"),
            new Surface("tooling", "tooling/generator/src", "tooling/AGENTS.md"),
            new Surface("templates", "templates", "templates/AGENTS.md"),
            new Surface("products", "products", "products/AGENTS.md")
        );
        for (Surface surface : surfaces) {
            if (mode == Mode.FULL || changedPaths.isEmpty() || changedPaths.stream().anyMatch(surface::matches)) {
                results.add(checkSurfaceInstructions(root, surface));
            }
        }
        return results;
    }

    private static Result checkAgentLayout(Path root) throws IOException {
        Path agents = root.resolve("AGENTS.md");
        Path claude = root.resolve("CLAUDE.md");
        if (!Files.isRegularFile(agents)) {
            return fail("agent-layout", "AGENTS.md is missing");
        }
        if (!Files.isSymbolicLink(claude)) {
            return fail("agent-layout", "CLAUDE.md must be a symbolic link to AGENTS.md");
        }
        if (!Files.readSymbolicLink(claude).equals(Path.of("AGENTS.md"))) {
            return fail("agent-layout", "CLAUDE.md must target exactly AGENTS.md");
        }
        return pass("agent-layout", "Codex and Claude share AGENTS.md");
    }

    private static Result checkRequiredDocuments(Path root) {
        List<String> missing = REQUIRED_DOCUMENTS.stream()
            .filter(relative -> !Files.isRegularFile(root.resolve(relative)))
            .toList();
        return missing.isEmpty()
            ? pass("required-documents", "all governance documents exist")
            : fail("required-documents", "missing: " + String.join(", ", missing));
    }

    private static Result checkMarkdownLinks(Path root) throws IOException {
        List<String> errors = new ArrayList<>();
        for (Path markdown : markdownFiles(root)) {
            Matcher matcher = MARKDOWN_LINK.matcher(outsideFencedBlocks(Files.readString(markdown)));
            while (matcher.find()) {
                String destination = matcher.group(1).trim();
                if (destination.startsWith("http://") || destination.startsWith("https://")
                    || destination.startsWith("mailto:")) {
                    continue;
                }
                if (destination.startsWith("<") && destination.endsWith(">")) {
                    destination = destination.substring(1, destination.length() - 1);
                }
                String[] parts = destination.split("#", 2);
                String pathPart = parts[0];
                pathPart = URLDecoder.decode(pathPart, StandardCharsets.UTF_8);
                Path target = pathPart.isEmpty()
                    ? markdown
                    : markdown.getParent().resolve(pathPart).normalize();
                if (!target.startsWith(root) || !Files.exists(target)) {
                    errors.add(root.relativize(markdown) + " -> " + destination);
                } else if (parts.length == 2 && !parts[1].isEmpty()
                    && Files.isRegularFile(target) && target.toString().endsWith(".md")
                    && !hasAnchor(target, URLDecoder.decode(parts[1], StandardCharsets.UTF_8))) {
                    errors.add(root.relativize(markdown) + " -> missing anchor " + destination);
                }
            }
        }
        return errors.isEmpty()
            ? pass("markdown-links", "all relative document targets exist")
            : fail("markdown-links", String.join("; ", errors));
    }

    private static boolean hasAnchor(Path markdown, String expected) throws IOException {
        return outsideFencedBlocks(Files.readString(markdown)).lines()
            .filter(line -> line.matches("^#{1,6}\\s+.+"))
            .map(GovernanceCheck::headingAnchor)
            .anyMatch(expected::equals);
    }

    private static String headingAnchor(String heading) {
        String text = heading.replaceFirst("^#{1,6}\\s+", "")
            .toLowerCase(Locale.ROOT)
            .replace("`", "");
        StringBuilder slug = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isLetterOrDigit(character) || character == '-' || character == '_') {
                slug.append(character);
            } else if (Character.isWhitespace(character)) {
                slug.append('-');
            }
        }
        return slug.toString().replaceAll("-+", "-");
    }

    private static Result checkDocumentOwnership(Path root) throws IOException {
        Path context = root.resolve("CONTEXT.md");
        if (!Files.isRegularFile(context)) {
            return fail("document-ownership", "CONTEXT.md is missing");
        }
        List<String> ownedHeadings = List.of(
            "## Domain Language",
            "## Invariants",
            "## Delivery Sequence"
        );
        List<String> errors = new ArrayList<>();
        for (Path markdown : markdownFiles(root)) {
            if (markdown.equals(context)) {
                continue;
            }
            String content = outsideFencedBlocks(Files.readString(markdown));
            for (String heading : ownedHeadings) {
                if (content.lines().anyMatch(line -> line.equals(heading))) {
                    errors.add(root.relativize(markdown) + " duplicates CONTEXT.md heading " + heading);
                }
            }
        }
        return errors.isEmpty()
            ? pass("document-ownership", "CONTEXT.md owns domain headings")
            : fail("document-ownership", String.join("; ", errors));
    }

    private static String outsideFencedBlocks(String markdown) {
        StringBuilder result = new StringBuilder();
        boolean fenced = false;
        char fenceCharacter = 0;
        int fenceLength = 0;
        for (String line : markdown.split("\\R", -1)) {
            String stripped = line.stripLeading();
            if (!fenced && (stripped.startsWith("```") || stripped.startsWith("~~~"))) {
                fenced = true;
                fenceCharacter = stripped.charAt(0);
                fenceLength = leadingCount(stripped, fenceCharacter);
                continue;
            }
            if (fenced && !stripped.isEmpty() && stripped.charAt(0) == fenceCharacter
                && leadingCount(stripped, fenceCharacter) >= fenceLength) {
                fenced = false;
                continue;
            }
            if (!fenced) {
                result.append(line).append('\n');
            }
        }
        return result.toString();
    }

    private static Result checkInstructionBudgets(Path root) throws IOException {
        List<String> errors = new ArrayList<>();
        for (Path markdown : markdownFiles(root)) {
            if (!markdown.getFileName().toString().equals("AGENTS.md")) {
                continue;
            }
            Path relative = root.relativize(markdown);
            int budget = relative.toString().equals("AGENTS.md") ? 1200
                : relative.toString().equals("docs/AGENTS.md") ? 800
                : relative.toString().equals(".agents/notes/AGENTS.md") ? 800
                : 600;
            String content = outsideFencedBlocks(Files.readString(markdown)).trim();
            int words = content.isEmpty() ? 0 : content.split("\\s+").length;
            if (words > budget) {
                errors.add(relative + " has " + words + " words; budget is " + budget);
            }
        }
        return errors.isEmpty()
            ? pass("instruction-budgets", "all AGENTS.md files are within scope budgets")
            : fail("instruction-budgets", String.join("; ", errors));
    }

    private static int leadingCount(String value, char expected) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == expected) {
            count++;
        }
        return count;
    }

    private static Result checkAgentNotes(Path root) throws IOException {
        Path notesRoot = root.resolve(".agents/notes");
        if (!Files.isDirectory(notesRoot)) {
            return fail("agent-notes", ".agents/notes is missing");
        }
        List<String> errors = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(notesRoot)) {
            for (Path note : stream.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".md"))
                .filter(path -> !path.equals(notesRoot.resolve("AGENTS.md")))
                .sorted(Comparator.naturalOrder()).toList()) {
                validateNote(notesRoot, note, errors);
            }
        }
        return errors.isEmpty()
            ? pass("agent-notes", "all active notes match lifecycle and format")
            : fail("agent-notes", String.join("; ", errors));
    }

    private static void validateNote(Path notesRoot, Path note, List<String> errors) throws IOException {
        Path relative = notesRoot.relativize(note);
        if (relative.getNameCount() != 3) {
            errors.add(relative + " must be <lifecycle>/<class>/<date>-<topic>.md");
            return;
        }
        String lifecycle = relative.getName(0).toString();
        String decisionClass = relative.getName(1).toString();
        if (!List.of("proposed", "implemented", "rejected").contains(lifecycle)) {
            errors.add(relative + " has invalid lifecycle");
        }
        if (!List.of("architecture", "governance", "process", "testing").contains(decisionClass)) {
            errors.add(relative + " has invalid class");
        }
        if (lifecycle.equals("implemented") && decisionClass.equals("architecture")) {
            errors.add(relative + " must be promoted to docs/adr instead of implemented here");
        }
        if (!relative.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}-[a-z0-9-]+\\.md")) {
            errors.add(relative + " has invalid filename");
        }
        String content = Files.readString(note);
        String expectedStatus = "Status: " + lifecycle;
        if (!content.startsWith("# Agent Note: ") || !content.contains("\n\n" + expectedStatus)) {
            errors.add(relative + " status must match " + lifecycle);
        }
        for (String heading : List.of("## Problem", "## Alternatives considered")) {
            if (!content.contains(heading)) {
                errors.add(relative + " is missing " + heading);
            }
        }
        String lifecycleHeading = lifecycle.equals("implemented") ? "## Decision" : "## Proposal";
        if (!content.contains(lifecycleHeading)) {
            errors.add(relative + " is missing " + lifecycleHeading);
        }
        if (lifecycle.equals("implemented") && !content.contains("## Consequences")) {
            errors.add(relative + " is missing ## Consequences");
        }
        if (lifecycle.equals("proposed")
            && (!content.contains("## Acceptance criteria") || !content.contains("## Risks"))) {
            errors.add(relative + " must include acceptance criteria and risks");
        }
    }

    private static Result checkSurfaceInstructions(Path root, Surface surface) {
        Path source = root.resolve(surface.activationPath());
        if (!Files.exists(source)) {
            return unavailable(
                surface.name() + "-instructions",
                "activate when " + surface.activationPath() + " exists"
            );
        }
        Path instructions = root.resolve(surface.instructionsPath());
        return Files.isRegularFile(instructions)
            ? pass(surface.name() + "-instructions", surface.instructionsPath() + " exists")
            : fail(
                surface.name() + "-instructions",
                surface.activationPath() + " exists but " + surface.instructionsPath() + " is missing"
            );
    }

    private static List<Path> markdownFiles(Path root) throws IOException {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".md"))
                .filter(path -> !path.startsWith(root.resolve(".git")))
                .filter(path -> !path.startsWith(root.resolve(".idea")))
                .sorted()
                .toList();
        }
    }

    private static Result pass(String name, String detail) {
        return new Result(name, State.PASS, detail);
    }

    private static Result fail(String name, String detail) {
        return new Result(name, State.FAIL, detail);
    }

    private static Result unavailable(String name, String detail) {
        return new Result(name, State.UNAVAILABLE, detail);
    }

    private record Surface(String name, String activationPath, String instructionsPath) {
        boolean matches(String changedPath) {
            String normalized = changedPath.replace('\\', '/').toLowerCase(Locale.ROOT);
            return normalized.equals(name) || normalized.startsWith(name + "/");
        }
    }
}
```

- [ ] **Step 4: Add the stable command wrapper**

Create `scripts/check.sh`:

```sh
#!/bin/sh
set -eu

if [ "$#" -eq 0 ]; then
  echo "usage: ./scripts/check.sh <focused|full> [changed-path ...]" >&2
  exit 2
fi

mode=$1
shift

case "$mode" in
  focused|full) ;;
  *)
    echo "usage: ./scripts/check.sh <focused|full> [changed-path ...]" >&2
    exit 2
    ;;
esac

exec java scripts/governance/GovernanceCheck.java "$mode" "$@"
```

Run:

```bash
chmod +x scripts/check.sh
```

- [ ] **Step 5: Compile and run the validator tests**

Run:

```bash
test_classes="$(mktemp -d)"
javac -d "$test_classes" scripts/governance/GovernanceCheck.java scripts/tests/GovernanceCheckTest.java
java -cp "$test_classes" GovernanceCheckTest
```

Expected: `GovernanceCheckTest: PASS`.

- [ ] **Step 6: Verify the real repository fails before governance documents exist**

Run:

```bash
./scripts/check.sh full
```

Expected: exit `1`, including `FAIL agent-layout`, `FAIL required-documents`, and no false `PASS` for absent source surfaces.

- [ ] **Step 7: Commit the validator**

```bash
git add scripts/check.sh scripts/governance/GovernanceCheck.java scripts/tests/GovernanceCheckTest.java
git commit -m "build: add AI governance validator"
```

---

### Task 2: Establish the Authoritative Agent and Documentation Entry Points

**Files:**
- Create: `AGENTS.md`
- Create: `CLAUDE.md` as a symbolic link
- Create: `docs/AGENTS.md`
- Create: `docs/architecture.md`
- Create: `docs/development.md`
- Create: `.agents/notes/AGENTS.md`
- Modify: `CONTEXT.md`

**Interfaces:**
- Consumes: `./scripts/check.sh full` from Task 1.
- Produces: root instruction routing for Codex and Claude.
- Produces: current-state architecture and development workflow documents.
- Produces: explicit documentation ownership referenced by later scoped instructions.
- Produces: the Agent Note placement and format required by the root route.

- [ ] **Step 1: Add the root instructions**

Create `AGENTS.md`:

````markdown
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
````

- [ ] **Step 2: Add the Claude compatibility entry**

Run:

```bash
ln -s AGENTS.md CLAUDE.md
```

Expected: `readlink CLAUDE.md` prints `AGENTS.md`.

- [ ] **Step 3: Add documentation instructions**

Create `docs/AGENTS.md`:

````markdown
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
````

- [ ] **Step 4: Add the Agent Note instructions**

Create `.agents/notes/AGENTS.md`:

````markdown
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

Every note begins with:

```text
# Agent Note: <title>

Status: <lifecycle>
```

Every note contains `## Problem` and `## Alternatives considered`.

- A proposed note also contains `## Proposal`, `## Acceptance criteria`, and `## Risks`.
- An implemented note also contains `## Decision` and `## Consequences`, written as present reality.
- A rejected note retains `## Proposal`; its status and body explain the rejection.

Record genuine alternatives only. When an architecture proposal is accepted, move the durable decision into one ADR and remove or reject the proposal so there are not two active rationale owners.

## Verification

Run:

```sh
./scripts/check.sh focused .agents/notes
```
````

- [ ] **Step 5: Add the current-state architecture map**

Create `docs/architecture.md`:

````markdown
# Workbench Architecture

## Current state

The repository contains the domain context, an approved multi-project Workbench design, and implementation plans. It does not yet contain the planned Gradle builds, generator, templates, or reference Product. The [Phase 0 plan](superpowers/plans/2026-08-14-kmp-workbench-phase-0.md) is the executable source for creating that baseline; planned files are not current implementation.

## Target composition

The approved architecture separates five responsibilities:

```text
project.yaml
    ↓
tooling/manifest → tooling/generator → generated Product
                         ↑                  ↓
templates/ ─────────────┘             platform certification
                         ↑
platform-kit/ supplies build policy without selecting Product topology
```

- The Workbench root orchestrates generation and certification.
- `tooling/` parses the Manifest, resolves a deterministic template plan, renders into a temporary directory, verifies structure, and publishes atomically.
- `platform-kit/` supplies compiler, build, quality, and test policy through an included build.
- `templates/` contains only combinations proven by real Products and clean certification.
- `products/` contains independently buildable reference Products.

The approved detailed design is [KMP Multi-Project Workbench Design](superpowers/specs/2026-08-14-kmp-multi-project-workbench-design.md). [CONTEXT.md](../CONTEXT.md) owns domain definitions and invariants.

## Dependency direction

The Manifest selects current Product capabilities. The generator consumes the Manifest and templates. Generated Product builds may consume a locked platform-kit snapshot, but platform-kit never consumes Product source or decides which platforms exist. Platform applications consume narrow shared business interfaces through platform-owned Adapters; shared business code never imports platform UI.

## Lifecycle

Render and Certify are separate operations. Render proves a structurally valid, atomic file publication. Certify builds and tests the already rendered Product on each selected platform. Neither command may imply the guarantee of the other.

## Extension rules

A new Module requires an independently testable responsibility and a real consumer boundary. A new template requires proof from a maintained Product and clean certification. A new platform adds an independent application entry, Adapter rules, generator support, and certification evidence without widening platform-kit into a topology owner.
````

- [ ] **Step 6: Add the development workflow**

Create `docs/development.md`:

````markdown
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
````

- [ ] **Step 7: Link the context to its documentation owners**

Append to `CONTEXT.md`:

```markdown

## Documentation Map

- [Workbench architecture](docs/architecture.md) describes current composition, dependency direction, and extension points.
- [Development workflow](docs/development.md) defines local and CI verification responsibilities.
- [Repository instructions](AGENTS.md) route Codex, Claude, and contributors to the rules for a changed surface.
```

- [ ] **Step 8: Run the governance checks**

Run:

```bash
./scripts/check.sh full
git diff --check
```

Expected: `agent-layout`, `required-documents`, `markdown-links`, `document-ownership`, `instruction-budgets`, and `agent-notes` pass; `platform-kit-instructions`, `tooling-instructions`, `templates-instructions`, and `products-instructions` report `UNAVAILABLE`; the command exits `0`.

- [ ] **Step 9: Commit the entry points and documentation map**

```bash
git add AGENTS.md CLAUDE.md CONTEXT.md .agents/notes/AGENTS.md docs/AGENTS.md docs/architecture.md docs/development.md
git commit -m "docs: establish layered agent instructions"
```

---

### Task 3: Record the Governance Decision

**Files:**
- Create: `.agents/notes/implemented/process/2026-08-14-layered-ai-governance.md`

**Interfaces:**
- Consumes: note validation from `GovernanceCheck.checkAgentNotes`.
- Produces: the durable rationale for the governance baseline.

- [ ] **Step 1: Record the implemented governance decision**

Create `.agents/notes/implemented/process/2026-08-14-layered-ai-governance.md`:

```markdown
# Agent Note: Layered AI governance

Status: implemented

## Problem

The Workbench has domain and design documents but no stable instruction entry for coding agents, no scoped task routing, and no executable way to distinguish verified behavior from planned behavior. Separate Codex and Claude instructions would allow the same repository rule to drift.

## Decision

`AGENTS.md` is the authoritative instruction entry, and root `CLAUDE.md` links to it. Root instructions contain repository-wide invariants and route work to scoped instructions and owning documents. Architecture facts, accepted runtime decisions, governance rationale, reusable procedures, and task plans remain in separate owners.

`scripts/check.sh` exposes focused and full verification. The governance validator reports absent future implementation surfaces as unavailable. When a source surface appears, its nearest scoped `AGENTS.md` becomes mandatory in the same change.

## Alternatives considered

**One root instruction file:** This begins simply but forces every agent session to load unrelated product, platform, generator, and template rules as the repository grows.

**Separate Codex and Claude rules:** Native-looking files for each tool duplicate standing orders and create an avoidable consistency problem.

**Manifest-generated governance:** The repository has only one planned reference Product and no repeated governance metadata from which to design a stable schema.

## Consequences

Agents receive smaller, relevant instruction sets and use the same executable checks as CI. New implementation directories must ship scoped instructions with their first source. Maintainers must preserve one owner per fact and update validators when a prose invariant becomes mechanically enforceable. Governance manifest generation remains deferred until another real Product demonstrates repetition.
```

- [ ] **Step 2: Verify the valid note passes**

Run:

```bash
./scripts/check.sh focused .agents/notes
```

Expected: `PASS agent-notes` and exit `0`.

- [ ] **Step 3: Prove lifecycle mismatches are rejected**

Run:

```bash
test_classes="$(mktemp -d)"
javac -d "$test_classes" scripts/governance/GovernanceCheck.java scripts/tests/GovernanceCheckTest.java
java -cp "$test_classes" GovernanceCheckTest
```

Expected: `GovernanceCheckTest: PASS`, including the malformed-note regression.

- [ ] **Step 4: Commit the decision record**

```bash
git add .agents/notes/implemented/process/2026-08-14-layered-ai-governance.md
git commit -m "docs: govern agent decision records"
```

---

### Task 4: Put the Full Governance Check in CI

**Files:**
- Create: `.github/workflows/governance.yml`
- Modify: `docs/development.md`

**Interfaces:**
- Consumes: `./scripts/check.sh full` and Java 17.
- Produces: required GitHub Actions job `governance`.
- Produces: CI evidence that uses the same repository command as local agents.

- [ ] **Step 1: Add the CI workflow**

Create `.github/workflows/governance.yml`:

```yaml
name: Governance

on:
  pull_request:
  push:
    branches:
      - master

permissions:
  contents: read

jobs:
  governance:
    name: Governance
    runs-on: ubuntu-latest
    steps:
      - name: Check out repository
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "17"

      - name: Run governance checks
        run: ./scripts/check.sh full

      - name: Compile and run validator regression tests
        shell: bash
        run: |
          test_classes="$(mktemp -d)"
          javac -d "$test_classes" scripts/governance/GovernanceCheck.java scripts/tests/GovernanceCheckTest.java
          java -cp "$test_classes" GovernanceCheckTest
```

- [ ] **Step 2: Document the concrete CI job**

Replace the `## CI responsibility` section in `docs/development.md` with:

```markdown
## CI responsibility

The `Governance` workflow runs `./scripts/check.sh full` and the validator regression test on pull requests and pushes to `master`. Workflow YAML invokes repository commands and does not duplicate validation logic.

Platform build and certification workflows are added by the owning implementation plans. CI owns their complete matrix for currently supported hosts and platforms.
```

- [ ] **Step 3: Run the exact CI commands locally**

Run:

```bash
./scripts/check.sh full
test_classes="$(mktemp -d)"
javac -d "$test_classes" scripts/governance/GovernanceCheck.java scripts/tests/GovernanceCheckTest.java
java -cp "$test_classes" GovernanceCheckTest
git diff --check
```

Expected: governance exits `0`, the regression suite prints `GovernanceCheckTest: PASS`, and `git diff --check` prints nothing.

- [ ] **Step 4: Commit the CI gate**

```bash
git add .github/workflows/governance.yml docs/development.md
git commit -m "ci: enforce AI governance baseline"
```

---

### Task 5: Bind Scoped Instructions to the Phase 0 Delivery Plan

**Files:**
- Modify: `docs/superpowers/plans/2026-08-14-kmp-workbench-phase-0.md`

**Interfaces:**
- Consumes: surface activation rules from `GovernanceCheck`.
- Produces: Phase 0 tasks that create `platform-kit/AGENTS.md`, `tooling/AGENTS.md`, `templates/AGENTS.md`, and `products/AGENTS.md` with their real source surfaces.
- Produces: exact focused governance commands for each Phase 0 surface.

- [ ] **Step 1: Add governance to the Phase 0 global constraints**

Add these lines to `## Global Constraints`:

```markdown
- Root and scoped AI governance follows [AI Governance Design](../specs/2026-08-14-ai-governance-design.md).
- A new `platform-kit/`, `tooling/`, `templates/`, or `products/` source surface includes its scoped `AGENTS.md` in the same task.
- Every task runs `./scripts/check.sh focused <changed-path>...`; Phase 0 completion runs `./scripts/check.sh full`.
```

- [ ] **Step 2: Extend Task 2 with platform-kit instructions**

Add `platform-kit/AGENTS.md` to Task 2 `**Files:**`, then add this step before Task 2's test execution:

````markdown
- [ ] **Step: Add platform-kit instructions**

Create `platform-kit/AGENTS.md`:

```markdown
# Platform Kit Instructions

These rules supplement the repository-wide [instructions](../AGENTS.md). Read [Workbench architecture](../docs/architecture.md) before changing build policy.

- Platform-kit owns compiler, build, quality, and test policy; it never selects Product platforms or Module topology.
- Convention plugins configure an already selected official Kotlin or Android plugin and must not apply platform plugins implicitly.
- Toolchain values come from the root version catalog; do not duplicate dependency versions.
- Test policy with Gradle TestKit and focused plugin tests before running affected generated-Product certification.

Run `../scripts/check.sh focused platform-kit` and `../gradlew -p platform-kit test` for platform-kit changes.
```
````

- [ ] **Step 3: Extend Task 3 with tooling instructions**

Add `tooling/AGENTS.md` to Task 3 `**Files:**`, then add this step before Task 3's test execution:

````markdown
- [ ] **Step: Add tooling instructions**

Create `tooling/AGENTS.md`:

```markdown
# Tooling Instructions

These rules supplement the repository-wide [instructions](../AGENTS.md). Read [Workbench architecture](../docs/architecture.md) before changing the Manifest or generator.

- Validate the complete Manifest before writing Product files.
- Keep parsing, validation, resolution, rendering, structural verification, and publication as independently testable responsibilities.
- Render into a sibling temporary directory and publish atomically only after structural verification.
- Never overwrite a non-empty destination or claim that Render certified a platform build.
- Reject unsupported combinations explicitly; do not silently drop a requested platform or capability.

Run `../scripts/check.sh focused tooling` and `../gradlew :tooling:generator:test` for tooling changes.
```
````

- [ ] **Step 4: Extend Task 5 with template instructions**

Add `templates/AGENTS.md` to Task 5 `**Files:**`, then add this step before Task 5's template content steps:

````markdown
- [ ] **Step: Add template instructions**

Create `templates/AGENTS.md`:

```markdown
# Template Instructions

These rules supplement the repository-wide [instructions](../AGENTS.md). Templates are promoted only after a maintained Product proves the structure and a clean generated copy is certified.

- Keep template output deterministic for the same Manifest, template catalog, and toolchain lock.
- Use explicit replacement tokens and verify that no unresolved token reaches published output.
- Emit only selected platforms and capabilities.
- Do not place Product-specific roadmap phases or unproven shared abstractions into templates.
- A rendered tree proves structure; only Certify proves supported platform builds.

Run `../scripts/check.sh focused templates`, generator tests, and the certification commands for every claimed template combination.
```
````

- [ ] **Step 5: Extend Task 6 with Product instructions**

Task 6 first publishes `products/daily-board`, so add `products/AGENTS.md` to Task 6 `**Files:**`, then add this step before rendering the reference Product:

````markdown
- [ ] **Step: Add Product instructions**

Create `products/AGENTS.md`:

```markdown
# Product Instructions

These rules supplement the repository-wide [instructions](../AGENTS.md). Every Product is independently buildable and may add a Product-local `AGENTS.md` when it gains rules not shared by sibling Products.

- Platform applications own UI, navigation, lifecycle, accessibility, theme, permissions, and presentation state.
- Shared Kotlin owns business rules and narrow platform-facing commands, snapshots, failures, and event streams; it never owns platform UI.
- Unselected platforms do not appear in the Product settings or Gradle Module graph.
- Product source does not reach into Workbench tooling internals; exported Products consume only their locked platform-kit and generated metadata.
- Preserve local-first behavior until a Product explicitly selects and implements backend capabilities.

Run `../scripts/check.sh focused products` plus the Product's selected platform tests and certification commands.
```
````

- [ ] **Step 6: Make Task 7 publish current architecture**

Add `docs/architecture.md` to Task 7 `**Files:**`, then add this step after clean Product certification:

`````markdown
- [ ] **Step: Publish the implemented Phase 0 architecture**

In `docs/architecture.md`, replace `## Current state` and `## Target composition` with:

````markdown
## Current state

Phase 0 implements the root orchestration build, target-neutral platform-kit, Manifest validation, structural Render transaction, Android+iOS daily-board template, maintained reference Product, and clean certification entry point. Desktop, Web/Wasm, persistence, backend, authentication, synchronization, migration, and standalone CLI export remain outside the implemented baseline.

## Phase 0 composition

```text
project.yaml
    ↓
tooling/generator → products/daily-board
        ↑                    ↓
templates/           Android and iOS certification
        ↑
platform-kit supplies build policy without selecting Product topology
```

- The Workbench root orchestrates generation and certification.
- `tooling/generator` validates the Manifest, resolves the template, renders into a sibling temporary directory, verifies structure, and publishes atomically.
- `platform-kit/` supplies compiler, build, quality, and test policy through an included build.
- `templates/daily-board-base/` owns the certified Android+iOS template.
- `products/daily-board/` is the independently buildable reference Product.

The approved detailed design is [KMP Multi-Project Workbench Design](superpowers/specs/2026-08-14-kmp-multi-project-workbench-design.md). [CONTEXT.md](../CONTEXT.md) owns domain definitions and invariants.
````
`````

Include `docs/architecture.md` in Task 7's commit so shipped composition and certification evidence cannot diverge.

- [ ] **Step 7: Add governance to the Phase 0 completion gate**

Add this command before the existing Phase 0 completion commands:

```bash
./scripts/check.sh full
```

Expected: every governance check reports `PASS`; no scoped instruction remains `UNAVAILABLE` after all four source surfaces exist.

- [ ] **Step 8: Verify links and plan consistency**

Run:

```bash
./scripts/check.sh focused docs/superpowers/plans/2026-08-14-kmp-workbench-phase-0.md
rg -n 'AGENTS.md|scripts/check.sh' docs/superpowers/plans/2026-08-14-kmp-workbench-phase-0.md
git diff --check
```

Expected: governance exits `0`; `rg` shows all four scoped instruction files and the full completion command; `git diff --check` prints nothing.

- [ ] **Step 9: Commit the Phase 0 integration**

```bash
git add docs/superpowers/plans/2026-08-14-kmp-workbench-phase-0.md
git commit -m "docs: bind Phase 0 to AI governance"
```

---

### Task 6: Verify the Complete Governance Baseline

**Files:**
- Verify only; no planned file changes.

**Interfaces:**
- Consumes: all artifacts from Tasks 1–5.
- Produces: final local evidence for the governance baseline.

- [ ] **Step 1: Run all governance regression tests**

```bash
test_classes="$(mktemp -d)"
javac -d "$test_classes" scripts/governance/GovernanceCheck.java scripts/tests/GovernanceCheckTest.java
java -cp "$test_classes" GovernanceCheckTest
```

Expected: `GovernanceCheckTest: PASS`.

- [ ] **Step 2: Run focused checks for every currently governed surface**

```bash
./scripts/check.sh focused AGENTS.md docs .agents/notes scripts .github
```

Expected: all baseline governance checks pass; absent KMP implementation surfaces report `UNAVAILABLE` only when selected by the validator.

- [ ] **Step 3: Run the full repository governance check**

```bash
./scripts/check.sh full
```

Expected: exit `0`; baseline checks pass; absent `platform-kit`, `tooling`, `templates`, and `products` source surfaces report their activation conditions as `UNAVAILABLE`.

- [ ] **Step 4: Verify repository hygiene and commit boundaries**

```bash
git diff --check
git status --short
git log --oneline -6
```

Expected: `git diff --check` prints nothing; `.idea/` remains untracked and unstaged; the governance work is split into validator, instructions, notes, CI, and Phase 0 integration commits.

- [ ] **Step 5: Record completion only if every required command passed**

Do not create an empty completion commit. Report the exact commands run, their outcomes, and the four explicitly unavailable implementation surfaces. When Phase 0 later creates those surfaces, its completion gate must show all four scoped-instruction checks as `PASS`.
