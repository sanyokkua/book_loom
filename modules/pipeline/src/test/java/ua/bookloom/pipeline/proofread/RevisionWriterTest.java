package ua.bookloom.pipeline.proofread;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.proofread.SweepBook.HALE_LEFT;
import static ua.bookloom.pipeline.proofread.SweepBook.ok;

import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.Severity;

/**
 * A name swap by the code sweep never hides a real problem: a FLAGGED segment takes the swapped name but stays FLAGGED
 * — in the review list too — unless every finding it carries is a glossary finding the swap fixes.
 */
class RevisionWriterTest {

    private static final String OLD_TARGET = "Хейл пішов.";
    private static final String SWEPT_TARGET = "Гейл пішов.";
    private static final QaFinding OMISSION = new QaFinding("omission", Severity.MEDIUM, "drops a clause", "judge");

    @TempDir
    private Path tempDir;

    private SweepBook book;

    @BeforeEach
    void openBook() {
        book = SweepBook.open(tempDir);
    }

    // The segment is FLAGGED for an omission; the swap gives it the new name but the omission is still there to review.
    @Test
    void store_flaggedWithOmissionSwept_takesTheNameAndStaysFlaggedInTheQueue() {
        decided(SegmentStatus.FLAGGED, OMISSION);

        sweptAfterRename();

        final SegmentRecord record = book.stored(HALE_LEFT);
        assertThat(record.machineTarget()).isEqualTo(SWEPT_TARGET);
        assertThat(record.status()).isEqualTo(SegmentStatus.FLAGGED);
        assertThat(record.findings()).containsExactly(OMISSION);
        assertThat(flaggedQueue()).contains(HALE_LEFT);
    }

    // Only a glossary finding, fixed by the swap: REVISED and off the review list, the finding kept as history.
    @Test
    void store_flaggedWithOnlyAGlossaryFindingSwept_becomesRevisedAndKeepsTheFinding() {
        final QaFinding glossary =
                new QaFinding("glossary", Severity.MEDIUM, "a locked term's rendering is missing", "glossary");
        decided(SegmentStatus.FLAGGED, glossary);

        sweptAfterRename();

        final SegmentRecord record = book.stored(HALE_LEFT);
        assertThat(record.machineTarget()).isEqualTo(SWEPT_TARGET);
        assertThat(record.status()).isEqualTo(SegmentStatus.REVISED);
        assertThat(record.findings()).containsExactly(glossary);
        assertThat(flaggedQueue()).doesNotContain(HALE_LEFT);
    }

    // The status after the swap, by the status and the one finding the segment held before it.
    @ParameterizedTest
    @CsvSource({
        "ACCEPTED, omission, length, ACCEPTED",
        "FLAGGED, glossary, locked-term, REVISED",
        "FLAGGED, language, script, FLAGGED",
        "FLAGGED, meaning, judge, FLAGGED"
    })
    void store_lockedNameSwept_statusFollowsTheFindings(
            final SegmentStatus before, final String kind, final String raisedBy, final SegmentStatus after) {
        decided(before, new QaFinding(kind, Severity.LOW, "note", raisedBy));

        sweptAfterRename();

        assertThat(book.stored(HALE_LEFT))
                .extracting(SegmentRecord::machineTarget, SegmentRecord::status)
                .containsExactly(SWEPT_TARGET, after);
    }

    // A FLAGGED segment with no finding recorded (a low score) has nothing the swap can be said to fix.
    @Test
    void store_flaggedWithoutFindingSwept_staysFlagged() {
        decided(SegmentStatus.FLAGGED, null);

        sweptAfterRename();

        assertThat(book.stored(HALE_LEFT))
                .extracting(SegmentRecord::machineTarget, SegmentRecord::status)
                .containsExactly(SWEPT_TARGET, SegmentStatus.FLAGGED);
    }

    private void decided(final SegmentStatus status, @Nullable final QaFinding finding) {
        book.decide(HALE_LEFT, status, OLD_TARGET, OLD_TARGET);
        book.findings(HALE_LEFT, finding == null ? List.of() : List.of(finding));
    }

    // Hale was locked as Хейл when the segment was decided; the person renames him Гейл, then the sweep runs.
    private void sweptAfterRename() {
        final GlossaryEntry before = book.add(book.character("Hale", "Хейл", Gender.MALE, true));
        book.change(before, book.character("Hale", "Гейл", Gender.MALE, true));
        ok(book.sweep());
    }

    private List<String> flaggedQueue() {
        return ok(book.desk().queries().queue(book.desk().projectId(), ReviewFilter.ALL_FLAGGED)).stream()
                .map(SegmentView::segmentId)
                .toList();
    }
}
