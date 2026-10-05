package ua.bookloom.pipeline.heal;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Whether a drafted, edited or repaired target may be accepted: the hard gates pass, no deterministic check blocks, and
 * no verified blocker is left — an issue the reviewer named with a quote the app found in the text and that no edit
 * fixed. There is no score and no threshold: the confidence the checks derive only orders segments for review
 * ({@code specs/quality-gates/spec.md} "Accept a segment only by the acceptance rule"). Pure and silent — the caller
 * logs the decision it reaches with this rule.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AcceptanceRule {

    /**
     * Decides whether one segment's evaluated target may be accepted.
     *
     * @param qa the target's hard-gate and soft-check outcome
     * @param verifiedBlockersLeft how many issues the reviewer evidenced that no edit fixed; zero when the reviewer is
     *     off or found nothing it could not fix
     * @return {@code true} when every hard gate passes, no soft check failed outright and no verified blocker is left
     */
    public static boolean accepts(final QaResult qa, final int verifiedBlockersLeft) {
        Objects.requireNonNull(qa, "qa");
        return qa.hardGatesPass() && !qa.failedOutright() && verifiedBlockersLeft == 0;
    }

    /**
     * Whether the reviewer should read a drafted pair: text a check already refuses goes to repair first, and a text
     * the reviewer edits would only have to pass the same checks again.
     *
     * @param qa the draft's evaluation
     * @return {@code true} when every hard gate passes and no soft check failed outright
     */
    public static boolean readyForReview(final QaResult qa) {
        return accepts(qa, 0);
    }
}
