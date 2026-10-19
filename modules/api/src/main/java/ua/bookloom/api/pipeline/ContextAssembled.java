package ua.bookloom.api.pipeline;

import java.util.Objects;
import ua.bookloom.api.project.ContextSnapshot;

/**
 * Announces the context a segment's draft is about to be sent with, so a person can see what the model was given.
 *
 * <p>The texts are display text: the document's placeholders are already rendered as a person reads them.
 *
 * @param segmentId the segment the draft is for
 * @param context what the draft sees besides its own source: preceding translations, the rolling summary, the
 *     glossary names and the translation-memory hits
 */
public record ContextAssembled(String segmentId, ContextSnapshot context) implements JobEvent {

    /** Rejects an event missing either part. */
    public ContextAssembled {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(context, "context");
    }
}
