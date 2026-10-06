package ua.bookloom.pipeline.revision;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import ua.bookloom.api.project.DeferralReason;

/**
 * What one backward-revision pass changed, for the run's log and the export report.
 *
 * @param termSubstitutions how many changed locked renderings were substituted, one per swept term deferral
 * @param genderReRenders how many segments the revision call re-rendered for a character's now-known gender
 * @param proposals how many segments the person edited got a proposal instead of a change
 * @param notes one line per change, naming the segment by its locator; never null, empty when nothing changed
 * @param openDeferrals the deferrals still open once the pass ended, counted by reason; never null, empty when none
 */
public record ConsistencyReport(
        int termSubstitutions,
        int genderReRenders,
        int proposals,
        List<String> notes,
        Map<DeferralReason, Integer> openDeferrals) {

    /** Copies the notes and the counts so the report can never change after construction. */
    public ConsistencyReport {
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
        openDeferrals = Map.copyOf(Objects.requireNonNull(openDeferrals, "openDeferrals"));
    }

    /** A report that left no deferral open. */
    public ConsistencyReport(
            final int termSubstitutions, final int genderReRenders, final int proposals, final List<String> notes) {
        this(termSubstitutions, genderReRenders, proposals, notes, Map.of());
    }

    /** The segments still waiting for a character's gender. */
    public int openGenderDeferrals() {
        return openDeferrals.getOrDefault(DeferralReason.GENDER_UNKNOWN, 0);
    }
}
