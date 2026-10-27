package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.review.ReviewFixtures.FLAGGED_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.flag;
import static ua.bookloom.pipeline.review.ReviewFixtures.latestRun;
import static ua.bookloom.pipeline.review.ReviewFixtures.withContext;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * A retry asks for the review mode when it runs, so a setting changed between two retries applies to the second.
 *
 * <p>The mode reaches only the loop settings, which no decision reads today, so the proof is the question asked of the
 * provider, not an outcome.
 */
class RetryDraftReviewModeTest {

    @TempDir
    private Path tempDir;

    private Desk flaggedDesk() {
        final Desk desk = ReviewFixtures.epub(tempDir);
        latestRun(desk, JobState.PAUSED);
        flag(desk, FLAGGED_ID, "Чудовисько зустріло мене.");
        withContext(desk, FLAGGED_ID, RetryDraftTest.SNAPSHOT);
        return desk;
    }

    // IF the retry kept the mode it first saw, THEN a mode changed in Settings between two retries would not reach
    // the second one; each retry that reaches the model asks once and sees the current answer.
    @Test
    void retry_twiceWithTheModeChangedBetween_askedTheProviderOncePerRetryAndSawTheNewMode() {
        final Desk desk = flaggedDesk();
        final List<ReviewMode> answers = new ArrayList<>(List.of(ReviewMode.UNATTENDED, ReviewMode.MANUAL));
        final List<ReviewMode> given = new ArrayList<>();
        final RetryDraft retry = desk.retryDraft(() -> {
            final ReviewMode next = answers.removeFirst();
            given.add(next);
            return next;
        });

        retry.retry(desk.projectId(), FLAGGED_ID, null, false, RetryDraftTest.passing());
        retry.retry(desk.projectId(), FLAGGED_ID, null, false, RetryDraftTest.passing());

        assertThat(given).containsExactly(ReviewMode.UNATTENDED, ReviewMode.MANUAL);
    }
}
