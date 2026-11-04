package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.qa.CheckLevel;
import ua.bookloom.pipeline.qa.CheckLevels;
import ua.bookloom.pipeline.qa.QaResult;

/**
 * Whether a drafted, edited or repaired target may be accepted: the hard gates pass, no soft check failed outright, no
 * finding of level {@link CheckLevel#BLOCK} is left, and no verified blocker is left — an issue the reviewer named
 * with a quote the app found in the text and that no edit fixed. REPAIR findings left become the proofread mark and
 * NOTE findings the audit mark (design D13's outcome table). There is no score and no threshold: the confidence the
 * checks derive only orders segments for review ({@code specs/quality-gates/spec.md} "Accept a segment only by the
 * acceptance rule"). Pure and silent — the caller logs the decision it reaches with this rule.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AcceptanceRule {

    /** The mark an accepted segment carries to the Proofread stage, read from the findings left on it. */
    public enum Mark {
        /** No finding is left. */
        NONE,
        /** A REPAIR finding is left: the Proofread stage reads the segment again. */
        PROOFREAD,
        /** Only NOTE findings are left: the segment is listed for the audit. */
        AUDIT
    }

    /**
     * One segment's decision and the findings it was read from, split by level. The mark is derived from the stored
     * findings whenever they are read, so nothing new is stored.
     *
     * @param accepted whether the segment may be accepted
     * @param mark the mark of the findings left; {@link Mark#PROOFREAD} when any REPAIR finding is left, whatever
     *     {@code accepted} says
     * @param block the BLOCK findings, in order; never null
     * @param repair the REPAIR findings, in order; never null
     * @param note the NOTE findings, in order; never null
     */
    public record Decision(
            boolean accepted, Mark mark, List<QaFinding> block, List<QaFinding> repair, List<QaFinding> note) {

        /** Rejects missing parts and copies the lists. */
        public Decision {
            Objects.requireNonNull(mark, "mark");
            block = List.copyOf(Objects.requireNonNull(block, "block"));
            repair = List.copyOf(Objects.requireNonNull(repair, "repair"));
            note = List.copyOf(Objects.requireNonNull(note, "note"));
        }
    }

    /**
     * Decides one segment's evaluated target by the levels of the findings left on it.
     *
     * @param qa the target's hard-gate and soft-check outcome
     * @param verifiedBlockersLeft how many issues the reviewer evidenced that no edit fixed; zero when the reviewer is
     *     off or found nothing it could not fix
     * @return the decision: accepted when every hard gate passes, no soft check failed outright, no BLOCK finding and
     *     no verified blocker is left
     */
    public static Decision decide(final QaResult qa, final int verifiedBlockersLeft) {
        Objects.requireNonNull(qa, "qa");
        final List<QaFinding> block = atLevel(qa, CheckLevel.BLOCK);
        final List<QaFinding> repair = atLevel(qa, CheckLevel.REPAIR);
        final List<QaFinding> note = atLevel(qa, CheckLevel.NOTE);
        final boolean accepted =
                qa.hardGatesPass() && !qa.failedOutright() && verifiedBlockersLeft == 0 && block.isEmpty();
        return new Decision(accepted, markOf(repair, note), block, repair, note);
    }

    /**
     * Decides whether one segment's evaluated target may be accepted.
     *
     * @param qa the target's hard-gate and soft-check outcome
     * @param verifiedBlockersLeft how many issues the reviewer evidenced that no edit fixed
     * @return {@code true} when {@link #decide} accepts it, {@code false} otherwise
     */
    public static boolean accepts(final QaResult qa, final int verifiedBlockersLeft) {
        return decide(qa, verifiedBlockersLeft).accepted();
    }

    /**
     * Whether the reviewer should read a drafted pair: text a check already refuses goes to repair first, and a text
     * the reviewer edits would only have to pass the same checks again.
     *
     * @param qa the draft's evaluation
     * @return {@code true} when every hard gate passes, no soft check failed outright and no BLOCK finding is left
     */
    public static boolean readyForReview(final QaResult qa) {
        return accepts(qa, 0);
    }

    private static List<QaFinding> atLevel(final QaResult qa, final CheckLevel level) {
        return qa.findings().stream()
                .filter(finding -> CheckLevels.of(finding) == level)
                .toList();
    }

    private static Mark markOf(final List<QaFinding> repair, final List<QaFinding> note) {
        if (!repair.isEmpty()) {
            return Mark.PROOFREAD;
        }
        return note.isEmpty() ? Mark.NONE : Mark.AUDIT;
    }
}
