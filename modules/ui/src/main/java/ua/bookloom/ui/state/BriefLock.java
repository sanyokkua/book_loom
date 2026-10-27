package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import lombok.extern.slf4j.Slf4j;

/**
 * The two facts the Book Brief reads from the run: it is read-only while a translation is at work (translating,
 * pausing, stopping, a stuck call, waiting for the AI program), because a change then would apply to some segments and
 * not others; and its languages are locked once any segment is decided or the run is at work, because the project's
 * stored translations are of that pair. Both follow the run-state source, the one place those facts are worked out.
 * FX thread only.
 */
@Slf4j
@Singleton
public final class BriefLock {

    private final BooleanBinding readOnly;
    private final BooleanBinding languagesLocked;

    /**
     * Follows the run.
     *
     * @param runs the run-state source whose row and decided count the lock reads
     * @param stored the stored project, whose decided count tells a reopened project before any run of this session
     */
    @Inject
    public BriefLock(final RunStateSource runs, final BriefProject stored) {
        Objects.requireNonNull(runs, "runs");
        Objects.requireNonNull(stored, "stored");
        this.readOnly = Bindings.createBooleanBinding(() -> runs.view().get().isAtWork(), runs.view());
        this.languagesLocked = Bindings.createBooleanBinding(
                () -> runs.decidedSegments().get() > 0
                        || stored.decidedSegments().get() > 0
                        || runs.view().get().isAtWork(),
                runs.decidedSegments(),
                stored.decidedSegments(),
                runs.view());
        readOnly.addListener((observed, was, now) -> log.debug("the brief is read-only: {}", now));
        languagesLocked.addListener((observed, was, now) -> log.debug("the brief's languages are locked: {}", now));
    }

    /** {@return a binding that is {@code true} while the brief cannot be changed at all} */
    public BooleanBinding readOnly() {
        return readOnly;
    }

    /** {@return a binding that is {@code true} while the two language boxes cannot be changed} */
    public BooleanBinding languagesLocked() {
        return languagesLocked;
    }
}
