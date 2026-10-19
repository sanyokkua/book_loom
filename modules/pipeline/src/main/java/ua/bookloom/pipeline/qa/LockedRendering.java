package ua.bookloom.pipeline.qa;

import java.util.Objects;

/**
 * One locked glossary term present in a segment, paired with the rendering it must come back as
 * ({@link GlossaryCheck}).
 *
 * @param term the locked glossary term as it occurs in the source
 * @param rendering the entered target-language rendering {@link GlossaryCheck} looks for in the target
 */
public record LockedRendering(String term, String rendering) {

    /**
     * Validates the invariants a caller is entitled to assume.
     */
    public LockedRendering {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(rendering, "rendering");
    }
}
