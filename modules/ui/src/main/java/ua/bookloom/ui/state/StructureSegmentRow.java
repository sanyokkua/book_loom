package ua.bookloom.ui.state;

import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.pipeline.SegmentPreview;

/**
 * One row of the structure screen's segment list: a segment as a run would meet it, and whether it opens a new planned
 * chunk, which the list draws as a visible boundary.
 *
 * @param preview the segment's listing entry
 * @param chunkStart whether this is the first segment of its planned chunk
 */
public record StructureSegmentRow(SegmentPreview preview, boolean chunkStart) {

    /** Rejects a missing preview. */
    public StructureSegmentRow {
        Objects.requireNonNull(preview, "preview");
    }

    /**
     * Whether the row passes the screen's filters.
     *
     * @param needle the text a person typed; blank matches every row, otherwise a case-insensitive substring of the
     *     shown text or the locator
     * @param kind the kind chosen, or {@code null} for every kind
     * @return {@code true} if the row is kept in the list
     */
    public boolean matches(final String needle, final @Nullable SegmentKind kind) {
        Objects.requireNonNull(needle, "needle");
        if (kind != null && preview.kind() != kind) {
            return false;
        }
        if (needle.isBlank()) {
            return true;
        }
        final String wanted = needle.strip().toLowerCase(Locale.ROOT);
        return preview.displaySource().toLowerCase(Locale.ROOT).contains(wanted)
                || preview.locator().toLowerCase(Locale.ROOT).contains(wanted);
    }
}
