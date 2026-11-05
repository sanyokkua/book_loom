package ua.bookloom.pipeline.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static ua.bookloom.pipeline.review.ReviewFixtures.FLAGGED_ID;
import static ua.bookloom.pipeline.review.ReviewFixtures.MONSTER_TARGET;
import static ua.bookloom.pipeline.review.ReviewFixtures.accept;
import static ua.bookloom.pipeline.review.ReviewFixtures.flag;
import static ua.bookloom.pipeline.review.ReviewFixtures.latestRun;
import static ua.bookloom.pipeline.review.ReviewFixtures.stored;
import static ua.bookloom.pipeline.review.ReviewFixtures.update;
import static ua.bookloom.pipeline.review.ReviewFixtures.withContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.PromptSection;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Narrator;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.SequenceJobs;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.TestReplies;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * A retry is one fair second attempt: it builds its context from the project as it stands now, is decided by the same checks, reviewer
 * and acceptance rule with no repair round, never downgrades an accepted segment and never queues behind a running book.
 */
class RetryDraftTest {

    static final ContextSnapshot SNAPSHOT = new ContextSnapshot(
            "f00d",
            List.of(new PromptSection(
                    "items", "[Items]", PromptSection.Origin.USER, List.of("monster → потвора (term)"))),
            List.of(),
            false);
    static final String NOTE = "keep it more formal";
    static final String DRAFT = "draft";
    static final String TOO_SHORT = "Чудовисько тут";
    private static final String ACCEPTED_ID = "ch02.xhtml:3";
    private static final String ACCEPTED_TARGET = "Дощ ущух лише надвечір.";
    private static final String LIGHTHOUSE_ID = "Book.md:1";
    private static final String LIGHTHOUSE_TARGET = "Маяк стояв на скелі.";

    @TempDir
    private Path tempDir;

    @Test
    void retry_pausedRunWithNoteAndLowerTemperature_acceptsAsDraftedAndReviewed() {
        // a passing retry is ACCEPTED with its new machine target, path DRAFT, marked reviewed and stored
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        final ScriptedChatModel model = passing();

        final SegmentRecord record = ok(retry(desk, model));

        assertThat(record.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(record.path()).isEqualTo(SegmentPath.DRAFT);
        assertThat(record.reviewed()).isTrue();
        assertThat(record.machineTarget()).isEqualTo(MONSTER_TARGET);
        assertThat(record.maskedMachineTarget()).isEqualTo(MONSTER_TARGET);
        assertThat(Objects.requireNonNull(record.context(), "snapshot").retry()).isTrue();
        assertThat(stored(desk, FLAGGED_ID)).isEqualTo(record);
    }

    @Test
    void retry_noteAndLowerTemperature_makesOneDraftAtTheLowerTemperatureThenOneReviewerOverThePair() {
        // one draft call at 0.1 carrying the note as its extra instruction, then the reviewer over that one pair as s1
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        final ScriptedChatModel model = passing();

        ok(retry(desk, model));

        assertThat(model.requests())
                .extracting(RetryDraftTest::formatOf, ChatRequest::temperature)
                .containsExactly(tuple(DRAFT, 0.1), tuple("reviewer", 0.0));
        assertThat(userMessage(model.requests().getFirst())).contains("[Extra instruction]\n" + NOTE);
        assertThat(userMessage(model.requests().get(1)))
                .contains(
                        "<Pair id=\"s1\"><Source>The monster met me at midnight.</Source><Candidate>" + MONSTER_TARGET);
    }

    @Test
    void retry_glossaryTargetChanged_usesNewTarget() {
        // the first draft's stored sections said monster → потвора; the retry builds from today's glossary, sends
        // чудовисько and stores the sections it sent, marked as a retry's
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        Objects.requireNonNull(desk.glossary()
                .add(GlossaryEntry.of(
                        "g1", desk.projectId(), "monster", "чудовисько", TermType.TERM, Gender.NEUTER, false))
                .data());
        Objects.requireNonNull(desk.glossary()
                .add(GlossaryEntry.of("g2", desk.projectId(), "Hale", "Хейл", TermType.CHARACTER, Gender.MALE, false))
                .data());
        final ScriptedChatModel model = passing();

        final SegmentRecord record = ok(retry(desk, model));

        assertThat(userMessage(model.requests().getFirst()))
                .contains("monster → чудовисько")
                .doesNotContain("потвора", "Hale →");
        final ContextSnapshot stored = Objects.requireNonNull(record.context(), "snapshot");
        assertThat(stored.retry()).isTrue();
        assertThat(stored.sections())
                .flatExtracting(PromptSection::lines)
                .anySatisfy(line -> assertThat(line).contains("monster → чудовисько"))
                .noneSatisfy(line -> assertThat(line).contains("потвора"));
    }

    @Test
    void retry_nothingChanged_sameContext() {
        // a run drafts each paragraph alone; Try again with nothing changed sends the very messages that draft was sent
        // and stores the same frame and sections, now marked as the retry's
        final Path book = TestBooks.markdown(tempDir.resolve("Book.md"), """
                The old man walked to the harbour.

                The lighthouse stood on the cliff.

                The boats came back at dusk.
                """);
        final SequenceJobs.Prepared prepared =
                SequenceJobs.importBook(book, SequenceJobs.brief("en", "uk", QualityDial.FAST, Narrator.unspecified()));
        final ScriptedChatModel run = new ScriptedChatModel()
                .answer(reply("Старий пішов до гавані."))
                .answer(reply(LIGHTHOUSE_TARGET))
                .answer(reply("Човни повернулися надвечір."));
        Objects.requireNonNull(SequenceJobs.runAlone(prepared, run).data(), "job report");
        final ContextSnapshot drafted =
                Objects.requireNonNull(storedIn(prepared, LIGHTHOUSE_ID).context(), "drafted snapshot");
        final ScriptedChatModel again = new ScriptedChatModel().answer(reply(LIGHTHOUSE_TARGET));

        final SegmentRecord retried =
                ok(SequenceJobs.retryDraft(prepared).retry(prepared.projectId(), LIGHTHOUSE_ID, null, false, again));

        assertThat(again.requests().getFirst().messages())
                .isEqualTo(run.requests().get(1).messages());
        assertThat(retried.context())
                .isEqualTo(new ContextSnapshot(drafted.frameHash(), drafted.sections(), List.of(), true));
    }

    @Test
    void retry_withoutLowerTemperature_draftsAtTheDraftTemperature() {
        // an unchecked "Lower temperature" keeps the draft's own 0.2
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        final ScriptedChatModel model = passing();

        ok(desk.retryDraft(ReviewMode.ASSISTED).retry(desk.projectId(), FLAGGED_ID, null, false, model));

        assertThat(model.requests().getFirst().temperature()).isEqualTo(0.2);
        assertThat(userMessage(model.requests().getFirst())).doesNotContain("[Extra instruction]");
    }

    @ParameterizedTest
    @EnumSource(
            value = JobState.class,
            names = {"PAUSED", "COMPLETED", "CANCELLED", "FAILED"})
    void retry_runNotRunning_makesTheDraftRequest(final JobState state) {
        // a paused, stopped, completed or failed run holds no place, so the retry reaches the model at once
        final Desk desk = flaggedWithSnapshot(state);
        final ScriptedChatModel model = passing();

        ok(retry(desk, model));

        assertThat(formatOf(model.requests().getFirst())).isEqualTo(DRAFT);
    }

    @Test
    void retry_noRunYet_makesTheDraftRequest() {
        // a project that never ran has no run to wait for
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, "Чудовисько зустріло мене.");
        withContext(desk, FLAGGED_ID, SNAPSHOT);
        final ScriptedChatModel model = passing();

        ok(retry(desk, model));

        assertThat(formatOf(model.requests().getFirst())).isEqualTo(DRAFT);
    }

    @Test
    void retry_runningRun_isBusyAndMakesNoRequest() {
        // a retry never queues behind a running book: busy, read from the run record, before any call
        final Desk desk = flaggedWithSnapshot(JobState.RUNNING);
        final SegmentRecord before = stored(desk, FLAGGED_ID);
        final ScriptedChatModel model = passing();

        final Result<SegmentRecord> result = retry(desk, model);

        assertThat(error(result).code()).isEqualTo(ErrorCode.busy);
        assertThat(model.requests()).isEmpty();
        assertThat(stored(desk, FLAGGED_ID)).isEqualTo(before);
    }

    @Test
    void retry_flaggedTooShortTarget_staysFlaggedWithTheLengthOmissionAndNoRepair() {
        // 14 characters for 31 (ratio 0.45) fails the length band: FLAGGED, not reviewed, no directed fix follows
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        final ScriptedChatModel model = answering(TOO_SHORT);

        final SegmentRecord record = ok(retry(desk, model));

        assertThat(record.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(record.reviewed()).isFalse();
        assertThat(record.machineTarget()).isEqualTo(TOO_SHORT);
        assertThat(record.findings())
                .extracting(QaFinding::kind, QaFinding::severity, QaFinding::raisedBy)
                .contains(tuple("omission", Severity.MEDIUM, "length"));
        assertThat(model.requests()).extracting(RetryDraftTest::formatOf).containsExactly(DRAFT);
        assertThat(stored(desk, FLAGGED_ID)).isEqualTo(record);
    }

    @Test
    void retry_flaggedDraftFailingTheGate_keepsThePreviousMachineTarget() {
        // a retry whose reply is refused as unusable — a control character no book can hold is rejected whole at the
        // reply, never stripped — has no target of its own, so the earlier one stays
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(reply("Чудовисько\u0008 зустріло мене опівночі."))
                .answer(reply("Чудовисько\u0008 зустріло мене опівночі."));

        final SegmentRecord record = ok(retry(desk, model));

        assertThat(record.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(record.machineTarget()).isEqualTo("Чудовисько зустріло мене.");
        assertThat(record.findings()).extracting(QaFinding::raisedBy).contains("reply");
    }

    @Test
    void retry_acceptedTooShortTarget_keepsTheStoredRecordAndAnswersTheNewFinding() {
        // a failing retry never downgrades an ACCEPTED segment; 9 characters for 36 fails the length band, and the
        // answer only reports what the retry found
        final Desk desk = ReviewFixtures.epub(tempDir);
        latestRun(desk, JobState.COMPLETED);
        accept(desk, ACCEPTED_ID, ACCEPTED_TARGET);
        withContext(desk, ACCEPTED_ID, SNAPSHOT);
        final SegmentRecord before = stored(desk, ACCEPTED_ID);
        final ScriptedChatModel model = answering("Дощ ущух.");

        final SegmentRecord answer =
                ok(desk.retryDraft(ReviewMode.ASSISTED).retry(desk.projectId(), ACCEPTED_ID, null, false, model));

        assertThat(stored(desk, ACCEPTED_ID)).isEqualTo(before);
        assertThat(answer.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(answer.machineTarget()).isEqualTo(ACCEPTED_TARGET);
        assertThat(answer.findings())
                .extracting(QaFinding::kind, QaFinding::severity, QaFinding::raisedBy)
                .contains(tuple("omission", Severity.MEDIUM, "length"));
    }

    @ParameterizedTest
    @EnumSource(
            value = SegmentStatus.class,
            names = {"PENDING", "REVISED"})
    void retry_pendingOrRevisedSegment_isValidationWithNoRequest(final SegmentStatus status) {
        // only a FLAGGED or an ACCEPTED segment is retried
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        update(desk, FLAGGED_ID, record -> record.withStatus(status));
        final ScriptedChatModel model = passing();

        final Result<SegmentRecord> result = retry(desk, model);

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void retry_recordWithoutSnapshot_draftsFromTheProjectAsItIsNow() {
        // the retry never reads the first draft's snapshot, so a record that holds none is drafted again all the same
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, "Чудовисько зустріло мене.");
        final ScriptedChatModel model = passing();

        final SegmentRecord record = ok(retry(desk, model));

        assertThat(formatOf(model.requests().getFirst())).isEqualTo(DRAFT);
        assertThat(record.status()).isEqualTo(SegmentStatus.ACCEPTED);
        assertThat(Objects.requireNonNull(record.context(), "snapshot").retry()).isTrue();
    }

    @Test
    void retry_unknownSegment_isValidation() {
        // an id the project does not hold is refused, not thrown
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);

        final Result<SegmentRecord> result =
                desk.retryDraft(ReviewMode.ASSISTED).retry(desk.projectId(), "ch99.xhtml:0", null, false, passing());

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
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

    static ScriptedChatModel passing() {
        return answering(MONSTER_TARGET);
    }

    static ScriptedChatModel answering(final String target) {
        return new ScriptedChatModel()
                .answerTo(DRAFT, reply(target))
                .answerTo("reviewer", Result.ok(new ChatResponse("{\"results\":[]}", FinishReason.STOP)));
    }

    static Result<ChatResponse> reply(final String target) {
        return Result.ok(new ChatResponse(TestReplies.targetReply(target), FinishReason.STOP));
    }

    private static SegmentRecord storedIn(final SequenceJobs.Prepared prepared, final String id) {
        return Objects.requireNonNull(
                        prepared.stores()
                                .segments()
                                .find(prepared.projectId(), id)
                                .data(),
                        "find " + id)
                .orElseThrow();
    }

    // A translate-family call is told apart by its kind, since every one asks for the batch schema.
    static String formatOf(final ChatRequest request) {
        return ScriptedChatModel.callName(request);
    }

    static String userMessage(final ChatRequest request) {
        return request.messages().get(1).content();
    }

    static SegmentRecord ok(final Result<SegmentRecord> result) {
        return Objects.requireNonNull(result.data(), () -> "expected success but got " + result.error());
    }

    private static AppError error(final Result<?> result) {
        return Objects.requireNonNull(result.error(), () -> "expected an error but got " + result.data());
    }
}
