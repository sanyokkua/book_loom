package ua.bookloom.document.golden;

/**
 * What the corpus verification's P2 probe records for one book — P1's zero-edit output written a second time,
 * with zero edits, and compared to itself (design.md D6, task 11.1).
 */
sealed interface CorpusFixedPointOutcome {

    /** P1 did not produce an output to feed this probe. */
    record NotAttempted() implements CorpusFixedPointOutcome {}

    /**
     * Re-opening P1's output failed.
     *
     * @param errorCode the {@code ErrorCode} name the port returned
     */
    record ReopenFailed(String errorCode) implements CorpusFixedPointOutcome {}

    /**
     * The second write was refused.
     *
     * @param errorCode the {@code ErrorCode} name the port returned
     */
    record WriteFailed(String errorCode) implements CorpusFixedPointOutcome {}

    /**
     * The second write succeeded; judged on {@code canonicalEqual} (trap 4) — {@code rawBytesEqual} is recorded
     * only for information, since zip entry timestamps make it noisy even for a structurally identical output.
     *
     * @param rawBytesEqual whether the second output's raw bytes equal the first output's raw bytes
     * @param canonicalEqual whether the second output is canonical-equal to the first
     */
    record Written(boolean rawBytesEqual, boolean canonicalEqual) implements CorpusFixedPointOutcome {}
}
