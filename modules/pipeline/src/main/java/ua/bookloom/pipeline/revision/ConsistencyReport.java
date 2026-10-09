package ua.bookloom.pipeline.revision;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import ua.bookloom.api.pipeline.ConsistencyChecks;
import ua.bookloom.api.pipeline.GenderWait;
import ua.bookloom.api.project.DeferralReason;

/**
 * What one backward-revision pass changed, for the run's log and the export report.
 *
 * @param termSubstitutions how many changed locked renderings were substituted, one per swept term deferral
 * @param genderReRenders how many segments the revision call re-rendered for a character's now-known gender
 * @param proposals how many segments the person edited got a proposal instead of a change
 * @param notes one line per change, naming the segment by its locator; never null, empty when nothing changed
 * @param openDeferrals the deferrals still open once the pass ended, counted by reason; never null, empty when none
 * @param neighbourFixes how many segments the check against the neighbouring paragraphs corrected
 * @param checks what the retry of doubted segments and the check against the neighbours came to besides the fixes
 * @param awaitingGender the characters whose unknown gender still holds segments back, most waiting first; never
 *     null, empty when none does
 */
public record ConsistencyReport(
        int termSubstitutions,
        int genderReRenders,
        int proposals,
        List<String> notes,
        Map<DeferralReason, Integer> openDeferrals,
        int neighbourFixes,
        ConsistencyChecks checks,
        List<GenderWait> awaitingGender) {

    /** Copies the notes and the counts so the report can never change after construction. */
    public ConsistencyReport {
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
        openDeferrals = Map.copyOf(Objects.requireNonNull(openDeferrals, "openDeferrals"));
        Objects.requireNonNull(checks, "checks");
        awaitingGender = List.copyOf(Objects.requireNonNull(awaitingGender, "awaitingGender"));
    }

    /** A report that names no waiting character. */
    public ConsistencyReport(
            final int termSubstitutions,
            final int genderReRenders,
            final int proposals,
            final List<String> notes,
            final Map<DeferralReason, Integer> openDeferrals,
            final int neighbourFixes,
            final ConsistencyChecks checks) {
        this(termSubstitutions, genderReRenders, proposals, notes, openDeferrals, neighbourFixes, checks, List.of());
    }

    /** A report with no model step besides the gender re-render and the neighbour fixes it counts. */
    public ConsistencyReport(
            final int termSubstitutions,
            final int genderReRenders,
            final int proposals,
            final List<String> notes,
            final Map<DeferralReason, Integer> openDeferrals,
            final int neighbourFixes) {
        this(
                termSubstitutions,
                genderReRenders,
                proposals,
                notes,
                openDeferrals,
                neighbourFixes,
                ConsistencyChecks.NONE);
    }

    /** A report with no neighbour check. */
    public ConsistencyReport(
            final int termSubstitutions,
            final int genderReRenders,
            final int proposals,
            final List<String> notes,
            final Map<DeferralReason, Integer> openDeferrals) {
        this(termSubstitutions, genderReRenders, proposals, notes, openDeferrals, 0);
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
