package ua.bookloom.api.pipeline;

import java.util.Objects;
import ua.bookloom.api.document.SegmentKind;

/**
 * One source segment of a unit as the Structure screen lists it before any model call, with the chunk a run would
 * pack it into. The chunk is a plan: a real run drafts in token-budgeted batches that can differ at the edges.
 *
 * @param segmentId the segment's id in the book
 * @param locator the name the review desk and the logs show the segment by
 * @param kind what the segment is
 * @param displaySource the source text a reader sees, formatting tokens removed
 * @param maskedSource the source text with each formatting token as {@code ⟦gN⟧}, for the readable view
 * @param tokenEstimate the estimated tokens of the masked source
 * @param keptVerbatim whether a run copies the text as it is, with no model call (a numeral, a scene break)
 * @param keptAsSource whether the brief leaves this auxiliary kind untranslated
 * @param chunkIndex the 1-based planned chunk within the unit; 0 when the segment is not chunked
 * @param chunkCount how many planned chunks the unit has; 0 when the segment is not chunked
 * @param oversized whether the segment alone exceeds a chunk's token budget
 */
public record SegmentPreview(
        String segmentId,
        String locator,
        SegmentKind kind,
        String displaySource,
        String maskedSource,
        int tokenEstimate,
        boolean keptVerbatim,
        boolean keptAsSource,
        int chunkIndex,
        int chunkCount,
        boolean oversized) {

    /** Rejects a missing text or a negative count. */
    public SegmentPreview {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(displaySource, "displaySource");
        Objects.requireNonNull(maskedSource, "maskedSource");
        if (tokenEstimate < 0 || chunkIndex < 0 || chunkCount < 0) {
            throw new IllegalArgumentException("no count may be negative");
        }
    }
}
