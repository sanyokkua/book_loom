package ua.bookloom.ui.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.stream.LongStream;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.CallItem;
import ua.bookloom.api.pipeline.CallItemStatus;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSegment;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.ui.LiveCallFixtures;
import ua.bookloom.ui.LiveRowFixtures;
import ua.bookloom.ui.ThemeTestSupport;
import ua.bookloom.ui.TooltipProbe;
import ua.bookloom.ui.state.LiveRow.Stage;

/** "All calls..." lists the calls the run kept, newest first, in the progress dialog's Calls view. */
class LiveAllCallsTest extends LiveCardTestBase {

    private static final List<CallKind> KINDS = List.of(CallKind.DRAFT, CallKind.REVIEW, CallKind.PLACEHOLDER_REPAIR);

    // The kinds take turns so the rows tell the calls apart; the label is the pipeline's prompt name, never shown.
    private static CallSnapshot call(final long id) {
        return CallSnapshot.waiting(
                        id,
                        KINDS.get((int) ((id - 1) % KINDS.size())),
                        "draft-batch-json",
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

    // IF All calls... opened something else, THEN the person could not look back through the calls of the run.
    @Test
    void allCalls_pressed_opensTheCallsViewNewestFirst() {
        showWithCalls(1, 3);

        onFx(() -> button("translating-live-all-calls").fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(isShown("calls-card")).isTrue();
        assertThat(List.of(stepName(0), stepName(1), stepName(2)))
                .containsExactly("Placeholder repair", "Review", "Translate");
    }

    private static CallSnapshot draft(final long id) {
        return CallSnapshot.waiting(
                id,
                CallKind.DRAFT,
                "draft-batch-json",
                null,
                List.of(new CallSegment("s-" + id, "ch1 · p" + id, "Source " + id)),
                List.of(),
                LiveCallFixtures.STARTED,
                1,
                2,
                Duration.ofMinutes(3));
    }

    // The sentence over the selected call and what it says under "What it says".
    private List<String> sayAndOutput() {
        return ThemeTestSupport.onFx(() -> List.of(
                ((Label) required("progress-say")).getText(), ((TextArea) required("progress-output")).getText()));
    }

    // IF the call still waiting on a running translation (the newest one, so the one the dialog opens on) were listed
    // as done, THEN "Translate is done" would sit over "Nothing yet." every time the dialog is opened; it says the call
    // is still at work, and an answered batch says the translations it brought back.
    @Test
    void allCalls_newestCallStillWaiting_saysItIsAtWorkAndAnAnsweredBatchShowsItsTranslations() {
        final CallSnapshot answered = draft(1).answered("{\"1\":\"Джерело 1\"}", null, Duration.ofSeconds(3))
                .withItems(List.of(new CallItem("1", "s-1", "Джерело 1", CallItemStatus.OK, List.of())));
        showLive(LiveRowFixtures.calls(LiveRowFixtures.rows(2, Stage.IN_CALL), List.of(answered, draft(2))));

        onFx(() -> button("translating-live-all-calls").fire());
        WaitForAsyncUtils.waitForFxEvents();
        final List<String> waiting = sayAndOutput();
        clickOn(required("progress-step-1"));
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(waiting).containsExactly("Working on Translate.", "Nothing yet.");
        assertThat(sayAndOutput()).containsExactly("Translate is done.", "Джерело 1");
    }

    // IF the dialog listed a call the card no longer keeps, THEN it would promise a prompt that is gone.
    @Test
    void allCalls_fiftyKept_listsFiftyAndNotTheOlderOnes() {
        showWithCalls(71, 120);

        onFx(() -> button("translating-live-all-calls").fire());
        WaitForAsyncUtils.waitForFxEvents();

        assertThat(stepName(0)).isEqualTo("Placeholder repair");
        assertThat(((ListView<?>) required("calls-card").lookup(".list-view")).getItems())
                .hasSize(50);
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

    // IF the button did not say what it lists, THEN "All calls..." would not tell that only the last 50 are kept.
    @Test
    void allCalls_hovered_saysTheLastFiftyAreKept() {
        showWithCalls(1, 2);

        assertThat(TooltipProbe.tipText(required("translating-live-all-calls")))
                .isEqualTo("Every call of this translation, newest first — the last 50 are kept.");
    }
}
