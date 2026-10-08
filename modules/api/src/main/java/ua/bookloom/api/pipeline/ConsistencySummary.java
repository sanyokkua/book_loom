package ua.bookloom.api.pipeline;

import java.util.Map;
import java.util.Objects;
import ua.bookloom.api.project.DeferralReason;

/**
 * What the final consistency pass did during one export, so the screen can say something after a pass that changes
 * nothing visible.
 *
 * @param status whether the pass ran, and whether it had a model for its gender step
 * @param termSubstitutions segments whose locked term was swept to its new rendering
 * @param genderReRenders segments re-rendered because a character's gender became known
 * @param openDeferrals the deferrals still open after the pass, counted by reason, so a pass that changed nothing can
 *     say what it is waiting for
 * @param neighbourFixes paragraphs the check against the paragraphs around them corrected
 * @param checks what the retry of doubted segments and the neighbour check came to besides the fixes
 */
public record ConsistencySummary(
        Status status,
        int termSubstitutions,
        int genderReRenders,
        Map<DeferralReason, Integer> openDeferrals,
        int neighbourFixes,
        ConsistencyChecks checks) {

    /** The summary of an export whose pass was switched off: nothing ran, nothing is to be said. */
    public static final ConsistencySummary NOT_RUN = new ConsistencySummary(Status.NOT_RUN, 0, 0);

    /** How far the pass got. */
    public enum Status {
        /** The switch was off. */
        NOT_RUN,
        /** The pass ran with the chosen model, so both of its steps ran. */
        RAN,
        /** The pass ran without a model, so only the name sweep ran and gender deferrals stayed open. */
        RAN_WITHOUT_MODEL
    }

    /** A summary with no count of the model steps besides their fixes. */
    public ConsistencySummary(
            final Status status,
            final int termSubstitutions,
            final int genderReRenders,
            final Map<DeferralReason, Integer> openDeferrals,
            final int neighbourFixes) {
        this(status, termSubstitutions, genderReRenders, openDeferrals, neighbourFixes, ConsistencyChecks.NONE);
    }

    /** A summary with no check against the neighbouring paragraphs. */
    public ConsistencySummary(
            final Status status,
            final int termSubstitutions,
            final int genderReRenders,
            final Map<DeferralReason, Integer> openDeferrals) {
        this(status, termSubstitutions, genderReRenders, openDeferrals, 0);
    }

    /** A summary that left no deferral open. */
    public ConsistencySummary(final Status status, final int termSubstitutions, final int genderReRenders) {
        this(status, termSubstitutions, genderReRenders, Map.of());
    }

    /** Rejects a missing status or a negative count and copies the open counts. */
    public ConsistencySummary {
        Objects.requireNonNull(status, "status");
        openDeferrals = Map.copyOf(Objects.requireNonNull(openDeferrals, "openDeferrals"));
        Objects.requireNonNull(checks, "checks");
        if (termSubstitutions < 0 || genderReRenders < 0 || neighbourFixes < 0) {
            throw new IllegalArgumentException(
                    "no count may be negative: " + termSubstitutions + ", " + genderReRenders);
        }
    }

    /** Segments the pass changed. */
    public int adjusted() {
        return termSubstitutions + genderReRenders + neighbourFixes + checks.retriedImproved();
    }

    /** Segments still waiting for a character's gender, which no pass can render until it is set. */
    public int openGenderDeferrals() {
        return openDeferrals.getOrDefault(DeferralReason.GENDER_UNKNOWN, 0);
    }
}
