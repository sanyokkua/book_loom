package ua.bookloom.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Keeps names, quotes and passages of the books under test out of the repository's sources. The list of what to keep
 * out is the owner's own and never committed (it would itself name the books): one case-sensitive word or phrase per
 * line in {@code neutral-denylist.txt} inside the directory {@code BOOKLOOM_CORPUS_DIR} points to; blank lines and
 * lines starting with {@code #} are skipped. Without the variable or the file the test is skipped, as on CI.
 */
class BookNeutralSourcesTest {

    private static final String CORPUS_DIR_ENV = "BOOKLOOM_CORPUS_DIR";
    private static final String DENYLIST_FILE = "neutral-denylist.txt";
    private static final Set<String> TEXT_EXTENSIONS =
            Set.of("java", "kt", "kts", "properties", "prompt", "txt", "json", "md", "fxml", "css", "xml", "csv");
    private static final String WORD_EDGE = "[\\p{L}\\p{N}_]";

    // IF a name from a book under test were typed into a test, a prompt or a fixture, THEN it would ship in the
    // repository; the owner's denylist names them, and this test lists every place one appears.
    @Test
    void sources_denylistedToken_none() {
        final List<String> tokens = denylist();

        assertThat(hits(tokens)).as("denylisted tokens in modules/*/src").isEmpty();
    }

    private static List<String> denylist() {
        final String dir = System.getenv(CORPUS_DIR_ENV);
        Assumptions.assumeTrue(dir != null && !dir.isBlank(), CORPUS_DIR_ENV + " is not set; skipping the guard");
        final Path file = Path.of(dir).resolve(DENYLIST_FILE);
        Assumptions.assumeTrue(Files.isRegularFile(file), file + " is absent; skipping the guard");
        final List<String> tokens = readLines(file).stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .toList();
        Assumptions.assumeFalse(tokens.isEmpty(), file + " holds no token; skipping the guard");
        return tokens;
    }

    private static List<String> hits(final List<String> tokens) {
        final Pattern pattern = wholeWord(tokens);
        final Path modules = modulesDir();
        try (Stream<Path> sources = Files.list(modules)) {
            return sources.map(module -> module.resolve("src"))
                    .filter(Files::isDirectory)
                    .flatMap(BookNeutralSourcesTest::textFiles)
                    .flatMap(file -> hitsIn(file, modules, pattern).stream())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> hitsIn(final Path file, final Path modules, final Pattern pattern) {
        final List<String> lines = readLines(file);
        final List<String> found = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            final Matcher matcher = pattern.matcher(lines.get(index));
            while (matcher.find()) {
                found.add(modules.relativize(file) + ":" + (index + 1) + ": " + matcher.group(1));
            }
        }
        return found;
    }

    private static Pattern wholeWord(final List<String> tokens) {
        final String alternation = tokens.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .reduce((left, right) -> left + "|" + right)
                .orElseThrow();
        return Pattern.compile("(?<!" + WORD_EDGE + ")(" + alternation + ")(?!" + WORD_EDGE + ")");
    }

    private static Stream<Path> textFiles(final Path root) {
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).filter(BookNeutralSourcesTest::isTextFile).toList().stream();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isTextFile(final Path file) {
        final String name = file.getFileName().toString();
        final int dot = name.lastIndexOf('.');
        return dot >= 0 && TEXT_EXTENSIONS.contains(name.substring(dot + 1));
    }

    private static List<String> readLines(final Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                    .lines()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Gradle runs a module's tests with that module as the working directory; the modules folder is its parent.
    private static Path modulesDir() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.isDirectory(dir.resolve("modules"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException(
                    "no modules directory above " + Path.of("").toAbsolutePath());
        }
        return dir.resolve("modules");
    }
}
