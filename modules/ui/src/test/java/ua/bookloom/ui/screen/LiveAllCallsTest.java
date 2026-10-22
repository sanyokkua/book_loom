package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.stream.LongStream;
import javafx.scene.control.Label;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.LiveRowFixtures;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.LiveRow.Stage;

/** "All calls…" lists the calls the run kept, newest first, in the progress dialog's Calls view. */
class LiveAllCallsTest extends LiveCardTestBase {

    private static CallSnapshot call(final long id) {
        return CallSnapshot.waiting(
                        id,
                        CallKind.DRAFT,
                        "Call " + id + " · Draft · 1 segment",
                        null,
                        List.of(new CallSegment("s-" + id, "ch7 · p" + id, "Source " + id)),
                        List.of(),
                        LiveCallFixtures.STARTED,
                        1,
                        2,
                        Duration.ofMinutes(3))
                .answered("reply " + id, null, Duration.ofSeconds(3));
    }

    private void showWithCalls(final long first, final long last) {
        showLive(LiveRowFixtures.calls(
                LiveRowFixtures.rows(2, Stage.IN_CALL),
                LongStream.rangeClosed(first, last)
                        .mapToObj(LiveAllCallsTest::call)
                        .toList()));
    }

    private String stepName(final int index) {
        return ((Label) required("progress-step-" + index).lookup(".progress-step-name")).getText();
    }

    // IF All calls… opened something else, THEN the person could not look back through the calls of the run.
    @Test
    void allCalls_pressed_opensTheCallsViewNewestFirst() {
        showWithCalls(1, 3);

        onFx(() -> button("translating-live-all-calls").fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isShown("calls-card")).isTrue();
        assertThat(List.of(stepName(0), stepName(1), stepName(2)))
                .containsExactly(
                        "Call 3 · Draft · 1 segment", "Call 2 · Draft · 1 segment", "Call 1 · Draft · 1 segment");
    }

    // IF the dialog listed a call the card no longer keeps, THEN it would promise a prompt that is gone.
    @Test
    void allCalls_fiftyKept_listsFiftyAndNotTheOlderOnes() {
        showWithCalls(71, 120);

        onFx(() -> button("translating-live-all-calls").fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(stepName(0)).isEqualTo("Call 120 · Draft · 1 segment");
        assertThat(textOf("calls-card")).doesNotContain("Call 70 ");
    }

    // IF Close left the dialog up, THEN it would cover the card for the rest of the run.
    @Test
    void close_pressed_closesTheDialog() {
        showWithCalls(1, 2);
        onFx(() -> button("translating-live-all-calls").fire());
        WaitForAsyncUtils.waitForFxEvents();

        onFx(() -> button("calls-close").fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isShown("calls-card")).isFalse();
    }

    // IF the button did not say what it lists, THEN "All calls…" would not tell that only the last 50 are kept.
    @Test
    void allCalls_hovered_saysTheLastFiftyAreKept() {
        showWithCalls(1, 2);

        assertThat(TooltipProbe.tipText(required("translating-live-all-calls")))
                .isEqualTo("Every call of this translation, newest first — the last 50 are kept.");
    }
}
