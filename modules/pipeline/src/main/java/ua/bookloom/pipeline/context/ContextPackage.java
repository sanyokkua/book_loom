package ua.bookloom.pipeline.context;

import java.util.Objects;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * One draft's context: what the prompt carries and the texts recorded so a retry can rebuild it.
 *
 * @param draftContext the prompt's optional blocks
 * @param snapshot the same content as texts, never ids
 */
public record ContextPackage(DraftContext draftContext, ContextSnapshot snapshot) {

    /** Rejects a missing component. */
    public ContextPackage {
        Objects.requireNonNull(draftContext, "draftContext");
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
