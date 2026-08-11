package ua.bookloom.api.document;

/**
 * Locates a segment's source text within its unit's immutable skeleton, in whichever way that skeleton can be
 * addressed: a tree-shaped skeleton by {@link NodeAnchor}, a byte-buffer skeleton by {@link ByteSpanAnchor}
 * (ADR-0025, {@code 02_Architecture/03_DOCUMENT_MODEL.md#data-model}).
 *
 * <p><strong>Why a sealed hierarchy rather than one record with nullable halves.</strong> The two kinds share no
 * component — a byte span has no node path and a tree has no offsets — so a single record would carry two
 * always-null fields and push the "which half is populated?" question to every consumer. Sealing it instead makes
 * an exhaustive pattern-matching {@code switch} the natural way to branch, which is what names every consumer at
 * compile time if a fifth format ever adds a third skeleton kind.
 */
public sealed interface SkeletonAnchor permits NodeAnchor, ByteSpanAnchor {}
