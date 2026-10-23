package ua.bookloom.pipeline.revision;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.pipeline.prompt.ModelCalls;

/**
 * The person's "write the file now": a stage stops before its next item, and a call it interrupted is no failure of the
 * pass but the stop itself, so the book is still written with every fix already stored.
 */
@Slf4j
// Checkstyle parses source before Lombok runs, so it cannot see the private constructor (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EarlyStop {

    /**
     * Whether a stage ended by a failed call stopped because the person asked to write the file now.
     *
     * @param ended the stage's failed result; non-null
     * @param calls the seam the stage sent its calls through
     * @param stage the stage, for the log line
     * @return {@code true} if the failure is the early stop's interrupted call, {@code false} for a real failure
     */
    static boolean isStop(final Result<?> ended, final ModelCalls calls, final String stage) {
        final AppError error = ended.error();
        final boolean stopped = error != null && error.code() == ErrorCode.cancelled && calls.isStoppingEarly();
        log.debug("{} ended code={} stoppingEarly={}", stage, error == null ? null : error.code(), stopped);
        return stopped;
    }

    /**
     * Whether a stage should leave its next item as stored.
     *
     * @param calls the seam the stage sends its calls through
     * @param stage the stage, for the log line
     * @param remaining the items it has not reached
     * @return {@code true} if the person asked to write the file now, {@code false} otherwise
     */
    static boolean isRequested(final ModelCalls calls, final String stage, final int remaining) {
        final boolean stopping = calls.isStoppingEarly();
        if (stopping) {
            log.info("{} stopped early: the file is written now remaining={}", stage, remaining);
        }
        return stopping;
    }
}
