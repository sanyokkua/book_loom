package ua.bookloom.ui.state;

import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.i18n.MessageKey;

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
    private final ReadOnlyObjectWrapper<@Nullable MessageKey> lockReason = new ReadOnlyObjectWrapper<>();

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

    /** Why a selected segment can only be read, shown beside its actions; {@code null} when they are offered. */
    ReadOnlyObjectProperty<@Nullable MessageKey> lockReason() {
        return lockReason.getReadOnlyProperty();
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
        lockReason.set(segment == null || offered ? null : reasonLocked());
        accept.set(offered && !dirty && segment != null && isDecidable(segment.status()));
        allSegments.set(reviewMode == ReviewMode.UNATTENDED && mirror.runState().get() == RunState.COMPLETED);
        log.debug(
                "review availability: actions {}, accept {}, all segments {}, locked by {}",
                offered,
                accept.get(),
                allSegments.get(),
                lockReason.get());
    }

    private MessageKey reasonLocked() {
        return isTranslating() ? MessageKey.REVIEW_LOCKED_RUNNING : MessageKey.REVIEW_LOCKED_RETRY;
    }

    private static boolean isDecidable(final SegmentStatus status) {
        return status == SegmentStatus.FLAGGED || status == SegmentStatus.ACCEPTED;
    }
}
