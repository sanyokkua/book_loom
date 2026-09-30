package ua.bookloom.ui.state;

import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.SegmentView;

/**
 * Which review actions are offered: a segment is selected, no run is translating and no retry is in flight; and
 * whether the All segments browse is offered. Kept apart from {@link ReviewViewModel} so that class stays a readable
 * size. FX thread only.
 */
@Slf4j
final class ReviewAvailability {

    private final StateMirror mirror;
    private final ReviewMode reviewMode;
    private final ReadOnlyBooleanWrapper actions = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper accept = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper allSegments = new ReadOnlyBooleanWrapper();

    ReviewAvailability(final StateMirror mirror, final ReviewMode reviewMode) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.reviewMode = Objects.requireNonNull(reviewMode, "reviewMode");
    }

    ReadOnlyBooleanProperty actions() {
        return actions.getReadOnlyProperty();
    }

    ReadOnlyBooleanProperty accept() {
        return accept.getReadOnlyProperty();
    }

    ReadOnlyBooleanProperty allSegments() {
        return allSegments.getReadOnlyProperty();
    }

    /** Whether the run is translating, in which case review is unavailable and a retry is answered busy. */
    boolean isTranslating() {
        final RunState state = mirror.runState().get();
        return state == RunState.RUNNING || state == RunState.PAUSING || state == RunState.STOPPING;
    }

    void refresh(final @Nullable SegmentView segment, final boolean dirty) {
        final boolean offered = segment != null
                && !isTranslating()
                && !mirror.review().retryInFlight().get();
        actions.set(offered);
        accept.set(offered && !dirty && segment != null && isDecidable(segment.status()));
        allSegments.set(reviewMode == ReviewMode.UNATTENDED && mirror.runState().get() == RunState.COMPLETED);
        log.debug(
                "review availability: actions {}, accept {}, all segments {}",
                offered,
                accept.get(),
                allSegments.get());
    }

    private static boolean isDecidable(final SegmentStatus status) {
        return status == SegmentStatus.FLAGGED || status == SegmentStatus.ACCEPTED;
    }
}
