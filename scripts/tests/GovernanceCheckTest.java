import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class GovernanceCheckTest {
    public static void main(String[] args) throws Exception {
        acceptsValidBaseline();
        unavailableSurfacesNamePhaseAndActivation();
        focusedModeNormalizesChangedPaths();
        wrapperRunsFromNestedDirectory();
        rejectsRegularClaudeFile();
        rejectsBrokenMarkdownLink();
        rejectsBrokenReferenceLink();
        acceptsReferenceLinksAndOptionalTitles();
        acceptsBalancedParenthesesInDestinations();
        preservesLiteralPlusInRelativePaths();
        ignoresLinksInsideCodeFences();
        preservesLineNumbersAfterCodeFences();
        ignoresLinksInsideInlineCode();
        rejectsBrokenMarkdownAnchor();
        acceptsSetextHeadingAnchors();
        supportsDuplicateHeadingSuffixes();
        avoidsGeneratedAnchorCollisions();
        rejectsMissingDuplicateHeadingSuffix();
        rejectsMalformedInlineLinkSyntax();
        rejectsDuplicateContextOwnership();
        rejectsOversizedRootInstructions();
        rejectsMalformedAgentNote();
        rejectsInexactAgentNoteOpeningLine();
        rejectsAgentNoteStatusOnlyLater();
        rejectsNearMatchAgentNoteHeading();
        rejectsFencedAgentNoteHeadingExample();
        rejectsOutOfOrderAgentNoteHeadings();
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

    private static void unavailableSurfacesNamePhaseAndActivation() throws Exception {
        Path root = fixture();
        List<GovernanceCheck.Result> results =
            GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of());
        assertDetail(
            results,
            "platform-kit-instructions",
            "Phase 0; activate when platform-kit/src exists"
        );
        assertDetail(
            results,
            "tooling-instructions",
            "Phase 0; activate when tooling/generator/src exists"
        );
        assertDetail(
            results,
            "templates-instructions",
            "Phase 0; activate when templates exists"
        );
        assertDetail(
            results,
            "products-instructions",
            "Phase 0; activate when products exists"
        );
    }

    private static void wrapperRunsFromNestedDirectory() throws Exception {
        Path repositoryRoot = Path.of(".").toAbsolutePath().normalize();
        Process process = new ProcessBuilder(
            repositoryRoot.resolve("scripts/check.sh").toString(),
            "focused",
            "docs/development.md"
        )
            .directory(repositoryRoot.resolve("docs").toFile())
            .redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0 || !output.contains("PASS agent-layout")) {
            throw new AssertionError(
                "Nested wrapper invocation failed with exit " + exitCode + ":\n" + output
            );
        }
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

    private static void rejectsBrokenReferenceLink() throws Exception {
        Path root = fixture();
        Files.writeString(
            root.resolve("docs/development.md"),
            "# Development\n\n[Architecture][architecture-ref]\n\n" +
                "[architecture-ref]: missing.md \"Architecture\"\n"
        );
        assertState(
            GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()),
            "markdown-links",
            GovernanceCheck.State.FAIL
        );
    }

    private static void preservesLineNumbersAfterCodeFences() throws Exception {
        Path root = fixture();
        Files.writeString(
            root.resolve("docs/development.md"),
            "# Development\n\n```markdown\n[example](missing.md)\n```\n\n[broken](missing.md)\n"
        );
        assertDetailContains(
            GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()),
            "markdown-links",
            "docs/development.md:7 -> missing.md"
        );
    }

    private static void acceptsReferenceLinksAndOptionalTitles() throws Exception {
        Path root = fixture();
        Files.writeString(
            root.resolve("docs/development.md"),
            "# Development\n\n[Architecture][architecture-ref] and [Architecture][].\n\n" +
                "[architecture-ref]: architecture.md \"Architecture title\"\n" +
                "[architecture]: <architecture.md> 'Collapsed title'\n"
        );
        assertNoFailure(GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()));
    }

    private static void acceptsBalancedParenthesesInDestinations() throws Exception {
        Path root = fixture();
        write(root, "docs/guide(v1).md", "# Guide\n");
        write(root, "docs/guide (v2).md", "# Guide\n");
        Files.writeString(
            root.resolve("docs/development.md"),
            "# Development\n\n[one](guide(v1).md \"Title\") " +
                "[two](<guide (v2).md> 'Other title')\n"
        );
        assertNoFailure(GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()));
    }

    private static void ignoresLinksInsideInlineCode() throws Exception {
        Path root = fixture();
        Files.writeString(
            root.resolve("docs/development.md"),
            "# Development\n\nUse `[fixture](missing.md)` as an example.\n"
        );
        assertNoFailure(GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()));
    }

    private static void preservesLiteralPlusInRelativePaths() throws Exception {
        Path root = fixture();
        write(root, "docs/c++.md", "# C++\n");
        Files.writeString(
            root.resolve("docs/development.md"),
            "# Development\n\n[C++](c++.md)\n"
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

    private static void acceptsSetextHeadingAnchors() throws Exception {
        Path root = fixture();
        Files.writeString(
            root.resolve("docs/architecture.md"),
            "Architecture\n============\n\nExtension Points\n----------------\n"
        );
        Files.writeString(
            root.resolve("docs/development.md"),
            "# Development\n\n[extension](architecture.md#extension-points)\n"
        );
        assertNoFailure(GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()));
    }

    private static void supportsDuplicateHeadingSuffixes() throws Exception {
        Path root = fixture();
        Files.writeString(
            root.resolve("docs/architecture.md"),
            "# Architecture\n\n## Repeat\n\n## Repeat\n"
        );
        Files.writeString(
            root.resolve("docs/development.md"),
            "# Development\n\n[second](architecture.md#repeat-1)\n"
        );
        assertNoFailure(GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()));
    }

    private static void rejectsMissingDuplicateHeadingSuffix() throws Exception {
        Path root = fixture();
        Files.writeString(
            root.resolve("docs/architecture.md"),
            "# Architecture\n\n## Repeat\n\n## Repeat\n"
        );
        Files.writeString(
            root.resolve("docs/development.md"),
            "# Development\n\n[third](architecture.md#repeat-2)\n"
        );
        assertState(
            GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()),
            "markdown-links",
            GovernanceCheck.State.FAIL
        );
    }

    private static void avoidsGeneratedAnchorCollisions() throws Exception {
        Path root = fixture();
        Files.writeString(
            root.resolve("docs/architecture.md"),
            "# Architecture\n\n## Repeat\n\n## Repeat\n\n## Repeat-1\n"
        );
        Files.writeString(
            root.resolve("docs/development.md"),
            "# Development\n\n[collision-free](architecture.md#repeat-1-1)\n"
        );
        assertNoFailure(GovernanceCheck.run(root, GovernanceCheck.Mode.FULL, List.of()));
    }

    private static void rejectsMalformedInlineLinkSyntax() throws Exception {
        Path root = fixture();
        Files.writeString(
            root.resolve("docs/development.md"),
            "# Development\n\n[bad](architecture.md \"unterminated)\n"
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

    private static void rejectsAgentNoteStatusOnlyLater() throws Exception {
        Path root = fixture();
        writeAgentNote(
            root,
            "# Agent Note: Example\n\nIntro before status.\n\nStatus: implemented\n\n" +
                implementedNoteSections()
        );
        assertAgentNotesFail(root);
    }

    private static void rejectsInexactAgentNoteOpeningLine() throws Exception {
        Path root = fixture();
        writeAgentNote(
            root,
            "# Agent Note: Example  \n\nStatus: implemented\n\n" + implementedNoteSections()
        );
        assertAgentNotesFail(root);
    }

    private static void rejectsNearMatchAgentNoteHeading() throws Exception {
        Path root = fixture();
        writeAgentNote(
            root,
            "# Agent Note: Example\n\nStatus: implemented\n\n" +
                "## Problematic\n\nNot the required heading.\n\n" +
                "## Decision\n\nDecision.\n\n" +
                "## Alternatives considered\n\nAlternative.\n\n" +
                "## Consequences\n\nConsequence.\n"
        );
        assertAgentNotesFail(root);
    }

    private static void rejectsFencedAgentNoteHeadingExample() throws Exception {
        Path root = fixture();
        writeAgentNote(
            root,
            "# Agent Note: Example\n\nStatus: implemented\n\n" +
                "```markdown\n## Problem\n```\n\n" +
                "## Decision\n\nDecision.\n\n" +
                "## Alternatives considered\n\nAlternative.\n\n" +
                "## Consequences\n\nConsequence.\n"
        );
        assertAgentNotesFail(root);
    }

    private static void rejectsOutOfOrderAgentNoteHeadings() throws Exception {
        Path root = fixture();
        writeAgentNote(
            root,
            "# Agent Note: Example\n\nStatus: implemented\n\n" +
                "## Decision\n\nDecision.\n\n" +
                "## Problem\n\nProblem.\n\n" +
                "## Alternatives considered\n\nAlternative.\n\n" +
                "## Consequences\n\nConsequence.\n"
        );
        assertAgentNotesFail(root);
    }

    private static String implementedNoteSections() {
        return "## Problem\n\nProblem.\n\n" +
            "## Decision\n\nDecision.\n\n" +
            "## Alternatives considered\n\nAlternative.\n\n" +
            "## Consequences\n\nConsequence.\n";
    }

    private static void writeAgentNote(Path root, String content) throws IOException {
        write(
            root,
            ".agents/notes/implemented/process/2026-08-14-example.md",
            content
        );
    }

    private static void assertAgentNotesFail(Path root) throws IOException {
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

    private static void focusedModeNormalizesChangedPaths() throws Exception {
        Path root = fixture();
        Files.createDirectories(root.resolve("tooling/generator/src"));
        for (String changedPath : List.of(
            "tooling/generator/src/Main.kt",
            "./tooling/generator/src/Main.kt",
            root.resolve("tooling/generator/src/Main.kt").toString()
        )) {
            assertState(
                GovernanceCheck.run(root, GovernanceCheck.Mode.FOCUSED, List.of(changedPath)),
                "tooling-instructions",
                GovernanceCheck.State.FAIL
            );
        }
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

    private static void assertDetail(
        List<GovernanceCheck.Result> results,
        String name,
        String expected
    ) {
        GovernanceCheck.Result result = results.stream()
            .filter(candidate -> candidate.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing result: " + name));
        if (!result.detail().equals(expected)) {
            throw new AssertionError(name + " expected detail '" + expected + "' but was " + result);
        }
    }

    private static void assertDetailContains(
        List<GovernanceCheck.Result> results,
        String name,
        String expectedFragment
    ) {
        GovernanceCheck.Result result = results.stream()
            .filter(candidate -> candidate.name().equals(name))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Missing result: " + name));
        if (!result.detail().contains(expectedFragment)) {
            throw new AssertionError(
                name + " expected detail containing '" + expectedFragment + "' but was " + result
            );
        }
    }
}
