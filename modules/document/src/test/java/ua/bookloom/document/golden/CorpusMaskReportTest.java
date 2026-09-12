package ua.bookloom.document.golden;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Task 10.1's instruction to "thread it through {@code CorpusJson} and {@code CorpusTsv} so a run's report
 * carries the new columns": both report shapes are exercised directly here, against hand-built
 * {@link CorpusBookOutcome} values, so a run over the real corpus is never needed to prove the wiring.
 */
class CorpusMaskReportTest {

    // WHEN the corpus verification runs, THEN each book's recorded outcome carries its total
    // placeholder count and its largest per-segment count, in the JSONL report a run writes.
    @Test
    void jsonOf_completedMaskOutcome_carriesPlaceholderStatisticsAndTheNotMeasuredSkipCountAsNull() {
        final CorpusBookOutcome outcome =
                bookOutcomeWithMask(new CorpusMaskOutcome.Completed(3, 2, 2, 1, true, 0, null, null));

        final String json = CorpusJson.jsonOf(outcome);

        assertThat(json)
                .contains("\"mask\":{")
                .contains("\"segmentCount\":3")
                .contains("\"totalPlaceholders\":2")
                .contains("\"maxPlaceholdersInOneSegment\":2")
                .contains("\"segmentsWithPlaceholders\":1")
                .contains("\"ok\":true")
                .contains("\"mismatchCount\":0")
                .contains("\"skippedCodeOnlyBlocks\":null")
                .contains("\"failureMessage\":null");
    }

    // WHEN the verification runs over a corpus in which one book fails to open, THEN the
    // book's report entry records the mask probe as not attempted rather than a fabricated statistic.
    @Test
    void jsonOf_notAttemptedMaskOutcome_recordsNotAttemptedStatus() {
        final CorpusBookOutcome outcome = bookOutcomeWithMask(new CorpusMaskOutcome.NotAttempted());

        final String json = CorpusJson.jsonOf(outcome);

        assertThat(json).contains("\"mask\":{\"status\":\"notAttempted\"}");
    }

    // WHEN the corpus verification runs, THEN each book's recorded outcome carries its total
    // placeholder count and its largest per-segment count, in the summary TSV row a run writes alongside the
    // JSONL report.
    @Test
    void rowOf_completedMaskOutcome_includesMaskColumnsAtTheirDocumentedPositions() {
        final CorpusBookOutcome outcome =
                bookOutcomeWithMask(new CorpusMaskOutcome.Completed(3, 2, 2, 1, true, 0, null, null));

        final String[] cells = CorpusTsv.rowOf(outcome).split("\t", -1);

        assertThat(cells[15]).isEqualTo("completed");
        assertThat(cells[16]).isEqualTo("true");
        assertThat(cells[17]).isEqualTo("2");
        assertThat(cells[18]).isEqualTo("2");
        assertThat(cells[19]).isEqualTo("1");
        assertThat(cells[20]).isEqualTo("notMeasured");
    }

    // WHEN the verification runs over a corpus in which one book fails to open, THEN the
    // book's summary row records the mask probe as not attempted, distinctly from a measured zero.
    @Test
    void rowOf_notAttemptedMaskOutcome_recordsDashesAtTheDocumentedPositions() {
        final CorpusBookOutcome outcome = bookOutcomeWithMask(new CorpusMaskOutcome.NotAttempted());

        final String[] cells = CorpusTsv.rowOf(outcome).split("\t", -1);

        assertThat(cells[15]).isEqualTo("notAttempted");
        assertThat(cells[16]).isEqualTo("-");
        assertThat(cells[17]).isEqualTo("-");
        assertThat(cells[18]).isEqualTo("-");
        assertThat(cells[19]).isEqualTo("-");
        assertThat(cells[20]).isEqualTo("-");
    }

    private static CorpusBookOutcome bookOutcomeWithMask(CorpusMaskOutcome mask) {
        return new CorpusBookOutcome(
                "book.md",
                new CorpusOpenOutcome.Opened(
                        "MARKDOWN", "UTF-8", false, null, 1, 3, Map.of("PARAGRAPH", 3), 1, 5, 9, 12, 0, 5L, 1.0),
                new CorpusIdentityOutcome.NotAttempted(),
                new CorpusFixedPointOutcome.NotAttempted(),
                new CorpusMutationOutcome.NotAttempted(),
                new CorpusIdempotenceOutcome.NotAttempted(),
                mask,
                new CorpusResourceOutcome(5L, 100L));
    }
}
