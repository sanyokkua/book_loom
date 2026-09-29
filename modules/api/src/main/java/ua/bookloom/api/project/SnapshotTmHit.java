package ua.bookloom.api.project;

import java.util.Objects;

/**
 * A translation-memory hit as a retry's context snapshot saw it — the text, not the id, so a retry replays exactly
 * even after translation memory changes.
 *
 * @param kind how closely the hit matched
 * @param source the matched source text
 * @param target the matched target text
 */
public record SnapshotTmHit(TmHitKind kind, String source, String target) {

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public SnapshotTmHit {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
    }

    /** How closely a translation-memory hit matched the segment being translated. */
    public enum TmHitKind {

        /** The same source text stored under a different context key — offered to the model as a hint. */
        EXACT,

        /** The source text and the context key both match — the entry a run reuses without a model call. */
        CONTEXT,

        /** A similar-but-not-identical source text, matched by fuzzy scoring. */
        FUZZY
    }
}
