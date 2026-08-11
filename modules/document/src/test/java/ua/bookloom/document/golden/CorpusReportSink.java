package ua.bookloom.document.golden;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import lombok.RequiredArgsConstructor;

/**
 * Writes the corpus verification's report: one JSON object per book per line (greppable, stable keys, flushed
 * after every book so a crash still leaves a partial report — see {@link CorpusJson}) and a summary TSV beside
 * it, one row per book (design.md D6, task 11.1).
 *
 * <p>Book identity in the report is the file name only — the corpus is third-party copyrighted material and no
 * book content is ever copied into either file.
 */
@RequiredArgsConstructor
final class CorpusReportSink implements AutoCloseable {

    static final String JSONL_FILE_NAME = "corpus-verification.jsonl";
    static final String SUMMARY_FILE_NAME = "corpus-verification-summary.tsv";

    private static final String TSV_HEADER =
            "file\topen\tunits\tsegments\tidentity\tidentityCanonical\tfixedPoint\tfixedPointCanonical\t"
                    + "mutation\tmutationCountsMatch\tmutationTuplesMatch\tmutationMarkerClean\t"
                    + "idempotence\tidempotenceTuplesMatch\tidempotenceSourceMatch\twallClockMs\tfileSizeBytes";

    private final BufferedWriter jsonl;
    private final BufferedWriter tsv;

    /**
     * Opens both report files under {@code reportDir}, creating it if necessary, and writes the TSV header.
     *
     * @param reportDir the directory to write into; created if it does not already exist
     * @return a sink ready to record books
     * @throws IOException if either report file cannot be created
     */
    static CorpusReportSink open(Path reportDir) throws IOException {
        Files.createDirectories(reportDir);
        final BufferedWriter jsonlWriter =
                Files.newBufferedWriter(reportDir.resolve(JSONL_FILE_NAME), StandardCharsets.UTF_8);
        final BufferedWriter tsvWriter =
                Files.newBufferedWriter(reportDir.resolve(SUMMARY_FILE_NAME), StandardCharsets.UTF_8);
        tsvWriter.write(TSV_HEADER);
        tsvWriter.newLine();
        tsvWriter.flush();
        return new CorpusReportSink(jsonlWriter, tsvWriter);
    }

    /**
     * Records one book's outcome to both report files and flushes immediately.
     *
     * @param outcome the book's outcome
     * @throws IOException if either report file cannot be written
     */
    void record(CorpusBookOutcome outcome) throws IOException {
        Objects.requireNonNull(outcome, "outcome");
        jsonl.write(CorpusJson.jsonOf(outcome));
        jsonl.newLine();
        jsonl.flush();
        tsv.write(CorpusTsv.rowOf(outcome));
        tsv.newLine();
        tsv.flush();
    }

    @Override
    public void close() throws IOException {
        jsonl.close();
        tsv.close();
    }
}
