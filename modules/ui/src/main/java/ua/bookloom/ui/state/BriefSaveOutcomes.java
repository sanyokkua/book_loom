package ua.bookloom.ui.state;

import com.google.inject.ImplementedBy;
import java.util.function.UnaryOperator;
import ua.bookloom.api.pipeline.ProjectChange;
import ua.bookloom.api.project.BookBrief;

/**
 * What the brief's saver tells whoever cares once a save has been answered. Both calls come on the FX thread, in the
 * order the saves were answered.
 */
@ImplementedBy(BriefPairGuard.class)
public interface BriefSaveOutcomes {

    /** Hears nothing and changes nothing. */
    BriefSaveOutcomes NONE = new BriefSaveOutcomes() {};

    /**
     * A save was stored.
     *
     * @param projectId the project saved
     * @param brief the brief that was stored
     */
    default void stored(final String projectId, final BookBrief brief) {}

    /**
     * A save was refused by the service for a reason the person can act on.
     *
     * @param projectId the project the save was for
     * @param sent the brief that was refused
     * @param refusal why: a used pair or a used name
     * @return what to apply to the brief that waits behind this save, so it does not ask the same refused question
     *     again; the identity to leave it as it is
     */
    default UnaryOperator<BookBrief> refused(
            final String projectId, final BookBrief sent, final ProjectChange refusal) {
        return waiting -> waiting;
    }
}
