package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.CallSnapshot;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.api.pipeline.ExportProgressListener;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Turns what an export job announces into what the busy card shows: the worded step, the fraction of a counted step,
 * the facts of the export and the consistency pass's model calls. The job announces from its own thread; each
 * announcement is handed to the FX thread, where the activity lives.
 */
@Slf4j
final class ExportActivity implements ExportProgressListener {

    private final ActivityTracker.Handle handle;
    private final Messages messages;
    private final String fileName;
    private final @Nullable String model;
    private final Supplier<Instant> now;
    private final LiveCallState calls = new LiveCallState();

    ExportActivity(
            final ActivityTracker.Handle handle,
            final Messages messages,
            final Path destination,
            final @Nullable String model,
            final Supplier<Instant> now) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.now = Objects.requireNonNull(now, "now");
        final Path name = destination.getFileName();
        this.fileName = name == null ? destination.toString() : name.toString();
        this.model = model;
    }

    /** Hands the announcement to the FX thread. Any thread. */
    @Override
    public void onProgress(final ExportProgress progress) {
        Platform.runLater(() -> announce(progress));
    }

    /** Hands the call to the FX thread. Any thread. */
    @Override
    public void onCall(final CallSnapshot snapshot) {
        Platform.runLater(() -> show(snapshot));
    }

    /** Shows the call in the card's call view. FX thread only. */
    void show(final CallSnapshot snapshot) {
        log.debug("export call {} {} shown on the busy card", snapshot.callId(), snapshot.state());
        calls.snapshot(snapshot);
        handle.calls(calls.view(now.get()));
    }

    /** Shows the announcement. FX thread only. */
    void announce(final ExportProgress progress) {
        final String step = messages.get(stepKey(progress.step()));
        final boolean counted =
                progress.step() != ExportProgress.Step.VALIDATING && progress.step() != ExportProgress.Step.WRITING;
        final List<Activity.Detail> lines = new ArrayList<>();
        lines.add(new Activity.Detail(messages.get(MessageKey.ACTIVITY_DETAIL_FILE), fileName));
        if (model != null) {
            lines.add(new Activity.Detail(messages.get(MessageKey.ACTIVITY_DETAIL_MODEL), model));
        }
        if (counted) {
            lines.add(new Activity.Detail(
                    messages.get(
                            progress.step() == ExportProgress.Step.RETRY_DOUBTED
                                    ? MessageKey.ACTIVITY_DETAIL_SEGMENTS
                                    : MessageKey.ACTIVITY_DETAIL_REQUESTS),
                    messages.get(MessageKey.ACTIVITY_DETAIL_OF, progress.done(), progress.total())));
            handle.progress(progress.done(), progress.total(), step);
        } else {
            handle.indeterminate(step);
        }
        handle.details(lines);
    }

    private static MessageKey stepKey(final ExportProgress.Step step) {
        return switch (step) {
            case VALIDATING -> MessageKey.EXPORT_PROGRESS_VALIDATING;
            case CONSISTENCY_RETRY -> MessageKey.EXPORT_PROGRESS_RETRY;
            case RETRY_DOUBTED -> MessageKey.EXPORT_PROGRESS_RETRY_DOUBTED;
            case CONSISTENCY_NEIGHBOUR -> MessageKey.EXPORT_PROGRESS_NEIGHBOUR;
            case WRITING -> MessageKey.EXPORT_PROGRESS_WRITING;
        };
    }
}
