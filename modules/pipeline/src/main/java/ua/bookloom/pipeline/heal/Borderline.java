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
     * The floating-point tolerance this window's lower bound is read with, shared with
     * {@code heal.AcceptanceRule}'s τ comparison (design D8, "Tuning constants": confidence comparison), so a sum a
     * hair below the true bound still meets it.
     */
    public static final double ACCEPTANCE_TOLERANCE = 1e-9;

    /**
     * Decides whether an improved target is borderline.
     *
     * @param hardGatesPass whether every hard gate passed
     * @param failedOutright whether a soft check failed outright (owner decision D-3); a failed echo below the
     *     floor does not set this
     * @param confidence the segment's blended confidence, in {@code [0,1]}
     * @param tau the review mode's acceptance threshold
     * @return {@code true} when hard gates pass, no check failed outright, and confidence lies in
     *     {@code [tau - POLISH_WINDOW, tau)}
     */
    public static boolean isBorderline(
            final boolean hardGatesPass, final boolean failedOutright, final double confidence, final double tau) {
        return hardGatesPass
                && !failedOutright
                && confidence >= tau - POLISH_WINDOW - ACCEPTANCE_TOLERANCE
                && confidence < tau;
    }
}
