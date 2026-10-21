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
import ua.bookloom.api.pipeline.StepChanged;
import ua.bookloom.api.pipeline.StepKind;
import ua.bookloom.api.pipeline.StepResult;
import ua.bookloom.api.pipeline.StepState;
import ua.bookloom.api.pipeline.StepsPlanned;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

/**
 * Turns what an export job announces into what its progress dialog shows: the worded step, the facts of the export,
 * the consistency pass's model calls, and one step per unit of every stage, the segment it reread with its text
 * before and after and its verdict. The job announces from its own thread; each announcement is handed to the FX
 * thread, where the activity lives.
 */
@Slf4j
final class ExportActivity implements ExportProgressListener {

    private final ActivityTracker.Handle handle;
    private final Messages messages;
    private final String fileName;
    private final @Nullable String model;
    private final Supplier<Instant> now;
    private final LiveCallState calls = new LiveCallState();
    private ExportProgress.@Nullable Step stage;
    private int stageStart;
    private int stageDone;
    private int plannedSoFar;
    private int startedAt = -1;
    private String stageLabel = "";

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
        log.debug("export call {} {} shown on the progress dialog", snapshot.callId(), snapshot.state());
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
        steps(progress, step);
    }

    // Each stage plans its units as steps; units done since the last announcement end, the next one starts.
    private void steps(final ExportProgress progress, final String label) {
        if (progress.step() != stage) {
            log.debug("export stage {} plans {} steps after {}", progress.step(), progress.total(), plannedSoFar);
            // The new stage is planned before the old one's open step ends, so the bar never reads a finished stage
            // as the whole export.
            handle.steps(new StepsPlanned(StepKind.REQUEST, progress.total(), false));
            endStarted();
            stage = progress.step();
            stageLabel = label;
            stageStart = plannedSoFar;
            stageDone = 0;
            plannedSoFar += progress.total();
        }
        for (int unit = stageDone; unit < progress.done(); unit++) {
            final boolean last = unit == progress.done() - 1;
            final String item = last ? progress.itemRef() : null;
            handle.step(
                    new StepChanged(StepKind.REQUEST, label, stageStart + unit, StepState.ENDED, item, StepResult.DONE),
                    item == null
                            ? null
                            : new StepEntry.StepText(progress.before(), progress.after(), progress.verdict()));
        }
        stageDone = Math.max(stageDone, progress.done());
        final int next = stageStart + stageDone;
        if (stageDone < progress.total() && startedAt != next) {
            startedAt = next;
            handle.steps(new StepChanged(StepKind.REQUEST, label, next, StepState.STARTED, null, null));
        }
    }

    private void endStarted() {
        if (stage != null && startedAt >= stageStart + stageDone) {
            handle.steps(
                    new StepChanged(StepKind.REQUEST, stageLabel, startedAt, StepState.ENDED, null, StepResult.DONE));
        }
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
