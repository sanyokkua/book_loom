package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * A proofread window was read and its verdicts committed, so the live card can list it with what it changed.
 *
 * @param round the round the window was read in, 1 or 2
 * @param items the window's focus segments in reading order; never empty
 */
public record ProofreadWindowRead(int round, List<ProofreadItem> items) implements JobEvent {

    /** Rejects an impossible round and an empty window, and copies the items. */
    public ProofreadWindowRead {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        if (round < 1 || round > ProofreadRoundStarted.WHOLE_BOOK) {
            throw new IllegalArgumentException("round must be 1 or 2: " + round);
        }
        if (items.isEmpty()) {
            throw new IllegalArgumentException("a read window holds at least one segment");
        }
    }
}
