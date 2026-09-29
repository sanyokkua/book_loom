package ua.bookloom.pipeline.run;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.ErrorCode;

/**
 * Decides what an error a model call answered does to the run. It is a pure function of the code, kept apart from
 * the job so that a call which names no single segment — a chunk's judge — is routed by the same table.
 *
 * <p>The switch lists all fifteen codes without a {@code default}, so a sixteenth code cannot compile until someone
 * decides where it goes.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
public final class PauseDecider {

    /** What a model-call error does to the run. */
    public enum Route {

        /** The segment is flagged at once, without a repair, and the run goes on. */
        FLAG_AT_ONCE,

        /** The person stopped the run; it ends Cancelled and nothing is failed. */
        CANCELLED,

        /**
         * The provider could not answer now: the run pauses with the error where the person can retry, or fails
         * where pausing on an error is not enabled, and the interrupted call is made again on resume.
         */
        PAUSE_OR_FAIL,

        /** The error is not the provider's to fix, so it never pauses: the run ends Failed with {@code internal}. */
        FAIL
    }

    /**
     * Routes the error a model call answered.
     *
     * @param code the non-null code of the error
     * @return where the error sends the segment and the run
     */
    public static Route route(final ErrorCode code) {
        Objects.requireNonNull(code, "code");
        final Route route =
                switch (code) {
                    case emptyCompletion, contextWindow -> Route.FLAG_AT_ONCE;
                    case cancelled -> Route.CANCELLED;
                    case unreachable,
                            timeout,
                            auth,
                            rateLimited,
                            upstream,
                            modelNotFound,
                            modelUnavailable,
                            missingCredential,
                            validation -> Route.PAUSE_OR_FAIL;
                    case internal, busy, discoveryFailed -> Route.FAIL;
                };
        log.debug("Routed model-call error code={} route={}", code, route);
        return route;
    }
}
