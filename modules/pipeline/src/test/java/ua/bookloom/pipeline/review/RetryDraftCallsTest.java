package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.review.ReviewFixtures.FLAGGED_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.flag;
import static ua.bookloom.pipeline.review.ReviewFixtures.latestRun;
import static ua.bookloom.pipeline.review.ReviewFixtures.withContext;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.CallSnapshotUpdated;
import ua.bookloom.api.pipeline.CallState;
import ua.bookloom.api.pipeline.JobEvent;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/** A retry from the review desk announces its model calls the way a run does, so the call panel can show them. */
class RetryDraftCallsTest {

    @TempDir
    private Path tempDir;

    // IF a retry called the model directly, THEN the panel would show nothing of what the retry sent and got back.
    @Test
    void retry_withAListener_announcesTheDraftCallWithItsMessagesAndReply() {
        final Desk desk = ReviewFixtures.epub(tempDir);
        latestRun(desk, JobState.PAUSED);
        flag(desk, FLAGGED_ID, "Чудовисько зустріло мене.");
        withContext(desk, FLAGGED_ID, RetryDraftTest.SNAPSHOT);
        final List<JobEvent> events = new CopyOnWriteArrayList<>();

        desk.retryDraft(ReviewMode.ASSISTED)
                .retry(desk.projectId(), FLAGGED_ID, RetryDraftTest.NOTE, true, RetryDraftTest.passing(), events::add);

        final List<CallSnapshot> drafts = events.stream()
                .filter(CallSnapshotUpdated.class::isInstance)
                .map(event -> ((CallSnapshotUpdated) event).snapshot())
                .filter(snapshot -> snapshot.kind() == CallKind.DRAFT && snapshot.state() == CallState.ANSWERED)
                .toList();
        assertThat(drafts).isNotEmpty();
        assertThat(drafts.getFirst().sent()).isNotEmpty();
        assertThat(drafts.getFirst().reply()).contains("target");
        assertThat(drafts.getFirst().segments()).extracting("id").containsExactly(FLAGGED_ID);
    }
}
