package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static ua.bookloom.pipeline.revision.RevisionBook.BOBBY;
import static ua.bookloom.pipeline.revision.RevisionBook.RUN_BOBBY;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;
import static ua.bookloom.pipeline.revision.RevisionBook.reply;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.Severity;
import ua.bookloom.pipeline.review.ReviewFixtures;

/** Where the pass starts, and how much of a paragraph its answer may rewrite. */
class ConsistencyPassPolicyTest {

    private static final PassOptions EXPORT = new PassOptions(true, false);
    private static final ContextSnapshot SNAPSHOT =
            new ContextSnapshot(List.of("Ранок."), List.of(), List.of(), null, "Keep the narrator's voice.");
    private static final String OLD = "Сем зустрів давнього друга.";
    private static final String REWRITTEN = "Самуїл побачив старого приятеля.";
    private static final String GOOD = "Боббі зайшов, потім сів.";

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
    }

    private void flagged(final String segmentId, final String text, final String raisedBy) {
        ReviewFixtures.update(
                book.desk(),
                segmentId,
                record -> record.withStatus(SegmentStatus.FLAGGED)
                        .withMachineTarget(text, text)
                        .withFindings(List.of(new QaFinding("fluency", Severity.HIGH, "a defect", raisedBy))));
        ReviewFixtures.withContext(book.desk(), segmentId, SNAPSHOT);
    }

    // IF the pass went in reading order, THEN a call that runs out of time would be spent on the soft finding of an
    // early paragraph while the mixed-script word of a later one stayed.
    @Test
    void run_twoFlaggedSegments_theOneWithABlockingFindingIsDraftedFirst() {
        flagged(BOBBY, GOOD, "reviewer");
        flagged(RUN_BOBBY, "Біжи, Боббі, — сказав я.", "quote-balance");
        book.model().answerTo("draft", Result.err(AppError.of(ErrorCode.cancelled, "Cancelled", "Stopped.")));

        final Result<ConsistencyReport> ran = book.runExport(EXPORT);

        assertThat(ran.error()).isNotNull();
        assertThat(book.model().requests()).hasSize(1);
        assertThat(book.userMessage(0)).contains("Run, Bobby");
    }

    // IF an answer that rewrote most of a sound paragraph were stored, THEN the pass would trade a translation for a
    // different one with the same defects.
    @Test
    void run_neighbourAnswerRewritesMoreThanTheCap_isRefusedByTheRewriteCapRule() {
        accepted(SAM_MET_HALE, OLD);
        book.model().answerTo("consistency", reply(REWRITTEN));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo(OLD);
        assertThat(report.checks().refused()).containsExactly(entry(RevisionGuards.REWRITE_CAP_RULE, 1));
        assertThat(report.neighbourFixes()).isZero();
    }

    // The cap is for sound paragraphs: one with a blocking finding is the defect the answer is there to remove.
    @Test
    void run_neighbourAnswerRewritesAParagraphWithABlockingFinding_isStored() {
        accepted(SAM_MET_HALE, OLD);
        ReviewFixtures.update(
                book.desk(),
                SAM_MET_HALE,
                record -> record.withStatus(SegmentStatus.FLAGGED)
                        .withFindings(List.of(new QaFinding("language", Severity.HIGH, "mixed", "script-purity"))));
        book.model().answerTo("consistency", reply(REWRITTEN));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo(REWRITTEN);
        assertThat(report.neighbourFixes()).isEqualTo(1);
    }

    // IF a revision that dropped a comma-separated clause were stored, THEN the translation would lose a part of the
    // sentence (p160).
    @Test
    void run_neighbourAnswerDropsAClause_isRefusedByTheClausesRule() {
        accepted(SAM_MET_HALE, "Сем, зустрівши Гейла, привітався.");
        book.model().answerTo("consistency", reply("Сем зустрівши Гейла, привітався."));

        final ConsistencyReport report = ok(book.run(true));

        assertThat(report.checks().refused()).containsExactly(entry(RevisionGuards.CLAUSES, 1));
    }

    private void accepted(final String segmentId, final String text) {
        ReviewFixtures.update(
                book.desk(),
                segmentId,
                record -> record.withStatus(SegmentStatus.ACCEPTED)
                        .withMachineTarget(text, text)
                        .withPath(SegmentPath.REPAIRED));
    }
}
