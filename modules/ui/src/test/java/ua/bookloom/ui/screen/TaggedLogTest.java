package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.stream.Stream;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.LogKind;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.TechnicalDetails;

/** The activity log's cell: the tag first, the mark and the catalogue text after it, in the monospace face. */
class TaggedLogTest extends TranslatingScreenTestBase {

    private static final String LOCATOR = "ch7 · p42";

    private static final List<String> CALL_ARGS = List.of("review", " · " + LOCATOR, "2", "1", "0:06");

    static Stream<Arguments> lines() {
        return Stream.concat(visibleLines(), technicalLines());
    }

    private static Stream<Arguments> visibleLines() {
        return Stream.of(
                Arguments.of(
                        new LogEntry(LogKind.ACCEPTED, List.of(LOCATOR)), "ok    ✓ Segment ch7 · p42 was accepted."),
                Arguments.of(
                        LogEntry.of(
                                LogKind.REPAIRED,
                                MessageKey.LOG_MODEL_CALL,
                                List.of("directed_fix", " · " + LOCATOR, "0", "1", "0:06")),
                        "fix   ↺ Fix · ch7 · p42 · attempt 1 · 0:06"),
                Arguments.of(
                        new LogEntry(LogKind.MEMORY, List.of("reuse", LOCATOR)),
                        "mem   ≡ Segment ch7 · p42 was reused from the translation memory."),
                Arguments.of(
                        new LogEntry(LogKind.SUMMARY, List.of("3")),
                        "sum   Σ The running summary was updated (version 3)."),
                Arguments.of(
                        LogEntry.of(
                                LogKind.RETRIED,
                                MessageKey.LOG_CALL_FAILED,
                                List.of("directed_fix", " · " + LOCATOR, "0", "2", "1:30", "timeout")),
                        "retry ↻ Fix · ch7 · p42 · attempt 2 · 1:30 · failed: timeout"),
                Arguments.of(
                        new LogEntry(LogKind.RETRIED, List.of("resume", "")),
                        "retry ↻ The run resumed after a provider error."),
                Arguments.of(
                        new LogEntry(LogKind.SEGMENT_ERROR, List.of(LOCATOR)),
                        "err   ✕ Segment ch7 · p42 failed with a recoverable error."),
                Arguments.of(new LogEntry(LogKind.MILESTONE, List.of("paused")), "info  ◆ The run was paused."));
    }

    private static Stream<Arguments> technicalLines() {
        return Stream.of(
                Arguments.of(
                        LogEntry.technical(LogKind.MILESTONE, MessageKey.LOG_MODEL_CALL, CALL_ARGS),
                        "info  ◆ Review · ch7 · p42 · round 2 · attempt 1 · 0:06"),
                Arguments.of(
                        LogEntry.technical(
                                LogKind.MILESTONE, MessageKey.LOG_ROUND, List.of(LOCATOR, "1", "3", "meaning")),
                        "info  ◆ Segment ch7 · p42 entered repair round 1 of 3 to fix “meaning”."));
    }

    private void showLog(final LogEntry... entries) {
        showTranslating();
        publish(RunState.RUNNING);
        onFx(() -> injector.getInstance(TechnicalDetails.class).property().set(true));
        publishLog(entries);
        openActivityLog();
    }

    // IF a kind's tag were not first, or its mark or text were lost, THEN the kinds would not scan by column.
    @ParameterizedTest
    @MethodSource("lines")
    void cell_eachLine_rendersItsTagThenItsMarkThenTheCatalogueText(final LogEntry entry, final String expected) {
        showLog(entry);

        assertThat(logCells().get(0).getAccessibleText()).isEqualTo(expected);
    }

    // IF a routine answered call were listed with technical details off, THEN every reader would see a line per call.
    @Test
    void technicalLine_detailsOff_isNotListed_andOnItIsOneInfoLine() {
        showTranslating();
        publish(RunState.RUNNING);
        publishLog(
                new LogEntry(LogKind.ACCEPTED, List.of(LOCATOR)),
                LogEntry.technical(LogKind.MILESTONE, MessageKey.LOG_MODEL_CALL, CALL_ARGS));
        openActivityLog();
        assertThat(logCells()).extracting(cell -> cell.getItem().kind()).containsExactly(LogKind.ACCEPTED);

        onFx(() -> injector.getInstance(TechnicalDetails.class).property().set(true));

        assertThat(logCells())
                .extracting(cell -> cell.getItem().kind())
                .containsExactly(LogKind.ACCEPTED, LogKind.MILESTONE);
    }

    // IF the log were not set in the monospace face, THEN the tag column would not line up.
    @ParameterizedTest
    @MethodSource("lines")
    void cell_eachLine_isDrawnInTheMonospacedFamily(final LogEntry entry, final String ignored) {
        showLog(entry);

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
