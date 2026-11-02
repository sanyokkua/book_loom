package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;

/**
 * How a service sees and ends the window's run.
 *
 * @param mirror where the run's state is read and cleared
 * @param runner what stops a run that is still going
 */
public record RunAccess(StateMirror mirror, TranslationRunner runner) {

    /** Rejects a missing part. */
    @Inject
    public RunAccess {
        Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(runner, "runner");
    }
}
