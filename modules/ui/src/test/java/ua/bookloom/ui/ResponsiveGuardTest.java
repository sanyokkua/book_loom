package ua.bookloom.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * One place decides every width-dependent arrangement: {@code control/LayoutClass} (the width classes, the one
 * narrow-at-once, widen-16-late rule and the followers), used by {@code TwoColumns}, {@code FormGrid},
 * {@code PriorityRow} and the screens. A class that watches a width or the window's width to switch its own layout is
 * a second implementation; the few that read a width to size something, not to choose an arrangement, are named below.
 */
class ResponsiveGuardTest {

    private static final Path SOURCES = Path.of("src/main/java/ua/bookloom/ui");
    // Reading a width to follow it, or the window's width, or a hand-rolled threshold constant.
    private static final Pattern WIDTH_READ =
            Pattern.compile("widthProperty\\(\\)|(?:scene|getScene\\(\\)|window|getWindow\\(\\))\\.getWidth\\(\\)");
    private static final Pattern DOUBLE_CONSTANT = Pattern.compile("static final double (\\w+)\\s*=");
    private static final List<String> THRESHOLD_PARTS = List.of("BREAKPOINT", "SIDE_BY_SIDE");
    private static final List<String> THRESHOLD_ENDINGS = List.of("_BELOW", "_AT");
    private static final Map<String, String> SIZING_ONLY = Map.of(
            "control/LayoutClass.java", "the one responsive mechanism",
            "control/Wrapped.java", "gives a wrapping label the height of its lines; changes no arrangement",
            "control/Overrun.java", "shows a tooltip while a label is cut; changes no arrangement",
            "ModalHost.java", "sizes the dialog card to the window",
            "BusyHost.java", "sizes the progress card to the window",
            "control/LiveCallPanel.java", "the list takes a fixed share of its split",
            "screen/ReviewDeskCard.java", "the list takes a fixed share of its split",
            "screen/StructureSegmentCell.java", "wraps a segment's text at the cell's width",
            "screen/TermTables.java", "lets a table narrower than its columns' minimums scroll across; no arrangement");

    // IF a screen measured a width and switched its own layout, THEN two widgets would decide the same thing two ways.
    @Test
    void sources_outsideTheSharedMechanism_neverSwitchALayoutOnAWidth() {
        final List<String> offenders = javaFiles()
                .filter(file -> !SIZING_ONLY.containsKey(relative(file)))
                .filter(file -> readsWidth(read(file)))
                .map(ResponsiveGuardTest::relative)
                .toList();

        assertThat(offenders)
                .as("classes deciding a layout from a width themselves")
                .isEmpty();
    }

    // IF an allowed file stopped reading a width, THEN its exemption would stay behind as a hole nobody needs.
    @Test
    void allowList_eachEntry_stillReadsAWidth() {
        final List<String> stale = SIZING_ONLY.keySet().stream()
                .filter(name -> !readsWidth(read(SOURCES.resolve(name))))
                .sorted()
                .toList();

        assertThat(stale).as("allow-list entries that no longer read a width").isEmpty();
    }

    private static boolean readsWidth(final String source) {
        return WIDTH_READ.matcher(source).find()
                || DOUBLE_CONSTANT
                        .matcher(source)
                        .results()
                        .map(constant -> constant.group(1))
                        .anyMatch(ResponsiveGuardTest::isThreshold);
    }

    // A hand-rolled threshold: a constant named for a breakpoint, a side-by-side switch, or the width it is below or
    // at.
    private static boolean isThreshold(final String name) {
        return THRESHOLD_PARTS.stream().anyMatch(name::contains)
                || THRESHOLD_ENDINGS.stream().anyMatch(name::endsWith);
    }

    private static Stream<Path> javaFiles() {
        try (Stream<Path> walked = Files.walk(SOURCES)) {
            return walked.filter(file -> file.toString().endsWith(".java")).toList().stream();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String relative(final Path file) {
        return SOURCES.relativize(file).toString().replace('\\', '/');
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
