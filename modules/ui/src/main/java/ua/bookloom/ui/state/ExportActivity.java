package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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
 * one group row per stage reached and one step per unit of every stage. The job announces from its own thread; each
 * announcement is handed to the FX thread, where the activity lives.
 */
@Slf4j
final class ExportActivity implements ExportProgressListener {

    private final ActivityTracker.Handle handle;
    private final Messages messages;
    private final String fileName;
    private ExportProgress.@Nullable Step stage;
    private int stageStart;
    private int stageDone;
    private int plannedSoFar;
    private int startedAt = -1;
    private String stageLabel = "";
    private String groupLabel = "";

    ExportActivity(final ActivityTracker.Handle handle, final Messages messages, final Path destination) {
        this.handle = Objects.requireNonNull(handle, "handle");
        this.messages = Objects.requireNonNull(messages, "messages");
        final Path name = destination.getFileName();
        this.fileName = name == null ? destination.toString() : name.toString();
    }

    /** Hands the announcement to the FX thread. Any thread. */
    @Override
    public void onProgress(final ExportProgress progress) {
        Platform.runLater(() -> announce(progress));
    }

    /** Shows the announcement. FX thread only. */
    void announce(final ExportProgress progress) {
        final String step = messages.get(stepKey(progress.step()));
        final List<Activity.Detail> lines = new ArrayList<>();
        lines.add(new Activity.Detail(messages.get(MessageKey.ACTIVITY_DETAIL_FILE), fileName));
        handle.indeterminate(step);
        handle.details(lines);
        stages(progress, step);
        steps(progress, step);
    }

    // One group row per stage reached, so the dialog can show the stages apart from their items; no percentage counts
    // them while items are planned.
    private void stages(final ExportProgress progress, final String label) {
        if (progress.step() != stage && stage != null) {
            handle.step(
                    new StepChanged(
                            StepKind.STAGE,
                            groupLabel,
                            ExportStages.rowOf(stage),
                            StepState.ENDED,
                            null,
                            StepResult.DONE),
                    null);
        }
        final String shown = label;
        final boolean ended = progress.total() > 0 && progress.done() >= progress.total();
        groupLabel = shown;
        handle.step(
                new StepChanged(
                        StepKind.STAGE,
                        shown,
                        ExportStages.rowOf(progress.step()),
                        ended ? StepState.ENDED : StepState.STARTED,
                        null,
                        ended ? StepResult.DONE : null),
                null);
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
            handle.step(
                    new StepChanged(StepKind.REQUEST, label, stageStart + unit, StepState.ENDED, null, StepResult.DONE),
                    null);
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
            case WRITING -> MessageKey.EXPORT_PROGRESS_WRITING;
        };
    }
}
