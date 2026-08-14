import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class GovernanceCheck {
    enum Mode { FOCUSED, FULL }
    enum State { PASS, FAIL, UNAVAILABLE }
    record Result(String name, State state, String detail) {}

    private static final Pattern ATX_HEADING = Pattern.compile(
        "^ {0,3}(#{1,6})(?:[ \\t]+|$)(.*)$"
    );
    private static final Pattern SETEXT_HEADING = Pattern.compile("^ {0,3}(?:=+|-+)[ \\t]*$");
    private static final Pattern URI_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*");
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
        Path normalizedRoot = root.toAbsolutePath().normalize();
        List<String> normalizedChangedPaths = changedPaths.stream()
            .map(changedPath -> normalizeChangedPath(normalizedRoot, changedPath))
            .toList();
        root = normalizedRoot;
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
            if (mode == Mode.FULL || normalizedChangedPaths.isEmpty()
                || normalizedChangedPaths.stream().anyMatch(surface::matches)) {
                results.add(checkSurfaceInstructions(root, surface));
            }
        }
        return results;
    }

    private static String normalizeChangedPath(Path root, String changedPath) {
        Path supplied = Path.of(changedPath.replace('\\', '/'));
        Path absolute = supplied.isAbsolute()
            ? supplied.normalize()
            : root.resolve(supplied).normalize();
        return absolute.startsWith(root)
            ? root.relativize(absolute).toString().replace('\\', '/')
            : absolute.toString().replace('\\', '/');
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
            String content = outsideFencedBlocks(Files.readString(markdown));
            for (MarkdownDestination parsed : markdownDestinations(content, markdown, root, errors)) {
                String destination = parsed.value();
                if (URI_SCHEME.matcher(destination).matches() || destination.startsWith("//")) {
                    continue;
                }
                String[] parts = destination.split("#", 2);
                String pathPart = parts[0];
                pathPart = percentDecode(pathPart);
                Path target = pathPart.isEmpty()
                    ? markdown
                    : markdown.getParent().resolve(pathPart).normalize();
                if (!target.startsWith(root) || !Files.exists(target)) {
                    errors.add(root.relativize(markdown) + ":" + parsed.line() + " -> " + destination);
                } else if (parts.length == 2 && !parts[1].isEmpty()
                    && Files.isRegularFile(target) && target.toString().endsWith(".md")
                    && !hasAnchor(target, percentDecode(parts[1]))) {
                    errors.add(root.relativize(markdown) + ":" + parsed.line()
                        + " -> missing anchor " + destination);
                }
            }
        }
        return errors.isEmpty()
            ? pass("markdown-links", "all supported relative Markdown links and anchors resolve")
            : fail("markdown-links", String.join("; ", errors));
    }

    private static String percentDecode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static boolean hasAnchor(Path markdown, String expected) throws IOException {
        return headingAnchors(outsideFencedBlocks(Files.readString(markdown))).contains(expected);
    }

    private static Set<String> headingAnchors(String markdown) {
        String[] lines = markdown.split("\\R", -1);
        List<String> headings = new ArrayList<>();
        for (int index = 0; index < lines.length; index++) {
            Matcher atx = ATX_HEADING.matcher(lines[index]);
            if (atx.matches()) {
                String heading = atx.group(2).strip();
                heading = heading.replaceFirst("[ \\t]+#+[ \\t]*$", "");
                headings.add(heading);
            } else if (index + 1 < lines.length && !lines[index].isBlank()
                && SETEXT_HEADING.matcher(lines[index + 1]).matches()) {
                headings.add(lines[index].strip());
                index++;
            }
        }
        Set<String> anchors = new HashSet<>();
        for (String heading : headings) {
            String base = headingAnchor(heading);
            String candidate = base;
            int suffix = 0;
            while (anchors.contains(candidate)) {
                candidate = base + "-" + ++suffix;
            }
            anchors.add(candidate);
        }
        return anchors;
    }

    private static String headingAnchor(String text) {
        text = text.toLowerCase(Locale.ROOT).replace("`", "").strip();
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

    private static List<MarkdownDestination> markdownDestinations(
        String markdown,
        Path source,
        Path root,
        List<String> errors
    ) {
        String masked = maskInlineCode(markdown);
        String[] lines = masked.split("\\R", -1);
        Map<String, MarkdownDestination> definitions = new LinkedHashMap<>();
        Set<Integer> definitionLines = new HashSet<>();
        String relative = root.relativize(source).toString();

        for (int index = 0; index < lines.length; index++) {
            ReferenceDefinition definition = parseReferenceDefinition(lines[index], index + 1);
            if (definition == null) {
                continue;
            }
            definitionLines.add(index);
            if (definition.error() != null) {
                errors.add(relative + ":" + (index + 1) + " -> " + definition.error());
            } else {
                definitions.putIfAbsent(definition.label(), definition.destination());
            }
        }

        List<MarkdownDestination> destinations = new ArrayList<>(definitions.values());
        for (int index = 0; index < lines.length; index++) {
            if (!definitionLines.contains(index)) {
                scanInlineLinks(lines[index], index + 1, relative, definitions, destinations, errors);
            }
        }
        return destinations;
    }

    private static ReferenceDefinition parseReferenceDefinition(String line, int lineNumber) {
        int start = 0;
        while (start < line.length() && start < 4 && line.charAt(start) == ' ') {
            start++;
        }
        if (start > 3 || start >= line.length() || line.charAt(start) != '[') {
            return null;
        }
        int close = findClosingBracket(line, start);
        if (close < 0 || close + 1 >= line.length() || line.charAt(close + 1) != ':') {
            return null;
        }
        String label = normalizeReferenceLabel(line.substring(start + 1, close));
        DestinationParse parsed = parseDestination(line.substring(close + 2));
        if (label.isEmpty()) {
            return new ReferenceDefinition(label, null, "reference definition has an empty label");
        }
        if (parsed.error() != null) {
            return new ReferenceDefinition(label, null, "malformed reference definition: " + parsed.error());
        }
        return new ReferenceDefinition(
            label,
            new MarkdownDestination(parsed.destination(), lineNumber),
            null
        );
    }

    private static void scanInlineLinks(
        String line,
        int lineNumber,
        String source,
        Map<String, MarkdownDestination> definitions,
        List<MarkdownDestination> destinations,
        List<String> errors
    ) {
        for (int index = 0; index < line.length(); index++) {
            if (line.charAt(index) != '[' || isEscaped(line, index)) {
                continue;
            }
            int close = findClosingBracket(line, index);
            if (close < 0) {
                continue;
            }
            String text = line.substring(index + 1, close);
            int next = close + 1;
            if (next < line.length() && line.charAt(next) == '(') {
                int end = findClosingParenthesis(line, next);
                if (end < 0) {
                    errors.add(source + ":" + lineNumber + " -> malformed inline link: unclosed destination");
                    index = close;
                    continue;
                }
                DestinationParse parsed = parseDestination(line.substring(next + 1, end));
                if (parsed.error() != null) {
                    errors.add(source + ":" + lineNumber + " -> malformed inline link: " + parsed.error());
                } else {
                    destinations.add(new MarkdownDestination(parsed.destination(), lineNumber));
                }
                index = end;
            } else if (next < line.length() && line.charAt(next) == '[') {
                int referenceEnd = findClosingBracket(line, next);
                if (referenceEnd < 0) {
                    errors.add(source + ":" + lineNumber + " -> malformed reference link: unclosed label");
                    index = close;
                    continue;
                }
                String explicit = line.substring(next + 1, referenceEnd);
                String label = normalizeReferenceLabel(explicit.isEmpty() ? text : explicit);
                MarkdownDestination destination = definitions.get(label);
                if (destination == null) {
                    errors.add(source + ":" + lineNumber + " -> undefined reference link [" + label + "]");
                } else {
                    destinations.add(destination.withLine(lineNumber));
                }
                index = referenceEnd;
            } else {
                MarkdownDestination destination = definitions.get(normalizeReferenceLabel(text));
                if (destination != null) {
                    destinations.add(destination.withLine(lineNumber));
                }
                index = close;
            }
        }
    }

    private static DestinationParse parseDestination(String expression) {
        int index = skipWhitespace(expression, 0);
        if (index == expression.length()) {
            return new DestinationParse(null, "destination is empty");
        }
        String destination;
        if (expression.charAt(index) == '<') {
            int end = findUnescaped(expression, '>', index + 1);
            if (end < 0) {
                return new DestinationParse(null, "angle-bracket destination is unclosed");
            }
            destination = expression.substring(index + 1, end);
            index = end + 1;
        } else {
            int start = index;
            int depth = 0;
            while (index < expression.length()) {
                char character = expression.charAt(index);
                if (character == '\\' && index + 1 < expression.length()) {
                    index += 2;
                    continue;
                }
                if (character == '(') {
                    depth++;
                } else if (character == ')') {
                    if (depth == 0) {
                        return new DestinationParse(null, "destination has an unmatched parenthesis");
                    }
                    depth--;
                } else if (Character.isWhitespace(character) && depth == 0) {
                    break;
                }
                index++;
            }
            if (depth != 0) {
                return new DestinationParse(null, "destination has unbalanced parentheses");
            }
            destination = expression.substring(start, index);
        }
        if (destination.isEmpty()) {
            return new DestinationParse(null, "destination is empty");
        }
        index = skipWhitespace(expression, index);
        if (index < expression.length()) {
            char opener = expression.charAt(index);
            char closer = opener == '(' ? ')' : opener;
            if (!(opener == '\"' || opener == '\'' || opener == '(')) {
                return new DestinationParse(null, "unsupported text after destination");
            }
            int titleEnd = findUnescaped(expression, closer, index + 1);
            if (titleEnd < 0) {
                return new DestinationParse(null, "optional title is unclosed");
            }
            index = skipWhitespace(expression, titleEnd + 1);
            if (index != expression.length()) {
                return new DestinationParse(null, "unsupported text after optional title");
            }
        }
        return new DestinationParse(unescapeMarkdown(destination), null);
    }

    private static int findClosingBracket(String value, int open) {
        int depth = 1;
        for (int index = open + 1; index < value.length(); index++) {
            if (isEscaped(value, index)) {
                continue;
            }
            if (value.charAt(index) == '[') {
                depth++;
            } else if (value.charAt(index) == ']' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static int findClosingParenthesis(String value, int open) {
        int depth = 1;
        char quote = 0;
        for (int index = open + 1; index < value.length(); index++) {
            if (isEscaped(value, index)) {
                continue;
            }
            char character = value.charAt(index);
            if (quote != 0) {
                if (character == quote) {
                    quote = 0;
                }
            } else if (character == '\"' || character == '\'') {
                quote = character;
            } else if (character == '(') {
                depth++;
            } else if (character == ')' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static int findUnescaped(String value, char expected, int start) {
        for (int index = start; index < value.length(); index++) {
            if (value.charAt(index) == expected && !isEscaped(value, index)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isEscaped(String value, int index) {
        int slashes = 0;
        for (int cursor = index - 1; cursor >= 0 && value.charAt(cursor) == '\\'; cursor--) {
            slashes++;
        }
        return slashes % 2 == 1;
    }

    private static int skipWhitespace(String value, int index) {
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static String normalizeReferenceLabel(String label) {
        return label.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String unescapeMarkdown(String value) {
        return value.replaceAll("\\\\([!\"#$%&'()*+,./:;<=>?@\\[\\]\\\\^_`{|}~-])", "$1");
    }

    private static String maskInlineCode(String markdown) {
        char[] masked = markdown.toCharArray();
        int index = 0;
        while (index < markdown.length()) {
            if (markdown.charAt(index) != '`') {
                index++;
                continue;
            }
            int run = 1;
            while (index + run < markdown.length() && markdown.charAt(index + run) == '`') {
                run++;
            }
            String fence = "`".repeat(run);
            int end = markdown.indexOf(fence, index + run);
            if (end < 0) {
                index += run;
                continue;
            }
            for (int cursor = index; cursor < end + run; cursor++) {
                if (masked[cursor] != '\n' && masked[cursor] != '\r') {
                    masked[cursor] = ' ';
                }
            }
            index = end + run;
        }
        return new String(masked);
    }

    private record MarkdownDestination(String value, int line) {
        MarkdownDestination withLine(int newLine) {
            return new MarkdownDestination(value, newLine);
        }
    }
    private record DestinationParse(String destination, String error) {}
    private record ReferenceDefinition(
        String label,
        MarkdownDestination destination,
        String error
    ) {}

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
                result.append('\n');
                continue;
            }
            if (fenced && !stripped.isEmpty() && stripped.charAt(0) == fenceCharacter
                && leadingCount(stripped, fenceCharacter) >= fenceLength) {
                fenced = false;
                result.append('\n');
                continue;
            }
            if (!fenced) {
                result.append(line).append('\n');
            } else {
                result.append('\n');
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
        String[] openingLines = content.split("\\R", -1);
        String titlePrefix = "# Agent Note: ";
        boolean validTitle = openingLines.length > 0
            && openingLines[0].startsWith(titlePrefix)
            && openingLines[0].equals(openingLines[0].stripTrailing())
            && !openingLines[0].substring(titlePrefix.length()).isBlank();
        if (openingLines.length < 4
            || !validTitle
            || !openingLines[1].isEmpty()
            || !openingLines[2].equals(expectedStatus)
            || !openingLines[3].isEmpty()) {
            errors.add(relative + " must open with title, blank line, " + expectedStatus
                + ", and blank line exactly");
        }

        List<String> requiredHeadings = switch (lifecycle) {
            case "implemented" -> List.of(
                "## Problem",
                "## Decision",
                "## Alternatives considered",
                "## Consequences"
            );
            case "proposed" -> List.of(
                "## Problem",
                "## Proposal",
                "## Alternatives considered",
                "## Acceptance criteria",
                "## Risks"
            );
            case "rejected" -> List.of(
                "## Problem",
                "## Proposal",
                "## Alternatives considered"
            );
            default -> List.of();
        };
        List<String> actualHeadings = outsideFencedBlocks(content).lines()
            .filter(line -> line.startsWith("## ") && !line.startsWith("### "))
            .toList();
        int previous = -1;
        boolean orderValid = true;
        for (String heading : requiredHeadings) {
            int position = actualHeadings.indexOf(heading);
            if (position < 0) {
                errors.add(relative + " is missing exact heading " + heading);
            } else if (position <= previous) {
                orderValid = false;
            }
            previous = Math.max(previous, position);
        }
        if (!orderValid) {
            errors.add(relative + " required headings must appear in order: "
                + String.join(", ", requiredHeadings));
        }
    }

    private static Result checkSurfaceInstructions(Path root, Surface surface) {
        Path source = root.resolve(surface.activationPath());
        if (!Files.exists(source)) {
            return unavailable(
                surface.name() + "-instructions",
                "Phase 0; activate when " + surface.activationPath() + " exists"
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
        Set<Path> excludedRoots = Set.of(
            root.resolve(".git"),
            root.resolve(".idea"),
            root.resolve(".worktrees"),
            root.resolve("worktrees"),
            root.resolve(".superpowers")
        );
        List<Path> markdown = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                return excludedRoots.contains(directory)
                    ? FileVisitResult.SKIP_SUBTREE
                    : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (Files.isRegularFile(file) && file.toString().endsWith(".md")) {
                    markdown.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        markdown.sort(Comparator.naturalOrder());
        return List.copyOf(markdown);
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
            String normalized = changedPath.toLowerCase(Locale.ROOT);
            return normalized.equals(name) || normalized.startsWith(name + "/");
        }
    }
}
