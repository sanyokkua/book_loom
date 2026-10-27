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
