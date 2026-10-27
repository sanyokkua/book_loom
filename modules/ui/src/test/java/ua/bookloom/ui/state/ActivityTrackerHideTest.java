package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;

/** Long work put in the background, what still locks the window, and the step count behind the bar. */
class ActivityTrackerHideTest {

    private final ActivityTracker tracker = new ActivityTracker(new StateMirror());

    private static StepChanged started(final int index) {
        return new StepChanged(StepKind.REQUEST, "sample", index, StepState.STARTED, null, null);
    }

    private static StepChanged ended(final int index) {
        return new StepChanged(StepKind.REQUEST, "sample", index, StepState.ENDED, null, StepResult.DONE);
    }

    // IF hiding the names pass kept it blocking, THEN the dialog would come straight back and the window stay locked.
    @Test
    void hide_hideableBlockingWork_releasesBlocking() {
        final ActivityTracker.Handle names = tracker.begin(ActivityKind.NAMES_PASS, () -> {});

        tracker.hide(names.id());

        assertThat(tracker.blocking().get()).isFalse();
        assertThat(tracker.blockingActivity().get()).isNull();
        assertThat(tracker.running())
                .singleElement()
                .extracting(Activity::isHidden)
                .isEqualTo(true);
    }

    // IF short work could be hidden by a stray call, THEN a brief suggestion would end out of sight.
    @Test
    void hide_shortWork_keepsBlocking() {
        final ActivityTracker.Handle brief = tracker.begin(ActivityKind.SUGGEST_STYLE, () -> {});

        tracker.hide(brief.id());

        assertThat(tracker.blocking().get()).isTrue();
    }

    // IF revealing did not block again, THEN the chip's "open" would bring back no dialog.
    @Test
    void reveal_hiddenWork_blocksAgain() {
        final ActivityTracker.Handle names = tracker.begin(ActivityKind.NAMES_PASS, () -> {});
        tracker.hide(names.id());

        tracker.reveal(names.id());

        assertThat(tracker.blockingActivity().get()).extracting(Activity::id).isEqualTo(names.id());
    }

    // IF a shown names pass locked the navigation, THEN leaving its screen could never ask "Model work is running".
    @Test
    void locking_namesPassShown_isFalse() {
        tracker.begin(ActivityKind.NAMES_PASS, () -> {});

        assertThat(tracker.locking().get()).isFalse();
    }

    // IF short blocking work did not lock, THEN a click could leave the brief while its suggestion runs.
    @Test
    void locking_briefShown_isTrue() {
        tracker.begin(ActivityKind.SUGGEST_STYLE, () -> {});

        assertThat(tracker.locking().get()).isTrue();
    }

    // IF the bar did not count the steps, THEN it would sit still through the brief's six requests.
    @Test
    void steps_twoOfFourEnded_fractionIsHalf() {
        final ActivityTracker.Handle brief = tracker.begin(ActivityKind.SUGGEST_STYLE, () -> {});

        brief.steps(new StepsPlanned(StepKind.REQUEST, 4, false));
        brief.steps(started(0));
        brief.steps(ended(0));
        brief.steps(started(1));
        brief.steps(ended(1));

        assertThat(tracker.running().getFirst().fraction()).isEqualTo(0.5);
    }

    // IF added work moved the bar back, THEN a repair would look like lost progress.
    @Test
    void steps_morePlanned_fractionNeverFalls() {
        final ActivityTracker.Handle brief = tracker.begin(ActivityKind.SUGGEST_STYLE, () -> {});
        brief.steps(new StepsPlanned(StepKind.REQUEST, 4, false));
        brief.steps(ended(0));
        brief.steps(ended(1));

        brief.steps(new StepsPlanned(StepKind.REQUEST, 4, true));

        assertThat(tracker.running().getFirst().fraction()).isEqualTo(0.5);
        assertThat(tracker.running().getFirst().steps().total()).isEqualTo(8);
    }
}
