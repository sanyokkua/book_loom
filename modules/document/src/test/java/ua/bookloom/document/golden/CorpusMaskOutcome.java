package ua.bookloom.document.golden;

import org.jspecify.annotations.Nullable;

/**
 * What the corpus verification's mask probe records for one book — every segment given its own masked form back
 * as its own target, restored through the real {@code DocumentService#unmask} port, and compared against its
 * source content under the same canonical rule the identity probe uses (design.md D6, task 10.1). DD-49 warns
 * that "naive masking would explode the placeholder multiset", so this probe measures the actual per-book
 * placeholder cost across real book text rather than assuming it stays small.
 */
sealed interface CorpusMaskOutcome {

    /** P0 never opened the book, so there are no segments to restore. */
    record NotAttempted() implements CorpusMaskOutcome {}

    /**
     * Every segment of the opened document was restored from its own masked form and compared to its source; the
     * statistics are recorded whether or not every comparison passed (task 10.1's own instruction).
     *
     * @param segmentCount the number of segments probed
     * @param totalPlaceholders the sum, across every segment, of that segment's placeholder count
     * @param maxPlaceholdersInOneSegment the largest placeholder count carried by any single segment, or
     *     {@code 0} when {@code segmentCount} is {@code 0}
     * @param segmentsWithPlaceholders the count of segments whose placeholder map is non-empty
     * @param ok whether every segment's restored content matched its source under this book's format comparator
     * @param mismatchCount the count of segments whose restored content did not match its source (including a
     *     segment whose own {@code unmask} call itself returned an error)
     * @param skippedCodeOnlyBlocks the count of XHTML blocks skipped as code-only (the exclusion
     *     {@code BlockSegmentWalker} applies per D11), or {@code null} when a run does not measure it — see
     *     {@link CorpusMaskProbe}'s class Javadoc for why this harness does not re-derive that count
     * @param failureMessage the first mismatch's failure message, or {@code null} when {@code ok} is {@code true}
     */
    record Completed(
            int segmentCount,
            int totalPlaceholders,
            int maxPlaceholdersInOneSegment,
            int segmentsWithPlaceholders,
            boolean ok,
            int mismatchCount,
            @Nullable Integer skippedCodeOnlyBlocks,
            @Nullable String failureMessage)
            implements CorpusMaskOutcome {}
}
