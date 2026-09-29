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
 * later draft of the same chunk reads an undecided one as its preceding target, and each decided record stores the
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
     * The masked target an undecided draft or memory reuse offers, or null when the draft's markup did not restore or
     * it was flagged.
     */
    @Nullable
    String maskedTarget(final String segmentId) {
        final DraftOutcome outcome = undecided.get(segmentId);
        if (outcome == null) {
            return null;
        }
        return switch (outcome) {
            case DraftOutcome.Drafted drafted -> drafted.maskedForm();
            case DraftOutcome.Reused reused -> reused.maskedTarget();
            case DraftOutcome.FlaggedAtOnce ignored -> null;
        };
    }

    List<String> undecidedIds() {
        return List.copyOf(undecided.keySet());
    }
}
