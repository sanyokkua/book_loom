package ua.bookloom.ui.state;

import java.util.Objects;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * Which review actions are offered: a segment is selected, no run is translating and no retry is in flight. Kept apart from {@link ReviewViewModel} so that class stays a readable
 * size. FX thread only.
 */
@Slf4j
final class ReviewAvailability {

    private final StateMirror mirror;
    private final ReadOnlyBooleanWrapper actions = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper accept = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper translating = new ReadOnlyBooleanWrapper();
    private final ReadOnlyObjectWrapper<@Nullable MessageKey> lockReason = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<@Nullable MessageKey> acceptNote = new ReadOnlyObjectWrapper<>();

    /** Watches the run's mode and the retry flag, calling {@code refresh} when either changes. */
    ReviewAvailability(final StateMirror mirror, final Runnable refresh) {
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        Objects.requireNonNull(refresh, "refresh");
        mirror.runMode().addListener((observed, was, now) -> refresh.run());
        mirror.review().retryInFlight().addListener((observed, was, now) -> refresh.run());
    }

    ReadOnlyBooleanProperty actions() {
        return actions.getReadOnlyProperty();
    }

    ReadOnlyBooleanProperty accept() {
        return accept.getReadOnlyProperty();
    }

    /** Whether a run translates now, which makes the desk read-only and offers Pause. */
    ReadOnlyBooleanProperty translating() {
        return translating.getReadOnlyProperty();
    }

    /** Why a selected segment can only be read, shown beside its actions; {@code null} when they are offered. */
    ReadOnlyObjectProperty<@Nullable MessageKey> lockReason() {
        return lockReason.getReadOnlyProperty();
    }

    /** Why Accept is off for a segment that kept no translation; {@code null} otherwise. */
    ReadOnlyObjectProperty<@Nullable MessageKey> acceptNote() {
        return acceptNote.getReadOnlyProperty();
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
        // A segment that kept no translation has nothing to accept: accepting it would count the source as reviewed.
        final boolean decidable = segment != null && isDecidable(segment.status());
        final boolean hasTarget = segment != null && hasTarget(segment);
        accept.set(offered && !dirty && decidable && hasTarget);
        acceptNote.set(offered && decidable && !hasTarget ? noTargetNote(segment) : null);
        translating.set(isTranslating());
        log.debug(
                "review availability: actions {}, accept {}, translating {}, locked by {}",
                offered,
                accept.get(),
                translating.get(),
                lockReason.get());
    }

    // A refused reply the editor shows can be kept with Save edit, so the note points there; with none, there is
    // nothing to keep and the person must write or retry first.
    private static MessageKey noTargetNote(final @Nullable SegmentView segment) {
        return segment != null && segment.rejectedTarget() != null
                ? MessageKey.REVIEW_ACCEPT_NEEDS_SAVE
                : MessageKey.REVIEW_ACCEPT_NEEDS_TARGET;
    }

    private MessageKey reasonLocked() {
        return isTranslating() ? MessageKey.REVIEW_LOCKED_RUNNING : MessageKey.REVIEW_LOCKED_RETRY;
    }

    private static boolean hasTarget(final SegmentView segment) {
        return segment.maskedUserTarget() != null || segment.maskedMachineTarget() != null;
    }

    private static boolean isDecidable(final SegmentStatus status) {
        return status == SegmentStatus.FLAGGED || status == SegmentStatus.ACCEPTED;
    }
}
