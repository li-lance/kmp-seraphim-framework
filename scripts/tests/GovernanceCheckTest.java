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
