package ua.bookloom.api.project;

import java.util.Objects;

/**
 * A recurring-term rendering as a retry's context snapshot saw it — the texts, so a retry replays exactly even after
 * the lexicon's counts have moved.
 *
 * @param term the source term
 * @param rendering the rendering the draft was asked to keep consistent
 */
public record SnapshotRendering(String term, String rendering) {

    /** Rejects missing text. */
    public SnapshotRendering {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(rendering, "rendering");
    }
}
