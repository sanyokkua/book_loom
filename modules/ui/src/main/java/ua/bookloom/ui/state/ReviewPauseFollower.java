package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.Set;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ReviewMode;

/**
 * What the person's side of a review pause does: selects the segment the pause names, tells the screen to show it, and
 * resumes the run once the desk has confirmed an Accept or a Save edit on that segment.
 *
 * <p>A singleton, because a pause can arrive while another screen is shown and the panel is built later. Skip, Revert
 * and Retry leave the run paused: the person may still want the title bar's or the screen's Resume. Only a decision on
 * the very segment the pause names continues the run, so accepting another row of the list does not.
 */
@Slf4j
@Singleton
public final class ReviewPauseFollower {

    private static final Set<String> CONTINUING = Set.of("accept", "saveEdit");

    private final StateMirror mirror;
    private final ReviewViewModel review;
    private final TranslatingViewModel translating;
    private final ReviewMode reviewMode;
    private final ReadOnlyBooleanWrapper pausedForReview = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper acceptContinues = new ReadOnlyBooleanWrapper();

    /**
     * Starts following the mirror's review pause and the desk's confirmations.
     *
     * @param mirror the run's state, whose review section names the paused segment and announces decisions
     * @param review the panel's view model, which selects the paused segment
     * @param translating the run's controls, whose resume continues the run
     * @param reviewMode the mode of this launch; Manual words Accept as Accept &amp; continue
     */
    @Inject
    public ReviewPauseFollower(
            final StateMirror mirror,
            final ReviewViewModel review,
            final TranslatingViewModel translating,
            final ReviewMode reviewMode) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.review = Objects.requireNonNull(review, "review");
        this.translating = Objects.requireNonNull(translating, "translating");
        this.reviewMode = Objects.requireNonNull(reviewMode, "reviewMode");
        mirror.review().reviewPauseSegment().addListener((observed, was, now) -> follow(now));
        mirror.review().decided().addListener((observed, was, now) -> continueAfter(now));
        follow(mirror.review().reviewPauseSegment().get());
    }

    /**
     * Whether the run is paused for review, which is when the panel opens on its own.
     *
     * @return a read-only property; FX thread only
     */
    public ReadOnlyBooleanProperty pausedForReview() {
        return pausedForReview.getReadOnlyProperty();
    }

    /**
     * Whether Accept confirms the paused segment and resumes: the run is paused for review in Manual mode.
     *
     * @return a read-only property; FX thread only
     */
    public ReadOnlyBooleanProperty acceptContinues() {
        return acceptContinues.getReadOnlyProperty();
    }

    private void follow(final @Nullable String segmentId) {
        pausedForReview.set(segmentId != null);
        acceptContinues.set(segmentId != null && reviewMode == ReviewMode.MANUAL);
        if (segmentId != null) {
            log.debug("review pause at segment {} in {} mode: selecting it", segmentId, reviewMode);
            review.select(segmentId);
        }
    }

    private void continueAfter(final ReviewSection.@Nullable Decision decision) {
        final String paused = mirror.review().reviewPauseSegment().get();
        if (decision == null || paused == null) {
            return;
        }
        if (CONTINUING.contains(decision.action()) && paused.equals(decision.segmentId())) {
            log.debug("{} of the paused segment {} was confirmed: resuming", decision.action(), paused);
            translating.resume();
        } else {
            log.debug(
                    "{} of segment {} does not continue the pause at {}",
                    decision.action(),
                    decision.segmentId(),
                    paused);
        }
    }
}
