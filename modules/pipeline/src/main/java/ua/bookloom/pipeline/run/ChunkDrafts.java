package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.heal.DraftOutcome;

/**
 * One chunk's drafts, kept in memory until each is decided: the quality loop reads them all, and a later draft of the
 * same chunk reads an undecided one as its preceding target. A stop drops what is still undecided here.
 *
 * <p>Used from the job thread only, which is why the state is plain collections.
 */
final class ChunkDrafts {

    private final List<DraftOutcome> drafts = new ArrayList<>();
    private final Map<String, DraftOutcome> undecided = new LinkedHashMap<>();

    void drafted(final DraftOutcome outcome) {
        drafts.add(outcome);
        undecided.put(outcome.segment().id(), outcome);
    }

    void decided(final String segmentId) {
        undecided.remove(segmentId);
    }

    List<DraftOutcome> all() {
        return List.copyOf(drafts);
    }

    boolean isUndecided(final String segmentId) {
        return undecided.containsKey(segmentId);
    }

    /** The masked target an undecided draft offers, or null when its markup did not restore or it was flagged. */
    @Nullable
    String maskedTarget(final String segmentId) {
        return undecided.get(segmentId) instanceof DraftOutcome.Drafted drafted ? drafted.maskedForm() : null;
    }

    List<String> undecidedIds() {
        return List.copyOf(undecided.keySet());
    }
}
