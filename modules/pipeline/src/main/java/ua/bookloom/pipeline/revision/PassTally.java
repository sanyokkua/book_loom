package ua.bookloom.pipeline.revision;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        return new ConsistencyReport(termSubstitutions, genderReRenders, proposals, notes, openDeferrals);
    }

    private void changed(final String segmentId, final String locator, final String what) {
        changedSegments.add(segmentId);
        notes.add(locator + ": " + what);
    }
}
