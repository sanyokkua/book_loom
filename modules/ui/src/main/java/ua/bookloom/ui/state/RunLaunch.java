package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;

/**
 * What starts or continues a run.
 *
 * @param starter builds a run on the open book and starts it
 * @param guard what a continued run asks about the model before anything is prepared
 */
public record RunLaunch(RunStarter starter, ContinueGuard guard) {

    /** Rejects a missing part. */
    @Inject
    public RunLaunch {
        Objects.requireNonNull(starter, "starter");
        Objects.requireNonNull(guard, "guard");
    }
}
