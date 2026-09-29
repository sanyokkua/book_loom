package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.ErrorCode;

/**
 * Where a failure is shown, decided from the typed code it carries and from nothing else.
 *
 * <p>This places a failure met outside a run's own outcome; a run's outcome is routed by its state instead: stopped is
 * the stopped state, a failed run with {@code validation} is a refused start shown in place, and any other failed run
 * opens the blocking dialog.
 *
 * <p>{@link #of(ErrorCode)} is an exhaustive switch with no {@code default}, so a code added later is a compile error
 * here rather than a failure quietly drawn on the wrong surface.
 */
public enum FailureSurface {
    /** The run's own provider-error state, with a route to the provider settings and no dialog. */
    PROVIDER_ERROR,
    /** A segment the run flags and goes on from; the run itself does not stop. */
    FLAGGED_SEGMENT,
    /** A transient warning toast; nothing blocks and no dialog opens. */
    WARNING_TOAST,
    /** The stopped state the person chose; neither a dialog nor a message of error severity. */
    STOPPED,
    /** A message on the screen that refused, naming what is wrong. */
    IN_PLACE,
    /** The blocking error dialog. */
    DIALOG,
    /** Only where a model list was asked for; a run shows nothing extra for it. */
    SETTINGS_ONLY;

    /**
     * Assigns a code to its surface.
     *
     * @param code the code the failure carries; never {@code null}
     * @return the one surface that shows it; never {@code null}
     */
    public static FailureSurface of(final ErrorCode code) {
        return switch (Objects.requireNonNull(code, "code")) {
            case unreachable,
                    timeout,
                    auth,
                    rateLimited,
                    upstream,
                    modelNotFound,
                    modelUnavailable,
                    missingCredential -> PROVIDER_ERROR;
            case contextWindow, emptyCompletion -> FLAGGED_SEGMENT;
            case busy -> WARNING_TOAST;
            case cancelled -> STOPPED;
            case validation -> IN_PLACE;
            case internal -> DIALOG;
            case discoveryFailed -> SETTINGS_ONLY;
        };
    }
}
