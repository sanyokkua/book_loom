package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * What a glossary CSV import did, so one bad line is named instead of throwing away the good ones
 * ({@code specs/glossary/spec.md} "Import and export the glossary as CSV").
 *
 * @param imported how many rows were added or updated
 * @param malformedLines the 1-based lines, the header being line 1, whose row lacked a field or held an unknown value;
 *     a quoted field spanning lines reports the line its row starts on
 * @param refusedLines the 1-based lines whose row was locked with no target
 * @param duplicates how many rows named a term an earlier row of the same file already named; the last row of each
 *     term is the one imported
 */
public record GlossaryImportReport(
        int imported, List<Integer> malformedLines, List<Integer> refusedLines, int duplicates) {

    /**
     * A report of a file with no repeated term.
     *
     * @param imported how many rows were added or updated
     * @param malformedLines the lines whose row could not be read
     * @param refusedLines the lines whose row was locked with no target
     */
    public GlossaryImportReport(
            final int imported, final List<Integer> malformedLines, final List<Integer> refusedLines) {
        this(imported, malformedLines, refusedLines, 0);
    }

    /** Validates the invariants a caller is entitled to assume and defensively copies both line lists. */
    public GlossaryImportReport {
        Objects.requireNonNull(malformedLines, "malformedLines");
        Objects.requireNonNull(refusedLines, "refusedLines");
        malformedLines = List.copyOf(malformedLines);
        refusedLines = List.copyOf(refusedLines);
    }
}
