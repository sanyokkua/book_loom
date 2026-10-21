package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.ui.ThemeTestSupport.onFx;

import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;

/** The review desk follows the mode of the run on screen; with no run yet it asks for the mode now in force. */
class ReviewModePerRunTest extends ReviewViewModelTestBase {

    private void publishRunStarted(final ReviewMode mode) {
        onFx(() -> {
            mirror.publishRunStarted("Frankenstein.epub", new RunMode(QualityDial.BALANCED, mode));
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    private ReviewPauseFollower buildFollower() {
        buildViewModel();
        return onFx(() -> new ReviewPauseFollower(mirror, review, viewModel, () -> reviewMode));
    }

    private void pauseAt(final String segmentId) {
        onFx(() -> {
            mirror.review().publishReviewPauseSegment(segmentId);
            return null;
        });
        WaitForAsyncUtils.waitForFxEvents();
    }

    // IF the desk used the mode of the launch, THEN a finished Assisted run would still offer All segments just
    // because the window was opened Unattended.
    @Test
    void allSegmentsOffered_runStartedAssistedWhileProviderSaysUnattended_isNotOffered() {
        reviewMode = ReviewMode.UNATTENDED;
        buildReview();
        publishRunStarted(ReviewMode.ASSISTED);

        setRunState(RunState.COMPLETED);

        assertThat(onFx(() -> review.allSegmentsOffered().get())).isFalse();
    }

    // IF a run in progress took the changed setting, THEN All segments would appear for a run that was not Unattended.
    @Test
    void allSegmentsOffered_runStartedUnattendedThenProviderChanges_staysOffered() {
        reviewMode = ReviewMode.UNATTENDED;
        buildReview();
        publishRunStarted(ReviewMode.UNATTENDED);

        reviewMode = ReviewMode.MANUAL;
        setRunState(RunState.COMPLETED);

        assertThat(onFx(() -> review.allSegmentsOffered().get())).isTrue();
    }

    // IF no run has started (a reopened project), THEN the mode now in force decides, read at the time it is asked.
    @Test
    void allSegmentsOffered_noRunYetAndProviderChanges_followsTheProvider() {
        reviewMode = ReviewMode.UNATTENDED;
        buildReview();

        reviewMode = ReviewMode.ASSISTED;
        setRunState(RunState.COMPLETED);

        assertThat(onFx(() -> review.allSegmentsOffered().get())).isFalse();
    }

    // IF Accept continued the run because the setting is Manual now, THEN a run started Unattended would be
    // resumed by the person's Accept that it never asked to pause for.
    @Test
    void acceptContinues_runStartedUnattendedWhileProviderSaysManual_isFalse() {
        reviewMode = ReviewMode.MANUAL;
        buildReview();
        final ReviewPauseFollower follower = buildFollower();
        publishRunStarted(ReviewMode.UNATTENDED);

        pauseAt("s-1");

        assertThat(onFx(() -> follower.acceptContinues().get())).isFalse();
    }

    // IF the run's mode were ignored, THEN a Manual run paused for review would not word Accept as Accept & continue.
    @Test
    void acceptContinues_runStartedManualWhileProviderSaysUnattended_isTrue() {
        reviewMode = ReviewMode.UNATTENDED;
        buildReview();
        final ReviewPauseFollower follower = buildFollower();
        publishRunStarted(ReviewMode.MANUAL);

        pauseAt("s-1");

        assertThat(onFx(() -> follower.acceptContinues().get())).isTrue();
    }

    // IF no run has published a mode, THEN a pause (reopened project) asks the provider at that moment.
    @Test
    void acceptContinues_noRunYetAndProviderChangesToManual_isTrue() {
        reviewMode = ReviewMode.UNATTENDED;
        buildReview();
        final ReviewPauseFollower follower = buildFollower();

        reviewMode = ReviewMode.MANUAL;
        pauseAt("s-1");

        assertThat(onFx(() -> follower.acceptContinues().get())).isTrue();
    }
}
