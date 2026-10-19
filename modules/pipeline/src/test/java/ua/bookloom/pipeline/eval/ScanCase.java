package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A deterministic scan case of the real-run corpus: a few synthetic lines repeated as a book repeats them, and what the
 * production scan must and must not propose. The names the model scan is given and the recurring terms the run keeps a
 * rendering for are decided here, before any model is asked.
 *
 * @param id the case id
 * @param scan which production scan reads the lines
 * @param lines the English lines, in order
 * @param repeat how many times the lines repeat, so a term clears the scan's counting thresholds
 * @param mustPropose the terms the scan must propose, as it writes them
 * @param mustNotPropose the terms the scan must not propose: generic words that are no recurring term
 * @param knownFailure whether production proposes wrongly today; reported outside every rate, and the test fails once
 *     the scan is right so the flag is dropped with the fix
 * @param fixedBy the task that fixes a known failure
 * @param rationale why the case exists
 */
record ScanCase(
        String id,
        Scan scan,
        List<String> lines,
        int repeat,
        List<String> mustPropose,
        List<String> mustNotPropose,
        boolean knownFailure,
        @Nullable String fixedBy,
        String rationale) {

    /** The production scans a case can ask. */
    enum Scan {
        /** The recurring common terms and titles whose renderings the run keeps ({@code KeyTermScan}). */
        KEY_TERMS,
        /** The capitalised names the glossary scan proposes ({@code FrequencyScan}). */
        NAMES
    }

    /** Rejects missing parts and copies the lists. */
    ScanCase {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(scan, "scan");
        lines = List.copyOf(lines);
        mustPropose = List.copyOf(mustPropose);
        mustNotPropose = List.copyOf(mustNotPropose);
    }
}
