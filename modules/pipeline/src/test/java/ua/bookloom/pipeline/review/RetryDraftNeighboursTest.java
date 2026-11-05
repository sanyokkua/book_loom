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
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * A retry is shown the window a run's draft has around it, read from the stored targets now, and — for its reviewer —
 * today's character sheet of the names the segment holds.
 */
class RetryDraftNeighboursTest {

    @TempDir
    private Path tempDir;

    // A retry's window is the run's: the earlier pairs with the targets the run kept, and the next paragraph's source.
    @Test
    void retry_neighboursStored_showsTheKeptPreviousPairAndTheNextSource() {
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        accept(desk, "ch05.xhtml:10", "Ніч була тиха й темна.");
        accept(desk, "ch05.xhtml:12", "Вранці знову пішов дощ.");
        final ScriptedChatModel model = passing();

        ok(retry(desk, model));

        assertThat(userMessage(model.requests().getFirst()))
                .contains("""
                        Source: Chapter 5 line 10.
                        Translation: Ніч була тиха й темна.
                        """)
                .contains("do NOT translate or answer it]\nChapter 5 line 12.")
                .doesNotContain("Вранці знову пішов дощ.");
    }

    // A model copies the translation it is shown, so a flagged neighbour gives its source alone.
    @Test
    void retry_previousParagraphFlagged_showsItsSourceOnly() {
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        flag(desk, "ch05.xhtml:10", "Ніч була дуже темною.");
        final ScriptedChatModel model = passing();

        ok(retry(desk, model));

        assertThat(userMessage(model.requests().getFirst()))
                .contains("Source: Chapter 5 line 10.")
                .doesNotContain("Ніч була дуже темною.");
    }

    // The run's reviewer saw who the characters are; the retry's reviewer sees today's sheet of the names it holds.
    @Test
    void retry_characterTheSegmentNames_showsTheReviewerTheCharacterSheet() {
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        Objects.requireNonNull(desk.glossary()
                .add(GlossaryEntry.of(
                        "g1", desk.projectId(), "monster", "Чудовисько", TermType.CHARACTER, Gender.MALE, false))
                .data());
        final ScriptedChatModel model = passing();

        ok(retry(desk, model));

        assertThat(userMessage(model.requests().get(1)))
                .contains("[Characters in these pairs — who they are]\nmonster — male");
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

    // IF the retry read the first draft's stored sections, THEN two records that differ only in them would be retried
    // differently; the retry builds from the project as it is now.
    @Test
    void retry_storedSectionsDiffer_sameRequest() {
        final Desk plain = flaggedWithSnapshot(JobState.PAUSED);
        final ScriptedChatModel plainModel = passing();
        ok(retry(plain, plainModel));
        final Desk batched = flaggedWithSnapshot(JobState.PAUSED);
        withContext(
                batched,
                FLAGGED_ID,
                new ContextSnapshot(
                        "beef",
                        List.of(new PromptSection(
                                "items", "Items:", PromptSection.Origin.USER, List.of("<s id=\"1\">The monster.</s>"))),
                        List.of(),
                        false));
        final ScriptedChatModel batchedModel = passing();

        ok(retry(batched, batchedModel));

        assertThat(userMessage(batchedModel.requests().getFirst()))
                .isEqualTo(userMessage(plainModel.requests().getFirst()));
    }
}
