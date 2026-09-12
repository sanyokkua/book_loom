package ua.bookloom.api.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The translatable inner content of a single block-level element, plus everything needed to translate it, judge
 * it, and write it back to the exact node it came from ({@code 02_Architecture/03_DOCUMENT_MODEL.md#data-model}).
 *
 * <p><strong>What is not populated yet.</strong> Nothing translates a segment, so every segment a reader produces
 * is {@link SegmentStatus#PENDING} with {@code confidence == 0.0} and {@code targetInner == null}. Those three
 * fields ship in the shape so the pipeline fills them later rather than changing a record several modules already
 * compile against. {@code masked} and {@code placeholders} were in that list until inline masking landed and are
 * not any more — they carry real values for all four formats.
 *
 * @param id the stable segment id, shaped {@code {unitId}:{ordinal}} (the shape is not enforced here — see
 *     {@code :document})
 * @param unit the owning unit's id
 * @param order this segment's position within its unit, in document order; never negative
 * @param kind what block this segment was parsed from
 * @param sourceInner the block's raw inner content, with inline descendant elements still present, exactly as
 *     parsed — untouched by masking (ADR-0031)
 * @param masked {@code sourceInner} with every protected span replaced by a {@code ⟦gN⟧} token; for EPUB and FB2
 *     its character data is additionally entity-decoded, so {@code masked} is character data rather than a
 *     markup string (ADR-0031). For Markdown and TXT it carries the segment's source text with only its inline
 *     constructs replaced.
 * @param placeholders the ordered map from each token's bare key form ({@code g0}, not {@code ⟦g0⟧}) to the exact
 *     source fragment it replaced, in first-appearance order (ADR-0031)
 * @param sourceHash the SHA-256 hash over the exact pre-mask {@code sourceInner}; computed by {@code :document},
 *     carried here as a plain field
 * @param prevKey the document-order previous segment's id, or {@code null} at the start of the unit
 * @param nextKey the document-order next segment's id, or {@code null} at the end of the unit
 * @param anchor where this segment's source text lives in its unit's immutable skeleton — a {@link NodeAnchor}
 *     into a parsed tree, or a {@link ByteSpanAnchor} into the original byte buffer
 * @param targetInner the translated inner content after unmask, or {@code null} until translated; always
 *     {@code null} in this change, since nothing is ever translated here
 * @param status this segment's position in the status machine; always {@link SegmentStatus#PENDING} in this change
 * @param confidence the QA/judge confidence in {@code [0,1]}; always {@code 0.0} in this change, since no real
 *     score is ever produced here
 */
public record Segment(
        String id,
        String unit,
        int order,
        SegmentKind kind,
        String sourceInner,
        String masked,
        Map<String, String> placeholders,
        String sourceHash,
        @Nullable String prevKey,
        @Nullable String nextKey,
        SkeletonAnchor anchor,
        @Nullable String targetInner,
        SegmentStatus status,
        double confidence) {

    private static final double MIN_CONFIDENCE = 0.0;
    private static final double MAX_CONFIDENCE = 1.0;

    /**
     * Validates the invariants a caller is entitled to assume and defensively copies {@code placeholders} into an
     * unmodifiable, order-preserving map — order is meaningful here (first-appearance placeholder order), which is
     * why this uses {@link LinkedHashMap} rather than {@link Map#copyOf}, whose iteration order is unspecified.
     */
    public Segment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(sourceInner, "sourceInner");
        Objects.requireNonNull(masked, "masked");
        Objects.requireNonNull(placeholders, "placeholders");
        Objects.requireNonNull(sourceHash, "sourceHash");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(status, "status");
        if (order < 0) {
            throw new IllegalArgumentException("order must be >= 0, but was " + order);
        }
        if (confidence < MIN_CONFIDENCE || confidence > MAX_CONFIDENCE) {
            throw new IllegalArgumentException("confidence must be within [0,1], but was " + confidence);
        }
        placeholders = Collections.unmodifiableMap(new LinkedHashMap<>(placeholders));
    }
}
