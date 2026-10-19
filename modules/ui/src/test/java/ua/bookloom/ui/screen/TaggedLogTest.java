package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.Stream;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
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
                        LogKind.MODEL_CALL,
                        List.of("review", " · " + LOCATOR, "2", "1", "0:06"),
                        "call  ⇄ Review · ch7 · p42 · round 2 · attempt 1 · 0:06"),
                Arguments.of(
                        LogKind.CALL_FAILED,
                        List.of("directed_fix", " · " + LOCATOR, "0", "2", "1:30", "timeout"),
                        "fail  ⚠ Fix · ch7 · p42 · attempt 2 · 1:30 · failed: timeout"),
                Arguments.of(
                        LogKind.ROUND,
                        List.of(LOCATOR, "1", "3", "meaning"),
                        "round ↺ Segment ch7 · p42 entered repair round 1 of 3 to fix “meaning”."),
                Arguments.of(
                        LogKind.GLOSSARY_APPLIED,
                        List.of("reuse", LOCATOR),
                        "mem   ≡ Segment ch7 · p42 was reused from the translation memory."),
                Arguments.of(
                        LogKind.SUMMARY_UPDATED, List.of("3"), "sum   Σ The running summary was updated (version 3)."),
                Arguments.of(LogKind.RETRIED, List.of("resume", ""), "retry ↻ The run resumed after a provider error."),
                Arguments.of(
                        LogKind.SEGMENT_ERROR,
                        List.of(LOCATOR),
                        "err   ✕ Segment ch7 · p42 failed with a recoverable error."),
                Arguments.of(LogKind.MILESTONE, List.of("paused"), "info  ◆ The run was paused."));
    }

    // IF a kind's tag were not first, or its mark or text were lost, THEN the kinds would not scan by column.
    @ParameterizedTest
    @MethodSource("kinds")
    void cell_eachKind_rendersItsTagThenItsMarkThenTheCatalogueText(
            final LogKind kind, final List<String> args, final String expected) {
        showTranslating();

        publishLog(new LogEntry(kind, args));

        assertThat(logCells().get(0).getAccessibleText()).isEqualTo(expected);
    }

    // IF the log were not set in the monospace face, THEN the tag column would not line up.
    @ParameterizedTest
    @MethodSource("kinds")
    void cell_eachKind_isDrawnInTheMonospacedFamily(final LogKind kind, final List<String> args) {
        showTranslating();

        publishLog(new LogEntry(kind, args));

        final Label words = (Label) logCells().get(0).lookup(".log-text");
        // Whichever face the system offers, a monospaced one gives a narrow and a wide letter the same advance.
        assertThat(advance("iiii", words.getFont())).isEqualTo(advance("WWWW", words.getFont()), within(0.01));
    }

    private static double advance(final String text, final Font font) {
        final Text probe = new Text(text);
        probe.setFont(font);
        return probe.getLayoutBounds().getWidth();
    }
}
