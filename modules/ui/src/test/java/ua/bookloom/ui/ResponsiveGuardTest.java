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
    private static final Pattern WIDTH_SWITCH =
            Pattern.compile("widthProperty\\(\\)|(scene|getScene\\(\\)|window|getWindow\\(\\))\\.getWidth\\(\\)"
                    + "|static final double \\w*(_BELOW|BREAKPOINT\\w*|_AT|SIDE_BY_SIDE\\w*)\\s*=");
    private static final Map<String, String> SIZING_ONLY = Map.of(
            "control/LayoutClass.java", "the one responsive mechanism",
            "control/Overrun.java", "shows a tooltip while a label is cut; changes no arrangement",
            "ModalHost.java", "sizes the dialog card to the window",
            "BusyHost.java", "sizes the progress card to the window",
            "control/LiveCallPanel.java", "the list takes a fixed share of its split",
            "screen/ReviewDeskCard.java", "the list takes a fixed share of its split",
            "screen/StructureSegmentCell.java", "wraps a segment's text at the cell's width");

    // IF a screen measured a width and switched its own layout, THEN two widgets would decide the same thing two ways.
    @Test
    void sources_outsideTheSharedMechanism_neverSwitchALayoutOnAWidth() {
        final List<String> offenders = javaFiles()
                .filter(file -> !SIZING_ONLY.containsKey(relative(file)))
                .filter(file -> WIDTH_SWITCH.matcher(read(file)).find())
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
                .filter(name ->
                        !WIDTH_SWITCH.matcher(read(SOURCES.resolve(name))).find())
                .sorted()
                .toList();

        assertThat(stale).as("allow-list entries that no longer read a width").isEmpty();
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
