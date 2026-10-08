package ua.bookloom.pipeline.revision;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import ua.bookloom.api.pipeline.ConsistencyChecks;
import ua.bookloom.api.project.DeferralReason;

/**
 * The counts and notes of one pass as it goes. Used from the pass's own thread only, which is why it is plain
 * mutable state.
 */
final class PassTally {

    private final List<String> notes = new ArrayList<>();
    private final Set<String> changedSegments = new LinkedHashSet<>();
    private int termSubstitutions;
    private int genderReRenders;
    private int proposals;
    private int neighbourFixes;
    private int retriedImproved;
    private int retriedKept;
    private int neighbourUnchanged;
    private int skipped;
    private final Map<String, Integer> refused = new TreeMap<>();

    /** Records a machine target the sweep replaced, with the number of term deferrals it swept into it. */
    void swept(final String segmentId, final String locator, final int terms) {
        termSubstitutions += terms;
        changed(segmentId, locator, "locked term substituted");
    }

    /** Records a machine target the revision call re-rendered. */
    void reRendered(final String segmentId, final String locator) {
        genderReRenders++;
        changed(segmentId, locator, "revised for gender");
    }

    /** Records a machine target the check against its neighbours corrected. */
    void neighbourFixed(final String segmentId, final String locator) {
        neighbourFixes++;
        changed(segmentId, locator, "fixed against its neighbours");
    }

    /** Records a doubted segment whose fresh draft was better and replaced its machine target. */
    void retryImproved(final String segmentId, final String locator) {
        retriedImproved++;
        changed(segmentId, locator, "drafted again and improved");
    }

    /** Records a doubted segment whose fresh draft was no better, so the old text stayed. */
    void retryKept() {
        retriedKept++;
    }

    /** Records a paragraph the check against its neighbours found nothing to change in. */
    void neighbourUnchanged() {
        neighbourUnchanged++;
    }

    /** Records an answer the pass did not keep because it broke {@code rule}. */
    void refused(final String rule) {
        refused.merge(rule, 1, Integer::sum);
    }

    /** Records a segment whose call failed, so it was left as it was. */
    void skipped() {
        skipped++;
    }

    /** Records a proposal stored for a segment the person edited. */
    void proposed(final String segmentId, final String locator) {
        proposals++;
        changed(segmentId, locator, "proposal recorded");
    }

    /** The number of distinct segments whose target or proposal changed. */
    int segmentsChanged() {
        return changedSegments.size();
    }

    ConsistencyReport report(final Map<DeferralReason, Integer> openDeferrals) {
        return new ConsistencyReport(
                termSubstitutions,
                genderReRenders,
                proposals,
                notes,
                openDeferrals,
                neighbourFixes,
                new ConsistencyChecks(retriedImproved, retriedKept, neighbourUnchanged, refused, skipped));
    }

    private void changed(final String segmentId, final String locator, final String what) {
        changedSegments.add(segmentId);
        notes.add(locator + ": " + what);
    }
}
