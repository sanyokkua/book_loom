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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.JobState;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;
import ua.bookloom.api.project.SnapshotTerm;
import ua.bookloom.api.project.SnapshotTmHit;
import ua.bookloom.api.project.SnapshotTmHit.TmHitKind;
import ua.bookloom.api.project.TermType;
import ua.bookloom.pipeline.ScriptedChatModel;
import ua.bookloom.pipeline.review.ReviewFixtures.Desk;

/**
 * A retry is one fair second attempt: it replays the context the first draft saw, is decided by the same checks, judge
 * and acceptance rule with no repair round, never downgrades an accepted segment and never queues behind a running book.
 */
class RetryDraftTest {

    static final ContextSnapshot SNAPSHOT = new ContextSnapshot(
            List.of("Ніч була тиха й темна."),
            List.of(new SnapshotTerm("Hale", "Хейл", TermType.CHARACTER, Gender.MALE, false)),
            List.of(
                    new SnapshotTmHit(TmHitKind.EXACT, "The night was quiet.", "Ніч була тиха."),
                    new SnapshotTmHit(TmHitKind.CONTEXT, "The monster came.", "Чудовисько прийшло.")),
            "Хейл повернувся до замку.",
            "Keep the narrator's voice formal.");
    static final String NOTE = "keep it more formal";
    static final String TOO_SHORT = "Чудовисько тут";
    private static final String ACCEPTED_ID = "ch02.xhtml:3";
    private static final String ACCEPTED_TARGET = "Дощ ущух лише надвечір.";
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        assertThat(record.context()).isEqualTo(SNAPSHOT);
        assertThat(stored(desk, FLAGGED_ID)).isEqualTo(record);
    }

    @Test
    void retry_noteAndLowerTemperature_makesOneDraftAtTheLowerTemperatureThenOneJudgeOverThePair() {
        // one draft call at 0.1 carrying the note as its extra instruction, then the judge over that one pair as s1
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        final ScriptedChatModel model = passing();

        ok(retry(desk, model));

        assertThat(model.requests())
                .extracting(request -> formatOf(request), ChatRequest::temperature)
                .containsExactly(tuple("draft", 0.1), tuple("judge", 0.1));
        assertThat(userMessage(model.requests().getFirst())).contains("[Extra instruction]\n" + NOTE);
        assertThat(userMessage(model.requests().get(1)))
                .contains("[s1]\nSource: The monster met me at midnight.\nCandidate: " + MONSTER_TARGET);
    }

    @Test
    void retry_glossaryChangedSinceTheDraft_replaysTheSnapshotsTexts() {
        // the prompt carries what the first draft saw — Hale → Хейл — not today's Гейл
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        Objects.requireNonNull(desk.glossary()
                .add(new GlossaryEntry("g1", desk.projectId(), "Hale", "Гейл", TermType.CHARACTER, Gender.MALE, false))
                .data());
        final ScriptedChatModel model = passing();

        ok(retry(desk, model));

        final ChatRequest draft = model.requests().getFirst();
        assertThat(userMessage(draft))
                .contains(
                        "Хейл повернувся до замку.",
                        "Hale → Хейл (character, male)",
                        "The night was quiet. → Ніч була тиха.",
                        "<PreviousTranslations>\nНіч була тиха й темна.\n</PreviousTranslations>")
                .doesNotContain("Гейл", "The monster came.");
        assertThat(draft.messages().getFirst().content()).contains("Keep the narrator's voice formal.");
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

        assertThat(formatOf(model.requests().getFirst())).isEqualTo("draft");
    }

    @Test
    void retry_noRunYet_makesTheDraftRequest() {
        // a project that never ran has no run to wait for
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, "Чудовисько зустріло мене.");
        withContext(desk, FLAGGED_ID, SNAPSHOT);
        final ScriptedChatModel model = passing();

        ok(retry(desk, model));

        assertThat(formatOf(model.requests().getFirst())).isEqualTo("draft");
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
        assertThat(model.requests()).extracting(RetryDraftTest::formatOf).containsExactly("draft", "judge");
        assertThat(stored(desk, FLAGGED_ID)).isEqualTo(record);
    }

    @Test
    void retry_flaggedDraftFailingTheGate_keepsThePreviousMachineTarget() {
        // a retry whose reply never passes the placeholder gate has no target of its own, so the earlier one stays
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);
        final ScriptedChatModel model = new ScriptedChatModel()
                .answer(reply("Чудовисько ⟦g7⟧ зустріло мене опівночі."))
                .answer(reply("Чудовисько ⟦g7⟧ зустріло мене опівночі."));

        final SegmentRecord record = ok(retry(desk, model));

        assertThat(record.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(record.machineTarget()).isEqualTo("Чудовисько зустріло мене.");
        assertThat(record.findings()).extracting(QaFinding::raisedBy).contains("placeholder");
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
    void retry_recordWithoutSnapshot_isValidationWithNoRequest() {
        // with nothing recorded of the first draft's context there is nothing to replay
        final Desk desk = ReviewFixtures.epub(tempDir);
        flag(desk, FLAGGED_ID, "Чудовисько зустріло мене.");
        final ScriptedChatModel model = passing();

        final Result<SegmentRecord> result = retry(desk, model);

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(model.requests()).isEmpty();
    }

    @Test
    void retry_unknownSegment_isValidation() {
        // an id the project does not hold is refused, not thrown
        final Desk desk = flaggedWithSnapshot(JobState.PAUSED);

        final Result<SegmentRecord> result =
                desk.retryDraft(ReviewMode.ASSISTED).retry(desk.projectId(), "ch99.xhtml:0", null, false, passing());

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
    }

    @Test
    void retry_segmentTheRunDraftsInPieces_draftsEachPieceWithTheNoteAndJoinsThem() {
        // 200 sentences are above the run.s 1,200-token chunk budget: two piece drafts,
        // each at the lower temperature with the note, joined into one target that the judge then accepts
        final String source = IntStream.range(100, 300)
                .mapToObj(number -> "The night number " + number + " was calm.")
                .collect(Collectors.joining(" "));
        final Desk desk = ReviewFixtures.markdown(tempDir, source + "\n");
        latestRun(desk, JobState.PAUSED);
        flag(desk, "Book.md:0", "Ніч.");
        withContext(desk, "Book.md:0", SNAPSHOT);
        final PieceTranslatingModel model = new PieceTranslatingModel();

        final SegmentRecord record =
                ok(desk.retryDraft(ReviewMode.ASSISTED).retry(desk.projectId(), "Book.md:0", NOTE, true, model));

        assertThat(model.requests)
                .extracting(RetryDraftTest::formatOf, ChatRequest::temperature)
                .containsExactly(tuple("draft", 0.1), tuple("draft", 0.1), tuple("judge", 0.1));
        assertThat(model.requests.subList(0, 2))
                .allSatisfy(draft -> assertThat(userMessage(draft)).contains("[Extra instruction]\n" + NOTE));
        assertThat(userMessage(model.requests.getFirst())).contains("The night number 100 was calm.");
        assertThat(userMessage(model.requests.get(1))).contains("The night number 299 was calm.");
        assertThat(record)
                .extracting(SegmentRecord::status, SegmentRecord::path, SegmentRecord::reviewed)
                .containsExactly(SegmentStatus.ACCEPTED, SegmentPath.DRAFT, true);
        assertThat(record.machineTarget())
                .isEqualTo(IntStream.range(100, 300)
                        .mapToObj(number -> "Ніч номер " + number + " була дуже тихою.")
                        .collect(Collectors.joining(" ")));
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
                .answerTo("draft", reply(target))
                .answerTo(
                        "judge",
                        Result.ok(new ChatResponse("{\"score\":0.9,\"verdict\":\"accept\"}", FinishReason.STOP)));
    }

    private static Result<ChatResponse> reply(final String target) {
        try {
            return Result.ok(new ChatResponse(MAPPER.writeValueAsString(Map.of("target", target)), FinishReason.STOP));
        } catch (JsonProcessingException cause) {
            throw new AssertionError("could not encode scripted target", cause);
        }
    }

    private static String formatOf(final ChatRequest request) {
        return Objects.requireNonNull(request.responseFormat(), "format").name();
    }

    private static String userMessage(final ChatRequest request) {
        return request.messages().get(1).content();
    }

    private static SegmentRecord ok(final Result<SegmentRecord> result) {
        return Objects.requireNonNull(result.data(), () -> "expected success but got " + result.error());
    }

    private static AppError error(final Result<?> result) {
        return Objects.requireNonNull(result.error(), () -> "expected an error but got " + result.data());
    }

    /** Translates each night sentence of whatever text a draft shows it, and accepts whatever the judge is shown. */
    private static final class PieceTranslatingModel implements ChatModel {

        private static final Pattern TEXT = Pattern.compile("<Text>\n(.*)\n</Text>", Pattern.DOTALL);
        private static final Pattern NIGHT = Pattern.compile("The night number (\\d+) was calm\\.");
        private final List<ChatRequest> requests = new CopyOnWriteArrayList<>();

        @Override
        public Result<ChatResponse> chat(final ChatRequest request) {
            requests.add(request);
            final Matcher body = TEXT.matcher(userMessage(request));
            return "judge".equals(formatOf(request)) || !body.find()
                    ? Result.ok(new ChatResponse("{\"score\":0.9,\"verdict\":\"accept\"}", FinishReason.STOP))
                    : reply(NIGHT.matcher(body.group(1)).replaceAll("Ніч номер $1 була дуже тихою."));
        }
    }
}
