package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.state.BriefEstimate;
import ua.bookloom.ui.state.BriefLock;
import ua.bookloom.ui.state.BriefProject;
import ua.bookloom.ui.state.StartAdvice;

/**
 * What the Book Brief shows about its project.
 *
 * @param lock whether the brief is read-only and its languages locked
 * @param stored the open project's stored name and size
 * @param estimate the time estimate under the quality choice
 * @param advice the language, quant, licence and thinking advice for the model the run will use
 */
public record BriefFacts(BriefLock lock, BriefProject stored, BriefEstimate estimate, StartAdvice advice) {

    /** Rejects a missing part. */
    @Inject
    public BriefFacts {
        Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(estimate, "estimate");
        Objects.requireNonNull(advice, "advice");
    }
}
