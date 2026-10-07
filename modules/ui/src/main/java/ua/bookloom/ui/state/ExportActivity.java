package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ExportProgress;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Turns what an export job announces into what the busy card shows: the worded step, the fraction of a counted step
 * and the facts of the export. The job announces from its own thread; {@link #announceLater} hands each to the FX
 * thread, where the activity lives.
 */
final class ExportActivity {

    private final ActivityTracker.Handle handle;
    private final Messages messages;
    private final String fileName;
    private final @Nullable String model;

    ExportActivity(
            final ActivityTracker.Handle handle,
            final Messages messages,
            final Path destination,
            final @Nullable String model) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.messages = Objects.requireNonNull(messages, "messages");
        final Path name = destination.getFileName();
        this.fileName = name == null ? destination.toString() : name.toString();
        this.model = model;
    }

    /** Hands the announcement to the FX thread. Any thread. */
    void announceLater(final ExportProgress progress) {
        Platform.runLater(() -> announce(progress));
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
                    messages.get(MessageKey.ACTIVITY_DETAIL_REQUESTS),
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
            case CONSISTENCY_NEIGHBOUR -> MessageKey.EXPORT_PROGRESS_NEIGHBOUR;
            case WRITING -> MessageKey.EXPORT_PROGRESS_WRITING;
        };
    }
}
