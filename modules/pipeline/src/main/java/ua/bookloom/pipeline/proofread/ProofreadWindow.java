package ua.bookloom.pipeline.proofread;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One proofread window (design D15 "Windows"): the focus segments one call reads and may change, the reasons each was
 * marked, and one read-only neighbour on each side.
 *
 * @param focusIds the focus segments in reading order; never empty
 * @param reasons each focus segment's reasons, blocking first; a segment between two marks has none
 * @param beforeId the decided story segment just before the focus, or null when there is none
 * @param afterId the decided story segment just after the focus, or null when there is none
 */
public record ProofreadWindow(
        List<String> focusIds,
        Map<String, List<String>> reasons,
        @Nullable String beforeId,
        @Nullable String afterId) {

    /** Copies the parts and rejects an empty focus. */
    public ProofreadWindow {
        focusIds = List.copyOf(Objects.requireNonNull(focusIds, "focusIds"));
        reasons = Map.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (focusIds.isEmpty()) {
            throw new IllegalArgumentException("a proofread window needs a focus");
        }
    }

    /** {@return the window's name in the log: its first and last focus segment} */
    String label() {
        return focusIds.size() == 1 ? focusIds.getFirst() : focusIds.getFirst() + ".." + focusIds.getLast();
    }
}
