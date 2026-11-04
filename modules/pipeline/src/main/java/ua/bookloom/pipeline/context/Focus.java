package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What one call works on: its items in reading order and, for a repair, the reasons each item failed.
 *
 * @param items the segments, in reading order; copied, may be empty only for an analysis call
 * @param reasons the findings that sent a segment back, by segment id; copied, empty for a draft
 */
public record Focus(List<FocusItem> items, Map<String, List<String>> reasons) {

    /** Copies the parts. */
    public Focus {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        reasons = Map.copyOf(Objects.requireNonNull(reasons, "reasons"));
    }

    /**
     * The ids of the focus's segments.
     *
     * @return never null; in the items' order
     */
    public List<String> segmentIds() {
        return items.stream().map(FocusItem::segmentId).toList();
    }
}
