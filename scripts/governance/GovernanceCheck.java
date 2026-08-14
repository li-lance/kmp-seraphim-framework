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
