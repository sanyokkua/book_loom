package ua.bookloom.api.project;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The texts a draft's context saw, held so a retry replays exactly with no repository lookup — even after the
 * glossary, translation memory or rolling summary have since changed.
 *
 * @param precedingTargets the preceding segments' target texts the draft saw, in order
 * @param glossary the glossary terms the draft saw
 * @param tmHits the translation-memory hits the draft saw
 * @param summary the rolling summary text the draft saw, or null when none existed yet
 * @param styleSheet the style sheet text the draft saw
 * @param lexicon the established recurring-term renderings the draft was asked to keep consistent
 */
public record ContextSnapshot(
        List<String> precedingTargets,
        List<SnapshotTerm> glossary,
        List<SnapshotTmHit> tmHits,
        @Nullable String summary,
        String styleSheet,
        List<SnapshotRendering> lexicon) {

    /**
     * Validates the invariants a caller is entitled to assume and defensively copies the lists, so a caller-held
     * mutable list cannot corrupt this record after construction.
     */
    public ContextSnapshot {
        Objects.requireNonNull(precedingTargets, "precedingTargets");
        Objects.requireNonNull(glossary, "glossary");
        Objects.requireNonNull(tmHits, "tmHits");
        Objects.requireNonNull(styleSheet, "styleSheet");
        Objects.requireNonNull(lexicon, "lexicon");
        precedingTargets = List.copyOf(precedingTargets);
        glossary = List.copyOf(glossary);
        tmHits = List.copyOf(tmHits);
        lexicon = List.copyOf(lexicon);
    }

    /** A snapshot of a draft that was shown no recurring-term renderings. */
    public ContextSnapshot(
            final List<String> precedingTargets,
            final List<SnapshotTerm> glossary,
            final List<SnapshotTmHit> tmHits,
            @Nullable final String summary,
            final String styleSheet) {
        this(precedingTargets, glossary, tmHits, summary, styleSheet, List.of());
    }
}
