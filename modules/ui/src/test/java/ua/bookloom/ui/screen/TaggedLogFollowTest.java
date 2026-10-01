package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;
import javafx.scene.control.skin.VirtualFlow;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.LogKind;

/**
 * The activity log follows its newest line only while the person leaves it at the end: a line being read is never
 * pulled away by the next one, and Jump to latest brings the end back and follows again.
 */
class TaggedLogFollowTest extends TranslatingScreenTestBase {

    private static final int LINES = 40;

    private void publishLines(final int from, final int count) {
        mirror().publishLogEntries(IntStream.range(from, from + count)
                .mapToObj(i -> new LogEntry(LogKind.ACCEPTED, List.of("ch1 · p" + i)))
                .toList());
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> {});
        WaitForAsyncUtils.waitForFxEvents();
    }

    private VirtualFlow<?> flow() {
        return (VirtualFlow<?>) logList().lookup(".virtual-flow");
    }

    private int lastVisibleIndex() {
        return ThemeTestSupport.onFx(() -> flow().getLastVisibleCell().getIndex());
    }

    private int firstVisibleIndex() {
        return ThemeTestSupport.onFx(() -> flow().getFirstVisibleCell().getIndex());
    }

    private void running() {
        mirror().publishRunStarted("Frankenstein.epub");
        WaitForAsyncUtils.waitForFxEvents();
        showTranslating();
        publishLines(0, LINES);
    }

    // IF the log stopped following on its own, THEN a person watching the run would have to scroll after every line.
    @Test
    void log_leftAtTheEnd_followsEachNewLine() {
        running();

        publishLines(LINES, 5);

        assertThat(lastVisibleIndex()).isEqualTo(LINES + 4);
        assertThat(isShown("translating-log-jump")).isFalse();
    }

    // IF a new line pulled the log back to the end, THEN a person could never read an earlier line while the run goes
    // on; scrolled up, the log stays where it is and offers the way back.
    @Test
    void log_scrolledUp_staysWhereItIsAndOffersJumpToLatest() {
        running();
        onFx(() -> flow().setPosition(0));

        publishLines(LINES, 5);

        assertThat(firstVisibleIndex()).isZero();
        assertThat(isShown("translating-log-jump")).isTrue();
        assertThat(TooltipProbe.tipText(required("translating-log-jump"))).isNotBlank();
    }

    // IF Jump to latest only scrolled once, THEN the next line would leave the person behind again.
    @Test
    void jumpToLatest_pressed_showsTheNewestLineAndFollowsAgain() {
        running();
        onFx(() -> flow().setPosition(0));
        publishLines(LINES, 5);

        onFx(() -> button("translating-log-jump").fire());
        publishLines(LINES + 5, 3);

        assertThat(lastVisibleIndex()).isEqualTo(LINES + 7);
        assertThat(isShown("translating-log-jump")).isFalse();
    }
}
