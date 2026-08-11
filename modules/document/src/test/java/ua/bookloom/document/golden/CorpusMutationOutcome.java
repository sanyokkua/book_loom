package ua.bookloom.document.golden;

/**
 * What the corpus verification's P3 probe records for one book — every segment's {@code targetInner} set to a
 * marker prefix plus its own {@code sourceInner}, written, and re-opened (design.md D6, task 11.1a).
 *
 * <p>The marker-strip fields ({@code markerStripClean}/{@code markerMismatchCount}/{@code markerMissingCount} on
 * {@link Completed}) are the load-bearing check: they need no comparator to be trustworthy, because stripping the
 * known marker prefix from the re-opened text either reproduces the original {@code sourceInner} exactly or it
 * does not.
 */
sealed interface CorpusMutationOutcome {

    /** P0 never opened the book, so there is nothing to mutate. */
    record NotAttempted() implements CorpusMutationOutcome {}

    /**
     * The probe's own fresh open of the source unexpectedly failed even though P0 succeeded.
     *
     * @param errorCode the {@code ErrorCode} name the port returned
     */
    record OpenFailed(String errorCode) implements CorpusMutationOutcome {}

    /**
     * The mutated write was refused.
     *
     * @param errorCode the {@code ErrorCode} name the port returned
     * @param validationRefusal whether this refusal is {@code ErrorCode.validation} from a source whose resolved
     *     charset cannot represent the marker — a correct outcome, not a failure (design.md D6)
     */
    record WriteFailed(String errorCode, boolean validationRefusal) implements CorpusMutationOutcome {}

    /**
     * Re-opening the mutated output failed.
     *
     * @param errorCode the {@code ErrorCode} name the port returned
     */
    record ReopenFailed(String errorCode) implements CorpusMutationOutcome {}

    /**
     * The mutated write and its re-open both succeeded.
     *
     * @param unitCount the re-opened output's unit count
     * @param segmentCount the re-opened output's total segment count
     * @param countsMatchOpen whether {@code unitCount}/{@code segmentCount} equal P0's
     * @param tuplesMatchOpen whether the ordered {@code (unit order, segment id, kind, anchor)} tuples equal P0's
     * @param markerStripClean whether every re-opened segment's {@code sourceInner}, with the marker prefix
     *     stripped, equals P0's {@code sourceInner} for the same segment id
     * @param markerMismatchCount the count of segments found under the same id whose stripped text disagreed
     * @param markerMissingCount the count of P0 segment ids absent from the re-opened output
     */
    record Completed(
            int unitCount,
            int segmentCount,
            boolean countsMatchOpen,
            boolean tuplesMatchOpen,
            boolean markerStripClean,
            int markerMismatchCount,
            int markerMissingCount)
            implements CorpusMutationOutcome {}
}
