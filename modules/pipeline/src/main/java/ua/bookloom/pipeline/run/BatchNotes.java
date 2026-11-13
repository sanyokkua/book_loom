package ua.bookloom.pipeline.run;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.pipeline.SegmentOutcomeNote;
import ua.bookloom.pipeline.batch.ItemOutcome;
import ua.bookloom.pipeline.batch.ItemProblem;
import ua.bookloom.pipeline.batch.ItemStatus;

/** What a batch call's row notes of each item: taken, or left to its own draft and why. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class BatchNotes {

    private static final String GATE_REFUSED = "GATE";
    private static final String ECHO = "ECHO";

    static SegmentOutcomeNote of(
            final String segmentId, final ItemOutcome outcome, final boolean isAdopted, final boolean copied) {
        if (isAdopted) {
            return new SegmentOutcomeNote(segmentId, SegmentOutcomeNote.Kind.ADOPTED, "");
        }
        final String reason = copied ? ECHO : fallbackReason(outcome);
        return new SegmentOutcomeNote(segmentId, SegmentOutcomeNote.Kind.FELL_BACK, reason);
    }

    // The id's own status when the reply got the id wrong, else the checks that failed, else the gate's refusal.
    private static String fallbackReason(final ItemOutcome outcome) {
        if (outcome.status() != ItemStatus.OK) {
            return outcome.status().name();
        }
        if (outcome.problems().isEmpty()) {
            return GATE_REFUSED;
        }
        return String.join(
                ",", outcome.problems().stream().map(ItemProblem::name).toList());
    }
}
