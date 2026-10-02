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
 * <p>Each switch lists all fifteen codes without a {@code default}, so a sixteenth code cannot compile until
 * someone decides where it goes.
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
         * The call could not be answered now — the provider failed, or the step threw: the run pauses with the error,
         * or fails where pausing on an error is not enabled, and the interrupted call is made again on resume.
         * {@link Recovery} says whether the pause resumes by itself.
         */
        PAUSE_OR_FAIL,

        /** A code a run never sees, so it never pauses: the run ends Failed with {@code internal}. */
        FAIL
    }

    /**
     * How a run paused on an error gets going again, and how many such pauses one step may cause before it is flagged.
     * Kept apart from {@link Route} because it matters only for {@link Route#PAUSE_OR_FAIL}.
     */
    public enum Recovery {

        /**
         * The provider is down or refusing for now (unreachable, 5xx, 429). The run waits and probes by itself, and an
         * outage is not the step's fault: it spends a budget of its own, large enough that only calls made after a probe
         * found the provider reachable again use it up.
         */
        OUTAGE(true, 10),

        /** A call ran out of time. The run waits and tries again by itself, and the step's own budget is spent. */
        STALL(true, 2),

        /** An unexpected failure inside the run. The run waits and tries again by itself a few times. */
        FAULT(true, 3),

        /**
         * Something only the person can fix (a credential, a missing or unloaded model, a rejected request): the run
         * waits for them, because retrying by itself would flag the rest of the book one segment at a time.
         */
        PERSON(false, 2);

        private final boolean automatic;
        private final int pausesBeforeFlagging;

        Recovery(final boolean automatic, final int pausesBeforeFlagging) {
            this.automatic = automatic;
            this.pausesBeforeFlagging = pausesBeforeFlagging;
        }

        /**
         * Whether a pause of this kind resumes by itself once the provider answers a probe.
         *
         * @return {@code true} if the run recovers without the person, {@code false} otherwise
         */
        public boolean isAutomatic() {
            return automatic;
        }

        /**
         * How many pauses of this kind one step may cause before its next failure flags it.
         *
         * @return a positive count
         */
        public int pausesBeforeFlagging() {
            return pausesBeforeFlagging;
        }
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
                            validation,
                            internal -> Route.PAUSE_OR_FAIL;
                    case busy, discoveryFailed -> Route.FAIL;
                };
        log.debug("Routed model-call error code={} route={}", code, route);
        return route;
    }

    /**
     * Classifies an error the run pauses on by how it recovers.
     *
     * @param code the non-null code of the error
     * @return how a pause on it gets going again; {@link Recovery#PERSON} for a code that never pauses at all
     */
    public static Recovery recovery(final ErrorCode code) {
        Objects.requireNonNull(code, "code");
        final Recovery recovery =
                switch (code) {
                    case unreachable, upstream, rateLimited -> Recovery.OUTAGE;
                    case timeout -> Recovery.STALL;
                    case internal -> Recovery.FAULT;
                    case auth,
                            modelNotFound,
                            modelUnavailable,
                            missingCredential,
                            validation,
                            emptyCompletion,
                            contextWindow,
                            cancelled,
                            busy,
                            discoveryFailed -> Recovery.PERSON;
                };
        log.debug("Classified paused-on error code={} recovery={}", code, recovery);
        return recovery;
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
