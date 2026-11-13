package ua.bookloom.ui.screen;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.ui.state.ReviewViewModel;

/**
 * How the translating screen reaches the review desk.
 *
 * @param review the review desk's view model, which counts the segments to review
 * @param desk what opens the review desk from Review (n) and the strip
 */
public record ReviewAccess(ReviewViewModel review, ReviewDeskLauncher desk) {

    /** Rejects a missing part. */
    @Inject
    public ReviewAccess {
        Objects.requireNonNull(review, "review");
        Objects.requireNonNull(desk, "desk");
    }
}
