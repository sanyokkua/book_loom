package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import javafx.scene.control.ListCell;
import javafx.scene.text.Font;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.LogKind;

/** The activity log's cell: the tag first, the mark and the catalogue text after it, in the monospace face. */
class TaggedLogTest extends TranslatingScreenTestBase {

    private static final String LOCATOR = "ch7 · p42";

    static Stream<Arguments> kinds() {
        return Stream.of(
                Arguments.of(LogKind.ACCEPTED, List.of(LOCATOR), "ok    ✓ Segment ch7 · p42 was accepted."),
                Arguments.of(
                        LogKind.REPAIRED, List.of(LOCATOR), "fix   ✎ A repair pass was applied to segment ch7 · p42."),
                Arguments.of(
                        LogKind.GLOSSARY_APPLIED,
                        List.of("reuse", LOCATOR),
                        "mem   ≡ Segment ch7 · p42 was reused from the translation memory."),
                Arguments.of(
                        LogKind.SUMMARY_UPDATED, List.of("3"), "sum   Σ The running summary was updated (version 3)."),
                Arguments.of(
                        LogKind.RETRIED,
                        List.of("format", LOCATOR),
                        "retry ↻ Segment ch7 · p42 is being sent to the model again after a format check."),
                Arguments.of(
                        LogKind.SEGMENT_ERROR,
                        List.of(LOCATOR),
                        "err   ✕ Segment ch7 · p42 failed with a recoverable error."),
                Arguments.of(LogKind.MILESTONE, List.of("paused"), "info  ◆ The run was paused."));
    }

    // IF a kind's tag were not first, or its mark or text were lost, THEN the seven kinds would not scan by column.
    @ParameterizedTest
    @MethodSource("kinds")
    void cell_eachKind_rendersItsTagThenItsMarkThenTheCatalogueText(
            final LogKind kind, final List<String> args, final String expected) {
        showTranslating();

        publishLog(new LogEntry(kind, args));

        assertThat(logCells().get(0).getText()).isEqualTo(expected);
    }

    // IF the log were not set in the monospace face, THEN the tag column would not line up.
    @ParameterizedTest
    @MethodSource("kinds")
    void cell_eachKind_isDrawnInTheMonospacedFamily(final LogKind kind, final List<String> args) {
        showTranslating();

        publishLog(new LogEntry(kind, args));

        final ListCell<LogEntry> cell = logCells().get(0);
        assertThat(cell.getFont().getFamily())
                .isEqualTo(Font.font("Monospaced", 12).getFamily());
    }
}
