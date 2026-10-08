package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * A segment retry from the review panel: the model it uses, the desk call, and the hold on the run while the desk has
 * not answered. Kept apart from {@link ReviewViewModel} so that class stays a readable size.
 *
 * <p>The model is built with the provider settings' selection, read on the FX thread, and created off it, because
 * creating one can load it. The hold is published before the call is queued and withdrawn when the call returns,
 * whatever it returns. A retry is refused in place while other model work runs (a glossary scan, an export, a provider
 * inference test), and is registered with the {@link ActivityTracker} while it runs.
 */
@Slf4j
public final class ReviewRetry {

    /**
     * A retry that holds the run and is registered as an activity, waiting to be dispatched.
     *
     * @param call the desk call taking the project id and the segment id; it ends the activity and the hold itself
     * @param abandon ends the activity and the hold when the call is never dispatched; FX thread only
     */
    record Pending(BiFunction<String, String, Result<SegmentRecord>> call, Runnable abandon) {}

    private final ReviewDesk desk;
    private final ChatModelFactory models;
    private final StateMirror mirror;
    private final SettingsViewModel settings;
    private final Messages messages;
    private final ActivityTracker activities;

    /**
     * Creates the helper.
     *
     * @param desk the review desk that re-translates the segment
     * @param models the port a retry's model is created through
     * @param mirror the run's state, whose review section carries the hold
     * @param settings the provider settings that say which model a retry uses
     * @param messages the catalogue the in-place message is worded from
     * @param activities the model work under way, which a retry must not overlap
     */
    @Inject
    public ReviewRetry(
            final ReviewDesk desk,
            final ChatModelFactory models,
            final StateMirror mirror,
            final SettingsViewModel settings,
            final Messages messages,
            final ActivityTracker activities) {
        this.desk = Objects.requireNonNull(desk, "desk");
        this.models = Objects.requireNonNull(models, "models");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.activities = Objects.requireNonNull(activities, "activities");
    }

    /**
     * Holds the run and returns the desk call to make off the FX thread, or refuses in place when no model is chosen.
     * FX thread only.
     *
     * @param segmentId the non-null id of the segment retried
     * @param note the person's guidance, or null for none
     * @param lowerTemperature whether the retry samples at a lower temperature
     * @param inPlace where the refusal is shown
     * @return the pending retry, whose call takes the project id and the segment id and which must be abandoned when
     *     that call is never dispatched; or null when the retry was refused
     */
    @Nullable
    Pending begin(
            final String segmentId,
            final @Nullable String note,
            final boolean lowerTemperature,
            final Consumer<String> inPlace) {
        final Optional<ModelSelection> chosen = settings.selection();
        if (chosen.isEmpty()) {
            log.debug("retry of segment {} refused: no model is chosen", segmentId);
            inPlace.accept(messages.get(MessageKey.REVIEW_RETRY_NO_MODEL));
            return null;
        }
        final Optional<ActivityKind> conflict = activities.conflictFor(ActivityKind.REVIEW_RETRY);
        if (conflict.isPresent()) {
            log.debug("retry of segment {} refused: {} is running", segmentId, conflict.get());
            inPlace.accept(messages.get(
                    MessageKey.ACTIVITY_BLOCKED, messages.get(conflict.get().label())));
            return null;
        }
        logRetry(segmentId, note, lowerTemperature);
        mirror.review().publishRetryInFlight(true);
        final InterruptibleWork running = new InterruptibleWork();
        final ActivityTracker.Handle handle = activities.begin(ActivityKind.REVIEW_RETRY, running::stop);
        final BiFunction<String, String, Result<SegmentRecord>> call = (projectId, id) -> {
            try {
                return running.run(() -> call(projectId, id, chosen.get(), note, lowerTemperature), () -> stopped(id));
            } finally {
                Platform.runLater(handle::end);
            }
        };
        return new Pending(call, () -> abandon(segmentId, handle));
    }

    private void abandon(final String segmentId, final ActivityTracker.Handle handle) {
        log.warn("retry of segment {} was never dispatched; its activity and hold end", segmentId);
        handle.end();
        mirror.review().publishRetryInFlight(false);
    }

    private static void logRetry(final String segmentId, final @Nullable String note, final boolean lowerTemperature) {
        log.info(
                "retrying segment {} with lower temperature {}, note given {}",
                segmentId,
                lowerTemperature,
                note != null);
        log.trace("retry note of segment {}: {}", segmentId, note);
    }

    private Result<SegmentRecord> stopped(final String segmentId) {
        log.info("retry of segment {} stopped before it began", segmentId);
        mirror.review().publishRetryInFlight(false);
        return Result.err(AppError.of(ErrorCode.cancelled, "Retry stopped", "It was stopped before it began."));
    }

    private Result<SegmentRecord> call(
            final String projectId,
            final String segmentId,
            final ModelSelection selection,
            final @Nullable String note,
            final boolean lowerTemperature) {
        try {
            final Result<ChatModel> created = models.create(selection);
            final ChatModel model = created.data();
            if (model == null) {
                log.debug("no model was created for the retry of segment {}", segmentId);
                return Result.err(Objects.requireNonNull(created.error(), "error"));
            }
            return desk.retry(projectId, segmentId, note, lowerTemperature, model);
        } finally {
            mirror.review().publishRetryInFlight(false);
        }
    }
}
