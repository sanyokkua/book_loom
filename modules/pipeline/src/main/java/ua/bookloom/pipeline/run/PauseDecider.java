package ua.bookloom.pipeline.run;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.pipeline.PausePoint;
import ua.bookloom.api.pipeline.PauseReason;

/**
 * Decides what an error a model call answered does to the run, and where a decision boundary pauses. Both are pure
 * functions, kept apart from the job so that a call which names no single segment — a chunk's judge — is routed by the
 * same table, and so that the lock-held control only asks and records.
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

    /**
     * Chooses the one pause a decision boundary makes, if any: a requested pause, then a flagged segment where
     * on-flagged is in force, then the widest point that applies — between-stages, after-section, after-segment. The
     * last segment of a book ends a segment, a section and the stage at once, and the person should see one pause.
     *
     * @param points the non-null pause points in force at this boundary
     * @param requested whether a pause was requested
     * @param flagged whether the segment just decided was flagged
     * @param endsSection whether it was the last of its section
     * @param endsStage whether it was the last segment of the translation stage
     * @return the reason to pause with, or empty to go on
     */
    public static Optional<PauseReason> boundary(
            final Set<PausePoint> points,
            final boolean requested,
            final boolean flagged,
            final boolean endsSection,
            final boolean endsStage) {
        Objects.requireNonNull(points, "points");
        final Optional<PauseReason> reason =
                requested ? Optional.of(PauseReason.REQUESTED) : pointReached(points, flagged, endsSection, endsStage);
        log.debug(
                "Checked decision boundary requested={} flagged={} sectionEnd={} stageEnd={} points={} reason={}",
                requested,
                flagged,
                endsSection,
                endsStage,
                points,
                reason.orElse(null));
        return reason;
    }

    /**
     * Tells whether a pause for this reason names the segment just decided, so the review panel can open on it.
     *
     * @param reason the non-null reason of the pause
     * @return {@code true} for on-flagged and after-segment, {@code false} for every pause not about one segment
     */
    public static boolean namesSegment(final PauseReason reason) {
        return switch (Objects.requireNonNull(reason, "reason")) {
            case ON_FLAGGED, AFTER_SEGMENT -> true;
            case REQUESTED, AFTER_SECTION, BETWEEN_STAGES, ON_ERROR -> false;
        };
    }

    private static Optional<PauseReason> pointReached(
            final Set<PausePoint> points, final boolean flagged, final boolean endsSection, final boolean endsStage) {
        if (flagged && points.contains(PausePoint.ON_FLAGGED)) {
            return Optional.of(PauseReason.ON_FLAGGED);
        }
        if (endsStage && points.contains(PausePoint.BETWEEN_STAGES)) {
            return Optional.of(PauseReason.BETWEEN_STAGES);
        }
        if (endsSection && points.contains(PausePoint.AFTER_SECTION)) {
            return Optional.of(PauseReason.AFTER_SECTION);
        }
        return points.contains(PausePoint.AFTER_SEGMENT) ? Optional.of(PauseReason.AFTER_SEGMENT) : Optional.empty();
    }
}
