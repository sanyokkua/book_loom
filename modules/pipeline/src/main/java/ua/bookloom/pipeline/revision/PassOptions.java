package ua.bookloom.pipeline.revision;

import java.util.Objects;
import ua.bookloom.api.llm.ModelProfile;

/**
 * Which model steps a consistency pass runs besides the name sweep and the gender re-render.
 *
 * @param reviseDoubted whether every flagged or audit-doubted segment is drafted again first, kept only when better,
 *     and then checked against its neighbours too
 * @param everySegment whether the check against the neighbours reads every machine-owned paragraph, not only the
 *     repaired, flagged and doubted ones
 * @param profile the profile of the model the pass's calls reach, which sizes a retried draft's frame and window
 */
public record PassOptions(boolean reviseDoubted, boolean everySegment, ModelProfile profile) {

    /** The Max run's own backward revision: the neighbour check of the repaired and flagged paragraphs only. */
    public static final PassOptions BACKWARD_REVISION = new PassOptions(false, false);

    /** Rejects a missing profile. */
    public PassOptions {
        Objects.requireNonNull(profile, "profile");
    }

    /** The steps for a model whose profile nobody read: the defaults size a retried draft. */
    public PassOptions(final boolean reviseDoubted, final boolean everySegment) {
        this(reviseDoubted, everySegment, ModelProfile.defaults());
    }
}
