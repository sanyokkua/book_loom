package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * One class scrolls: {@code control/ScrollHost}. Every scroll pane the interface builds is one (or extends it), in code
 * and in FXML, and every Settings tab holds its content in one — a tab clips what it holds, so content laid straight
 * into a tab could run below the window with nothing to scroll it (the Generation, Automation and Storage tabs did).
 */
class ScrollGuardTest {

    private static final Path SOURCES = Path.of("src/main/java/ua/bookloom/ui");
    private static final Path VIEWS = Path.of("src/main/resources/ua/bookloom/ui");
    private static final String HOST = "control/ScrollHost.java";
    private static final Pattern OWN_SCROLL_PANE = Pattern.compile("new ScrollPane\\(|extends ScrollPane\\b");
    private static final Pattern FXML_SCROLL_PANE = Pattern.compile("<ScrollPane\\b");
    private static final Pattern TAB = Pattern.compile("<Tab\\b[^>]*>");
    private static final Pattern HOSTED_TAB = Pattern.compile("<Tab\\b[^>]*>\\s*(<!--(?s:.*?)-->\\s*)?<ScrollHost\\b");

    // IF a class built its own scroll pane, THEN its fit, bars and scrolling could drift from every other one.
    @Test
    void sources_outsideTheScrollHost_neverBuildAScrollPane() {
        final List<String> offenders = files(SOURCES, ".java")
                .filter(file -> !relative(SOURCES, file).equals(HOST))
                .filter(file -> OWN_SCROLL_PANE.matcher(read(file)).find())
                .map(file -> relative(SOURCES, file))
                .toList();

        assertThat(offenders).as("classes building a scroll pane of their own").isEmpty();
    }

    // IF a view declared a plain scroll pane, THEN it would scroll by settings no other view shares.
    @Test
    void views_neverDeclareAPlainScrollPane() {
        final List<String> offenders = files(VIEWS, ".fxml")
                .filter(file -> FXML_SCROLL_PANE.matcher(read(file)).find())
                .map(file -> relative(VIEWS, file))
                .toList();

        assertThat(offenders)
                .as("views declaring a ScrollPane instead of a ScrollHost")
                .isEmpty();
    }

    // IF a Settings tab held its content directly, THEN the tab would clip a card below the window and nothing scrolls.
    @Test
    void settingsTabs_eachHoldTheirContentInAScrollHost() {
        final String settings = read(VIEWS.resolve("screen/settings.fxml"));

        assertThat(HOSTED_TAB.matcher(settings).results().count())
                .as("Settings tabs whose content is a ScrollHost")
                .isEqualTo(TAB.matcher(settings).results().count())
                .isEqualTo(6);
    }

    private static Stream<Path> files(final Path root, final String suffix) {
        try (Stream<Path> walked = Files.walk(root)) {
            return walked.filter(file -> file.toString().endsWith(suffix)).toList().stream();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String relative(final Path root, final Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
