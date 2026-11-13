package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * What the Proofread stage left behind when the book was written, read from the stored verdicts, findings and
 * deferrals with no model call, so the report and the Export screen can say how much was re-read and what still waits.
 *
 * @param reRead the segments a Proofread window read, whatever it decided; never negative
 * @param waiting the distinct segments that still wait on the person: flagged, holding an open deferral, or holding a
 *     proofread mark no window read or whose rewrite was refused; never negative
 * @param awaitingGender the characters whose unknown or only suggested gender still holds segments back, most waiting
 *     first; never null, empty when none does
 * @param suggestedGenderSegments the distinct segments that wait on a suggested gender the person only has to confirm
 * @param unknownGenderSegments the distinct segments that wait on a gender nobody has given (or on the narrator's)
 */
public record ProofreadSummary(
        int reRead,
        int waiting,
        List<GenderWait> awaitingGender,
        int suggestedGenderSegments,
        int unknownGenderSegments) {

    /** The summary of a book nothing re-read and nothing waits in. */
    public static final ProofreadSummary NONE = new ProofreadSummary(0, 0);

    /** Rejects a negative count and copies the list. */
    public ProofreadSummary {
        awaitingGender = List.copyOf(Objects.requireNonNull(awaitingGender, "awaitingGender"));
        if (reRead < 0 || waiting < 0 || suggestedGenderSegments < 0 || unknownGenderSegments < 0) {
            throw new IllegalArgumentException("no count may be negative: " + reRead + ", " + waiting + ", "
                    + suggestedGenderSegments + ", " + unknownGenderSegments);
        }
    }

    /**
     * A summary in which no segment waits on a gender.
     *
     * @param reRead the segments a window read; never negative
     * @param waiting the segments that still wait on the person; never negative
     */
    public ProofreadSummary(final int reRead, final int waiting) {
        this(reRead, waiting, List.of(), 0, 0);
    }
}
