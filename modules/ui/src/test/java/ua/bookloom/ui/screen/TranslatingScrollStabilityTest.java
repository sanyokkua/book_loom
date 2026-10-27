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
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.state.LogEntry;
import ua.bookloom.ui.state.LogKind;
import ua.bookloom.ui.state.RunState;
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
        chooseModel();
        showTranslating();
        resizeScene(960, 640);
        mirror().publishRunStarted("Frankenstein.epub", null);
        publish(RunState.RUNNING);
        mirror().live()
                .publishCalls(LiveCallFixtures.calls(
                        LiveCallFixtures.waiting(2, 8, LONG_TEXT, LiveCallFixtures.smallPrompt()),
                        LiveCallFixtures.answered(
                                LiveCallFixtures.waiting(1, 8, LONG_TEXT, LiveCallFixtures.smallPrompt()), LONG_TEXT)));
        mirror().publishLogEntries(List.of(new LogEntry(LogKind.RETRIED, List.of("format", "ch7 · p42"))));
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void stuckOnAJudge() {
        final WaitingCall call = new WaitingCall(
                CallKind.REVIEW,
                "ch7 · p42",
                0,
                1,
                2,
                Duration.ofSeconds(61),
                Duration.ofSeconds(90),
                Duration.ofSeconds(61),
                true);
        mirror().live().publishWaitingCall(call);
        WaitForAsyncUtils.waitForFxEvents();
    }

    private void modelAnswers() {
        mirror().live().publishWaitingCall(null);
        WaitForAsyncUtils.waitForFxEvents();
        onFx(() -> {});
    }

    // IF the stuck call's banner, pinned above the page, moved the page when it cleared, THEN the person reading the
    // middle of the page would be thrown to another part of it.
    @Test
    void stuckBannerClears_pageKeepsItsPosition() {
        showATallRunningDashboard();
        stuckOnAJudge();
        onFx(() -> content().setVvalue(0.5));
        final double before = offset();
        assertThat(before).as("the page must be scrolled away from its top").isGreaterThan(50);

        modelAnswers();

        assertThat(optional("translating-banner")).isNull();
        assertThat(offset()).isCloseTo(before, within(PIXEL));
    }

    // The same for any focused control that goes away, here the log tab's Problems only choice while the person reads
    // the top of the page: the next focusable control, the log list, must not pull the page down.
    @Test
    void focusedNodeBelowTheViewportHidden_pageStaysAtTheTop() {
        showATallRunningDashboard();
        openActivityLog();
        final Node focused = required("translating-log-problems");
        onFx(focused::requestFocus);
        onFx(() -> content().setVvalue(0.0));
        final double before = offset();

        onFx(() -> focused.setVisible(false));
        onFx(() -> {});

        assertThat(offset()).isCloseTo(before, within(PIXEL));
    }
}
