package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static ua.bookloom.pipeline.revision.RevisionBook.BOBBY;
import static ua.bookloom.pipeline.revision.RevisionBook.RUN_BOBBY;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;
import static ua.bookloom.pipeline.revision.RevisionBook.reply;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.review.ReviewFixtures;

/**
 * The consistency pass's first model step on an export: a flagged or audit-doubted segment is drafted again and the
 * new text is kept only when it is better and loses nothing the old one had. Fixtures follow the owner's Burning
 * Chrome run: a dropped sentence (seg 95), stripped quote marks (seg 102) and a lost name.
 */
class ConsistencyPassRetryTest {

    private static final PassOptions EXPORT = new PassOptions(true, false);
    private static final ContextSnapshot SNAPSHOT =
            new ContextSnapshot(List.of("Ранок."), List.of(), List.of(), null, "Keep the narrator's voice.");
    private static final String DRAFT = "draft";
    private static final String DROPPED = "Боббі зайшов.";
    private static final String WHOLE = "Боббі зайшов. Він сів.";
    private static final String QUOTED = "«Біжи, Боббі», — сказав я. Він побіг.";

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
        book.add(book.character("Bobby", "Боббі", Gender.MALE, false));
        book.model().answerTo("reviewer", Result.ok(new ChatResponse("{\"results\":[]}", FinishReason.STOP)));
    }

    private void flagged(final String segmentId, final String text) {
        ReviewFixtures.update(
                book.desk(),
                segmentId,
                record -> record.withStatus(SegmentStatus.FLAGGED)
                        .withMachineTarget(text, text)
                        .withFindings(List.of(new QaFinding("completeness", Severity.HIGH, "a sentence", "dropped"))));
        ReviewFixtures.withContext(book.desk(), segmentId, SNAPSHOT);
    }

    private void neighbourKeeps(final String text) {
        book.model().answerTo("consistency", reply(text));
    }

    // IF a flagged segment were never drafted again, THEN a dropped sentence (seg 95) would reach the book.
    @Test
    void run_flaggedSegmentWithADroppedSentence_isDraftedAgainAndTheBetterTextStored() {
        flagged(BOBBY, DROPPED);
        book.model().answerTo(DRAFT, reply(WHOLE));
        neighbourKeeps(WHOLE);

        final ConsistencyReport report = ok(book.runExport(EXPORT));

        assertThat(book.stored(BOBBY))
                .extracting(record -> record.machineTarget(), record -> record.status())
                .containsExactly(WHOLE, SegmentStatus.REVISED);
        assertThat(report.checks().retriedImproved()).isEqualTo(1);
        assertThat(report.notes()).contains("ch2 · p03: drafted again and improved");
    }

    // IF a retry that strips the quote marks were kept, THEN the dialogue of seg 102 would lose its « » again.
    @Test
    void run_retryThatLosesTheQuoteMarks_keepsTheOldText() {
        flagged(RUN_BOBBY, QUOTED);
        book.model().answerTo(DRAFT, reply("Біжи, Боббі, сказав я. Він побіг."));
        neighbourKeeps(QUOTED);

        final ConsistencyReport report = ok(book.runExport(EXPORT));

        assertThat(book.stored(RUN_BOBBY))
                .extracting(record -> record.machineTarget(), record -> record.status())
                .containsExactly(QUOTED, SegmentStatus.FLAGGED);
        assertThat(report.checks().retriedImproved()).isZero();
        assertThat(report.checks().refused()).containsExactly(entry(RevisionGuards.QUOTES, 1));
    }

    // IF a retry that drops the name the source calls out were kept, THEN "Bobby" would vanish from the book.
    @Test
    void run_retryThatDropsTheCalledOutName_keepsTheOldText() {
        flagged(RUN_BOBBY, QUOTED);
        book.model().answerTo(DRAFT, reply("«Біжи», — сказав я. Він побіг."));
        neighbourKeeps(QUOTED);

        final ConsistencyReport report = ok(book.runExport(EXPORT));

        assertThat(book.stored(RUN_BOBBY).machineTarget()).isEqualTo(QUOTED);
        assertThat(report.checks().refused()).containsExactly(entry(PassChecks.CHECKS, 1));
    }

    // IF an audit-doubted accepted segment were left out, THEN only the run's own flags would ever be retried.
    @Test
    void run_acceptedSegmentTheAuditDoubts_isDraftedAgainAndImproved() {
        book.decide(BOBBY, "Він зайшов. Він сів.", "Він зайшов. Він сів.");
        ReviewFixtures.withContext(book.desk(), BOBBY, SNAPSHOT);
        book.model().answerTo(DRAFT, reply(WHOLE));
        neighbourKeeps(WHOLE);

        final ConsistencyReport report = ok(book.runExport(EXPORT));

        assertThat(book.stored(BOBBY).machineTarget()).isEqualTo(WHOLE);
        assertThat(report.checks().retriedImproved()).isEqualTo(1);
    }

    // IF a clean accepted segment were retried, THEN the pass would cost a draft per paragraph of the book.
    @Test
    void run_cleanAcceptedSegment_isNotDraftedAgain() {
        book.decide(BOBBY, WHOLE, WHOLE);
        ReviewFixtures.withContext(book.desk(), BOBBY, SNAPSHOT);

        final ConsistencyReport report = ok(book.runExport(EXPORT));

        assertThat(book.model().requests()).isEmpty();
        assertThat(report.checks().retriedImproved()).isZero();
    }

    // IF the person's own text were retried, THEN the pass could overwrite what they wrote.
    @Test
    void run_personEditedFlaggedSegment_isNeverDraftedAgain() {
        flagged(BOBBY, DROPPED);
        ReviewFixtures.update(
                book.desk(),
                BOBBY,
                record -> record.withStatus(SegmentStatus.FLAGGED).withUserTarget(DROPPED, DROPPED));

        ok(book.runExport(EXPORT));

        assertThat(book.model().requests()).isEmpty();
        assertThat(book.stored(BOBBY).userTarget()).isEqualTo(DROPPED);
    }

    // IF a failed draft ended the pass, THEN one timeout would cost the export.
    @Test
    void run_draftCallFails_segmentIsSkippedAndCounted() {
        flagged(BOBBY, DROPPED);
        book.model().answerTo(DRAFT, Result.err(AppError.of(ErrorCode.timeout, "Slow", "Too long.")));
        neighbourKeeps(DROPPED);

        final ConsistencyReport report = ok(book.runExport(EXPORT));

        assertThat(book.stored(BOBBY).machineTarget()).isEqualTo(DROPPED);
        assertThat(report.checks().skipped()).isEqualTo(1);
    }

    // IF a cancel did not end the pass, THEN Cancel would wait for every doubted segment; what was stored stays.
    @Test
    void run_cancelledDuringTheSecondDraft_endsCancelledAndKeepsTheFirstImprovement() {
        flagged(BOBBY, DROPPED);
        flagged(RUN_BOBBY, QUOTED);
        book.model().answerTo(DRAFT, reply(WHOLE));
        book.model().answerTo(DRAFT, Result.err(AppError.of(ErrorCode.cancelled, "Cancelled", "Stopped.")));

        final Result<ConsistencyReport> ran = book.runExport(EXPORT);

        assertThat(ran.error()).isNotNull().extracting(AppError::code).isEqualTo(ErrorCode.cancelled);
        assertThat(book.stored(BOBBY).machineTarget()).isEqualTo(WHOLE);
        assertThat(book.stored(RUN_BOBBY).machineTarget()).isEqualTo(QUOTED);
    }

    // IF the step announced calls instead of segments, THEN the busy card's bar would run past its end.
    @Test
    void run_twoDoubtedSegments_announcesTwoUnitsAndEachOneDone() {
        flagged(BOBBY, DROPPED);
        flagged(RUN_BOBBY, QUOTED);
        book.model().answerTo(DRAFT, reply(WHOLE)).answerTo(DRAFT, reply(QUOTED));
        neighbourKeeps(WHOLE);
        neighbourKeeps(QUOTED);
        final List<String> announced = new ArrayList<>();
        final ModelCalls calls = new ModelCalls() {
            @Override
            public Result<ChatResponse> call(
                    final CallKind kind, @Nullable final String segmentId, final ChatRequest request) {
                return book.model().chat(request);
            }

            @Override
            public void planned(final Stage stage, final int count) {
                announced.add("planned " + stage + " " + count);
            }

            @Override
            public void advanced(final Stage stage, final int done) {
                announced.add("advanced " + stage + " " + done);
            }
        };

        ok(book.runExport(calls, EXPORT));

        assertThat(announced)
                .startsWith(
                        "planned GENDER_RETRY 0",
                        "planned RETRY_DOUBTED 2",
                        "advanced RETRY_DOUBTED 1",
                        "advanced RETRY_DOUBTED 2");
    }

    // IF the Max run's own backward revision drafted again, THEN every Max run would pay for a second retry pass.
    @Test
    void run_backwardRevisionOptions_draftsNothingAgain() {
        flagged(BOBBY, DROPPED);

        neighbourKeeps(DROPPED);

        ok(book.runExport(PassOptions.BACKWARD_REVISION));

        assertThat(book.model().requests())
                .noneMatch(request -> request.responseFormat() != null
                        && DRAFT.equals(request.responseFormat().name()));
        assertThat(book.stored(BOBBY).machineTarget()).isEqualTo(DROPPED);
        assertThat(book.stored(BOBBY).status()).isEqualTo(SegmentStatus.FLAGGED);
    }
}
