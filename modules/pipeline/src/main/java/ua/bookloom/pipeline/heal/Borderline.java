package ua.bookloom.pipeline.heal;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Whether an improved target sits close enough below τ that a monolingual {@link Polish} pass, not another repair
 * round, is what it needs — polishing a target that still fails a check would only hide that failure, so the
 * window applies only once hard gates pass and no soft check failed outright (design D8, "Tuning constants": polish
 * window ε 0.05).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Borderline {

    /** The width of the polish window below τ ("Tuning constants": polish window ε 0.05). */
    public static final double POLISH_WINDOW = 0.05;

    /**
     * Decides whether an improved target is borderline.
     *
     * @param hardGatesPass whether every hard gate passed
     * @param failedOutright whether a soft check failed outright (owner decision D-3); a failed echo below the
     *     floor does not set this
     * @param confidence the segment's blended confidence, in {@code [0,1]}
     * @param tau the review mode's acceptance threshold
     * @return {@code true} when hard gates pass, no check failed outright, and confidence lies in
     *     {@code [tau - POLISH_WINDOW, tau - }{@link AcceptanceRule#ACCEPTANCE_TOLERANCE}{@code )} — the upper bound
     *     reads with the same tolerance {@link AcceptanceRule#accepts} does, so a target the rule would already
     *     accept is never sent to polish
     */
    public static boolean isBorderline(
            final boolean hardGatesPass, final boolean failedOutright, final double confidence, final double tau) {
        return hardGatesPass
                && !failedOutright
                && confidence >= tau - POLISH_WINDOW - AcceptanceRule.ACCEPTANCE_TOLERANCE
                && confidence < tau - AcceptanceRule.ACCEPTANCE_TOLERANCE;
    }
}
