package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.review.RetryDraftTest.NOTE;
import static ua.bookloom.pipeline.review.RetryDraftTest.SNAPSHOT;
import static ua.bookloom.pipeline.review.RetryDraftTest.passing;
import static ua.bookloom.pipeline.review.ReviewFixtures.FLAGGED_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.accept;
import static ua.bookloom.pipeline.review.ReviewFixtures.flag;
import static ua.bookloom.pipeline.review.ReviewFixtures.latestRun;
import static ua.bookloom.pipeline.review.ReviewFixtures.withContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * A retry is shown what the run's first draft had around it: the paragraph after it as the book now translates it,
 * and — for its reviewer — the character sheet the first draft saw.
 */
class RetryDraftNeighboursTest {

    @TempDir
    private Path tempDir;

    // The run's batch showed the model what came next; a retry shows the next paragraph as it is now translated.
    @Test
    void retry_nextParagraphTranslated_showsItAfterThePreviousTranslations() {
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        accept(desk, "ch05.xhtml:12", "Вранці знову пішов дощ.");
        final ScriptedChatModel model = passing();

        ok(retry(desk, model));

        assertThat(userMessage(model.requests().getFirst())).contains("""
                        </PreviousTranslations>

                        [Next translated text — context only; do NOT re-translate it]
                        <NextTranslation>
                        Вранці знову пішов дощ.
                        </NextTranslation>""");
    }

    @Test
    void retry_nextParagraphNotTranslated_showsNoNextText() {
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        final ScriptedChatModel model = passing();

        ok(retry(desk, model));

        assertThat(userMessage(model.requests().getFirst())).doesNotContain("[Next translated text");
    }

    // The run's reviewer saw who the characters are; the retry's reviewer sees the sheet the first draft saw.
    @Test
    void retry_snapshotWithCharacters_showsTheReviewerTheCharacterSheet() {
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        withContext(
                desk,
                FLAGGED_ID,
                new ContextSnapshot(
                        SNAPSHOT.precedingTargets(),
                        SNAPSHOT.glossary(),
                        SNAPSHOT.tmHits(),
                        SNAPSHOT.summary(),
                        SNAPSHOT.styleSheet(),
                        List.of(),
                        List.of("Hale — male")));
        final ScriptedChatModel model = passing();

        ok(retry(desk, model));

        assertThat(userMessage(model.requests().get(1)))
                .contains("[Characters in these pairs — who they are]\nHale — male");
    }

    private Desk flaggedWithSnapshot(final JobState run) {
        final Desk desk = ReviewFixtures.epub(tempDir);
        latestRun(desk, run);
        flag(desk, FLAGGED_ID, "Чудовисько зустріло мене.");
        withContext(desk, FLAGGED_ID, SNAPSHOT);
        return desk;
    }

    private static Result<SegmentRecord> retry(final Desk desk, final ScriptedChatModel model) {
        return desk.retryDraft(ReviewMode.ASSISTED).retry(desk.projectId(), FLAGGED_ID, NOTE, true, model);
    }

    private static String userMessage(final ChatRequest request) {
        return request.messages().get(1).content();
    }

    private static SegmentRecord ok(final Result<SegmentRecord> result) {
        return Objects.requireNonNull(result.data(), () -> "expected success but got " + result.error());
    }
}
