package ua.bookloom.pipeline.run;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.pipeline.heal.DraftOutcome;

/**
 * One chunk's drafts and memory reuses, kept in memory until each is decided: the quality loop reads them all, a
 * later window of the same chunk shows an undecided one by its source alone, and each decided record stores the
 * snapshot of what its draft saw. A stop drops what is still undecided here.
 *
 * <p>Used from the job thread only, which is why the state is plain collections.
 */
final class ChunkDrafts {

    private final List<DraftOutcome> drafts = new ArrayList<>();
    private final Map<String, DraftOutcome> undecided = new LinkedHashMap<>();
    private final Map<String, ContextSnapshot> snapshots = new LinkedHashMap<>();

    void drafted(final DraftOutcome outcome, final ContextSnapshot snapshot) {
        drafts.add(outcome);
        undecided.put(outcome.segment().id(), outcome);
        snapshots.put(outcome.segment().id(), snapshot);
    }

    ContextSnapshot snapshot(final String segmentId) {
        return Objects.requireNonNull(snapshots.get(segmentId), () -> "no snapshot for " + segmentId);
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

    /**
     * An undecided draft of the same masked source whose markup restored, which an identical auxiliary text can take
     * instead of a call of its own; null when there is none.
     */
    DraftOutcome.@Nullable Drafted undecidedDraftOf(final String maskedSource) {
        for (final DraftOutcome outcome : undecided.values()) {
            if (outcome instanceof DraftOutcome.Drafted drafted
                    && drafted.segment().masked().equals(maskedSource)
                    && drafted.restoredTarget() != null
                    && !drafted.inPieces()) {
                return drafted;
            }
        }
        return null;
    }

    List<String> undecidedIds() {
        return List.copyOf(undecided.keySet());
    }
}
