package ua.bookloom.document.golden;

/**
 * What the corpus verification's P4 probe records for one book — P1's zero-edit output re-opened and compared
 * to P0's parse of the source (design.md D6, task 11.1).
 */
sealed interface CorpusIdempotenceOutcome {

    /** P1 did not produce an output to feed this probe. */
    record NotAttempted() implements CorpusIdempotenceOutcome {}

    /**
     * Re-opening P1's output failed.
     *
     * @param errorCode the {@code ErrorCode} name the port returned
     */
    record ReopenFailed(String errorCode) implements CorpusIdempotenceOutcome {}

    /**
     * Re-opening P1's output succeeded.
     *
     * @param tuplesMatchOpen whether the ordered {@code (unit order, segment id, kind, anchor)} tuples equal P0's
     * @param sourceTextMatchOpen whether every segment's {@code sourceInner}, keyed by id, equals P0's
     */
    record Completed(boolean tuplesMatchOpen, boolean sourceTextMatchOpen) implements CorpusIdempotenceOutcome {}
}
