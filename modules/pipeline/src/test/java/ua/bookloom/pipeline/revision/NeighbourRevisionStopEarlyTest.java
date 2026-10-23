package ua.bookloom.pipeline.revision;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.revision.RevisionBook.DANNY;
import static ua.bookloom.pipeline.revision.RevisionBook.SAM_MET_HALE;
import static ua.bookloom.pipeline.revision.RevisionBook.ok;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.review.ReviewFixtures;

/**
 * A pass told to stop early sends no further call, leaves every unreached paragraph as stored and counts it as not
 * reread, so the report is honest about what the last check did not reach.
 */
class NeighbourRevisionStopEarlyTest {

    private static final String BEFORE = "Сем зустрів Хейла.";

    @TempDir
    private Path tempDir;

    private RevisionBook book;

    @BeforeEach
    void openBook() {
        book = RevisionBook.open(tempDir);
        flagged(DANNY);
        flagged(SAM_MET_HALE);
    }

    private void flagged(final String segmentId) {
        ReviewFixtures.update(
                book.desk(),
                segmentId,
                record -> record.withStatus(SegmentStatus.FLAGGED)
                        .withMachineTarget(BEFORE, BEFORE)
                        .withPath(SegmentPath.REPAIRED));
    }

    private ModelCalls stoppingEarly() {
        return new ModelCalls() {
            @Override
            public Result<ChatResponse> call(
                    final ua.bookloom.api.pipeline.CallKind kind,
                    final @org.jspecify.annotations.Nullable String segmentId,
                    final ua.bookloom.api.llm.ChatRequest request) {
                return book.model().chat(request);
            }

            @Override
            public boolean isStoppingEarly() {
                return true;
            }
        };
    }

    // IF a pass told to stop still sent its calls, THEN Write now would wait for the whole check.
    @Test
    void run_stopEarlyFlagSetBeforeTheStage_sendsNoCallAndCountsEveryPlannedItemNotReread() {
        final ConsistencyReport report = ok(book.runExport(stoppingEarly(), PassOptions.BACKWARD_REVISION));

        assertThat(book.model().requests()).isEmpty();
        assertThat(report.checks().notReread()).isEqualTo(2);
        assertThat(report.neighbourFixes()).isZero();
    }

    // IF an unreached paragraph were touched, THEN the file would hold text nobody checked and nobody asked for.
    @Test
    void run_stopEarlyFlagSetBeforeTheStage_leavesTheStoredTextAsItWas() {
        ok(book.runExport(stoppingEarly(), PassOptions.BACKWARD_REVISION));

        assertThat(book.stored(DANNY).machineTarget()).isEqualTo(BEFORE);
        assertThat(book.stored(SAM_MET_HALE).machineTarget()).isEqualTo(BEFORE);
    }

    // IF the stop counted its paragraphs as reread, THEN the report would claim a check that never happened.
    @Test
    void run_noStopEarly_countsNothingNotReread() {
        book.model().answerTimes(2, RevisionBook.reply(BEFORE));

        final ConsistencyReport report = ok(book.runExport(PassOptions.BACKWARD_REVISION));

        assertThat(report.checks().notReread()).isZero();
        assertThat(book.model().requests()).hasSize(2);
    }
}
