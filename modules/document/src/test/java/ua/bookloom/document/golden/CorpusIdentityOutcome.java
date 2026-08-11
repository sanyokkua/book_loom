package ua.bookloom.document.golden;

import org.jspecify.annotations.Nullable;

/**
 * What the corpus verification's P1 probe records for one book — a zero-edit write compared canonically against
 * the source (design.md D6, task 11.1).
 */
sealed interface CorpusIdentityOutcome {

    /** P0 never opened the book, so there is nothing to write back. */
    record NotAttempted() implements CorpusIdentityOutcome {}

    /**
     * The probe's own fresh open of the source (trap 5: a write probe never reuses another probe's registry)
     * unexpectedly failed even though P0 succeeded.
     *
     * @param errorCode the {@code ErrorCode} name the port returned
     */
    record OpenFailed(String errorCode) implements CorpusIdentityOutcome {}

    /**
     * The zero-edit write was refused.
     *
     * @param errorCode the {@code ErrorCode} name the port returned
     */
    record WriteFailed(String errorCode) implements CorpusIdentityOutcome {}

    /**
     * The write succeeded; both comparisons trap 4 requires as separate fields.
     *
     * @param rawBytesEqual whether the output's raw bytes equal the source's raw bytes
     * @param canonicalEqual whether the output is canonical-equal to the source under this book's format
     *     comparator
     * @param canonicalFailureMessage the comparator's failure message, or {@code null} when {@code canonicalEqual}
     *     is {@code true}
     */
    record Written(
            boolean rawBytesEqual,
            boolean canonicalEqual,
            @Nullable String canonicalFailureMessage) implements CorpusIdentityOutcome {}
}
