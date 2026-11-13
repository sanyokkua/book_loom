package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.api.pipeline.ReviewDesk;

/**
 * What the review view model reads and acts through.
 *
 * @param desk the review desk every read and action goes through
 * @param retry the model, the desk call and the hold on the run for a retry
 */
public record ReviewServices(ReviewDesk desk, ReviewRetry retry) {

    /** Rejects a missing part. */
    @Inject
    public ReviewServices {
        Objects.requireNonNull(desk, "desk");
        Objects.requireNonNull(retry, "retry");
    }
}
