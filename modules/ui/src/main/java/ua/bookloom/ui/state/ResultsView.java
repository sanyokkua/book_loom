package ua.bookloom.ui.state;

import java.util.Objects;

/**
 * What the Results view of the progress dialog needs: the results to list and what its two closing buttons do.
 *
 * @param results what the operation did and the means to put each row back
 * @param onShowChanged what "Show changed rows" does to the screen behind the dialog; it runs before {@code onDone}
 * @param onDone what closes the view; run by Done, by Escape and after "Show changed rows"
 */
public record ResultsView(ChangeResults results, Runnable onShowChanged, Runnable onDone) {

    /** Rejects a missing part. */
    public ResultsView {
        Objects.requireNonNull(results, "results");
        Objects.requireNonNull(onShowChanged, "onShowChanged");
        Objects.requireNonNull(onDone, "onDone");
    }
}
