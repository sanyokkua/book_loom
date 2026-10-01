package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.LiveRow;
import ua.bookloom.ui.state.LiveRows;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.LogKind;
import ua.bookloom.ui.state.RunState;
import ua.bookloom.ui.state.StateMirror;
import ua.bookloom.ui.state.WaitingCall;

/**
 * The translating page stays where the person scrolled it when a transient control they had focused goes away: the
 * slow-model banner's actions, cleared once the model answers, must not drag the page to the next focusable control.
 */
class TranslatingScrollStabilityTest extends TranslatingScreenTestBase {

    private static final String LONG_TEXT =
            "She had lost her mother, and the poor girl wept as she followed the coffin. ".repeat(8);
    private static final double PIXEL = 1.0;

    private ScrollPane content() {
        return (ScrollPane) required("shell-content-scroll");
    }

    /** How far the viewport's top is from the content's top, in pixels. */
    private double offset() {
        return ThemeTestSupport.onFx(() -> {
            final ScrollPane pane = content();
            final double overflow = pane.getContent().getLayoutBounds().getHeight()
                    - pane.getViewportBounds().getHeight();
            return (pane.getVvalue() - pane.getVmin()) / (pane.getVmax() - pane.getVmin()) * Math.max(0, overflow);
        });
    }

    private void showATallRunningDashboard() {
        showTranslating();
        publish(RunState.RUNNING);
        mirror().live()
                .publishLiveRows(new LiveRows(
                        new LiveRow("s-1", "ch7 · p41", LONG_TEXT, LONG_TEXT, 0.93, SegmentPath.DRAFT, false, false),
                        new LiveRow("s-2", "ch7 · p42", LONG_TEXT, null, null, null, true, false)));
        mirror().publishLogEntries(List.of(new LogEntry(LogKind.RETRIED, List.of("format", "ch7 · p42"))));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void stuckOnAJudge() {
        final WaitingCall call = new WaitingCall(
                CallKind.JUDGE,
                "ch7 · p42",
                0,
                1,
                2,
                Duration.ofSeconds(61),
                Duration.ofSeconds(90),
                Duration.ofSeconds(61),
                true);
        mirror().live().publishWaitingCall(call);
        mirror().publishWaitingSeconds(61);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void modelAnswers() {
        mirror().live().publishWaitingCall(null);
        mirror().publishWaitingSeconds(StateMirror.NOT_WAITING);
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> {});
    }

    // IF the focused Skip segment button vanished with the banner and the page followed the focus elsewhere, THEN the
    // person reading the middle of the page would be thrown to another part of it.
    @Test
    void stuckBannerClears_focusOnItsSkipButton_pageKeepsItsPosition() {
        showATallRunningDashboard();
        stuckOnAJudge();
        onFx(() -> button("translating-skip-segment").requestFocus());
        onFx(() -> content().setVvalue(0.5));
        final double before = offset();
        assertThat(before).as("the page must be scrolled away from its top").isGreaterThan(50);

        modelAnswers();

        assertThat(isShown("translating-skip-segment")).isFalse();
        assertThat(offset()).isCloseTo(before, within(PIXEL));
    }

    // The same for any focused control that goes away below the viewport, here the log's errors-only chip while the
    // person reads the top of the page: the next focusable control, the log list, must not pull the page down.
    @Test
    void focusedNodeBelowTheViewportHidden_pageStaysAtTheTop() {
        showATallRunningDashboard();
        final Node focused = required("translating-log-errors-only");
        onFx(focused::requestFocus);
        onFx(() -> content().setVvalue(0.0));
        final double before = offset();

        onFx(() -> focused.setVisible(false));
        onFx(() -> {});

        assertThat(offset()).isCloseTo(before, within(PIXEL));
    }
}
