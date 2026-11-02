package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.state.RunStateSource;
import ua.bookloom.ui.state.TranslatingViewModel;

/**
 * The run as a screen sees it.
 *
 * @param runs the run-state row the run actions and the live line follow
 * @param translating what Start, Pause and Resume ask of the run
 */
public record RunView(RunStateSource runs, TranslatingViewModel translating) {

    /** Rejects a missing part. */
    @Inject
    public RunView {
        Objects.requireNonNull(runs, "runs");
        Objects.requireNonNull(translating, "translating");
    }
}
