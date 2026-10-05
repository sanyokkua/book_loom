package ua.bookloom.ui.state;

import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.pipeline.JobProgress;
import ua.bookloom.api.pipeline.JobReport;

/**
 * The single observable state of the current run, and the one bridge from engine threads to the scene graph.
 *
 * <p>Exposed properties are read-only and the activity log is unmodifiable, so the {@code publish*} methods are the
 * only mutators. Each wraps its mutation in {@link Platform#runLater(Runnable)}, which is what makes it callable from
 * any thread; a second bridge would make the rule that the scene graph is touched on the FX thread only unenforceable.
 * Publications are applied in the order they were made, because the FX queue is ordered.
 */
@Slf4j
@Singleton
public final class StateMirror {

    /** The activity log keeps this many of the newest entries; a whole book would otherwise exhaust the list view. */
    public static final int MAX_LOG_ENTRIES = 500;

    /** The value of {@link #waitingSeconds()} while no model request has been outstanding long enough to mention. */
    public static final int NOT_WAITING = -1;

    private final ReadOnlyObjectWrapper<RunState> runState = new ReadOnlyObjectWrapper<>(RunState.IDLE);
    private final ReadOnlyIntegerWrapper accepted = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper flagged = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper remaining = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper total = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper section = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper sections = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper chunk = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper chunks = new ReadOnlyIntegerWrapper();
    private final ReadOnlyIntegerWrapper waitingSeconds = new ReadOnlyIntegerWrapper(NOT_WAITING);
    private final ReadOnlyObjectWrapper<RunFigures> figures = new ReadOnlyObjectWrapper<>(RunFigures.EMPTY);
    private final ReadOnlyDoubleWrapper progressFraction = new ReadOnlyDoubleWrapper();
    private final ReadOnlyObjectWrapper<@Nullable AppError> failure = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<@Nullable JobReport> report = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<@Nullable String> runFileName = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<@Nullable RunMode> runMode = new ReadOnlyObjectWrapper<>();
    private final ObservableList<LogEntry> logEntries = FXCollections.observableArrayList();
    private final ObservableList<LogEntry> readOnlyLog = FXCollections.unmodifiableObservableList(logEntries);
    private final LiveSection live = new LiveSection();
    private final ReviewSection review = new ReviewSection();

    /** Creates an idle, empty mirror. */
    public StateMirror() {
        // Every property starts at its default; a run's first publish fills them.
    }

    /**
     * The whole set of figures, which tells auto-accepted from repaired segments.
     *
     * @return the read-only figures; read on the FX thread
     */
    public ReadOnlyObjectProperty<RunFigures> figures() {
        return figures.getReadOnlyProperty();
    }

    /**
     * Where the current run is.
     *
     * @return the read-only run state; read on the FX thread
     */
    public ReadOnlyObjectProperty<RunState> runState() {
        return runState.getReadOnlyProperty();
    }

    /**
     * Segments accepted so far.
     *
     * @return the read-only count; read on the FX thread
     */
    public ReadOnlyIntegerProperty accepted() {
        return accepted.getReadOnlyProperty();
    }

    /**
     * Segments flagged so far.
     *
     * @return the read-only count; read on the FX thread
     */
    public ReadOnlyIntegerProperty flagged() {
        return flagged.getReadOnlyProperty();
    }

    /**
     * Segments not decided yet.
     *
     * @return the read-only count; read on the FX thread
     */
    public ReadOnlyIntegerProperty remaining() {
        return remaining.getReadOnlyProperty();
    }

    /**
     * Accepted plus flagged plus remaining.
     *
     * @return the read-only count; read on the FX thread
     */
    public ReadOnlyIntegerProperty total() {
        return total.getReadOnlyProperty();
    }

    /**
     * The 1-based body unit the run is translating.
     *
     * @return the read-only position, zero before the first; read on the FX thread
     */
    public ReadOnlyIntegerProperty section() {
        return section.getReadOnlyProperty();
    }

    /**
     * How many body units the book has.
     *
     * @return the read-only count, zero before a snapshot arrives; read on the FX thread
     */
    public ReadOnlyIntegerProperty sections() {
        return sections.getReadOnlyProperty();
    }

    /**
     * The 1-based chunk within the section being translated.
     *
     * @return the read-only position, zero before the first; read on the FX thread
     */
    public ReadOnlyIntegerProperty chunk() {
        return chunk.getReadOnlyProperty();
    }

    /**
     * How many chunks the section has.
     *
     * @return the read-only count, zero before a snapshot arrives; read on the FX thread
     */
    public ReadOnlyIntegerProperty chunks() {
        return chunks.getReadOnlyProperty();
    }

    /**
     * Decided segments over the total.
     *
     * @return the read-only proportion between 0.0 and 1.0; read on the FX thread
     */
    public ReadOnlyDoubleProperty progressFraction() {
        return progressFraction.getReadOnlyProperty();
    }

    /**
     * How long the request now outstanding has been waiting, once that is long enough for the banner to say so.
     *
     * @return a read-only whole number of seconds, or {@link #NOT_WAITING} when there is nothing to say; read on the FX
     *     thread
     */
    public ReadOnlyIntegerProperty waitingSeconds() {
        return waitingSeconds.getReadOnlyProperty();
    }

    /**
     * The error that ended the run, or absent for every other outcome including a stop.
     *
     * @return a read-only property holding {@code null} unless the run failed; read on the FX thread
     */
    public ReadOnlyObjectProperty<@Nullable AppError> failure() {
        return failure.getReadOnlyProperty();
    }

    /**
     * The terminal report, which the export screen reads.
     *
     * @return a read-only property holding {@code null} until a run returns one; read on the FX thread
     */
    public ReadOnlyObjectProperty<@Nullable JobReport> report() {
        return report.getReadOnlyProperty();
    }

    /**
     * The name of the book file the run started on.
     *
     * @return a read-only property holding {@code null} while no run exists; read on the FX thread
     */
    public ReadOnlyObjectProperty<@Nullable String> runFileName() {
        return runFileName.getReadOnlyProperty();
    }

    /**
     * The quality dial and review mode the run started with.
     *
     * @return a read-only property, {@code null} while no run exists or none was named; FX thread
     */
    public ReadOnlyObjectProperty<@Nullable RunMode> runMode() {
        return runMode.getReadOnlyProperty();
    }

    /**
     * The newest activity-log entries, oldest first.
     *
     * @return an unmodifiable list of at most {@link #MAX_LOG_ENTRIES} entries; read on the FX thread
     */
    public ObservableList<LogEntry> activityLog() {
        return readOnlyLog;
    }

    /**
     * The live rows, pace, kept-as-source count and flagged queue.
     *
     * @return the section, whose properties are read on the FX thread
     */
    public LiveSection live() {
        return live;
    }

    /**
     * Why a paused run is waiting: the model error it paused on or the segment a review pause names.
     *
     * @return the section, whose properties are read on the FX thread
     */
    public ReviewSection review() {
        return review;
    }

    /**
     * Resets every figure, the log, the failure, the report and the written file for a fresh run, names the book it
     * runs on, and shows it running.
     *
     * @param fileName the non-null name of the book file the run translates
     * @param mode the dial and review mode the title bar names, or {@code null} when the caller names none
     */
    public void publishRunStarted(final String fileName, final @Nullable RunMode mode) {
        Objects.requireNonNull(fileName, "fileName");
        log.debug("publishing run started on {} with mode {}", fileName, mode);
        Platform.runLater(() -> resetTo(fileName, mode, RunState.RUNNING));
    }

    /**
     * Forgets the run altogether: the state returns to idle, no file is named, and every figure, the log, the failure
     * and the report are reset, as at the start of the application.
     */
    public void publishRunCleared() {
        log.debug("publishing run cleared");
        Platform.runLater(this::resetToIdle);
    }

    /**
     * Shows a run state without touching the figures.
     *
     * @param state the state to show
     */
    public void publishRunState(final RunState state) {
        Objects.requireNonNull(state, "state");
        log.debug("publishing run state {}", state);
        Platform.runLater(() -> runState.set(state));
    }

    /**
     * Shows how long the model has been slow to answer, or withdraws the notice.
     *
     * <p>Not logged here: the run session logs when a wait is first shown and when it is cleared, and this is called
     * once a second while one lasts.
     *
     * @param seconds whole seconds the outstanding request has waited, or {@link #NOT_WAITING} to withdraw the notice
     */
    public void publishWaitingSeconds(final int seconds) {
        Platform.runLater(() -> waitingSeconds.set(seconds));
    }

    /**
     * Shows the figures derived from a snapshot.
     *
     * @param progress the engine's latest snapshot
     */
    public void publishProgress(final JobProgress progress) {
        Objects.requireNonNull(progress, "progress");
        final RunFigures figures = RunFigures.from(progress);
        Platform.runLater(() -> applyFigures(figures));
    }

    /**
     * Appends entries to the activity log, counting a line that repeats the one before it on that line, and dropping the
     * oldest beyond {@link #MAX_LOG_ENTRIES}.
     *
     * @param entries the entries in the order they happened
     */
    public void publishLogEntries(final List<LogEntry> entries) {
        final List<LogEntry> copy = List.copyOf(Objects.requireNonNull(entries, "entries"));
        Platform.runLater(() -> appendToLog(copy));
    }

    /**
     * Shows how a run ended.
     *
     * @param state the terminal state
     * @param report the terminal report, or {@code null} when the run returned none
     * @param error the error that ended the run, or {@code null} for an outcome that is not a failure
     */
    public void publishOutcome(final RunState state, final @Nullable JobReport report, final @Nullable AppError error) {
        publishOutcome(state, report, error, () -> {});
    }

    /**
     * Shows how a run ended, running {@code beforeApply} on the FX thread first.
     *
     * <p>The hook runs in the same FX task that shows the terminal state, so once an observer sees that state, the
     * runner is already accepting a new {@code start()}. Ordering a new run's reset after this outcome needs no help
     * from the hook: the FX queue is first-in, first-out.
     *
     * @param state the terminal state
     * @param report the terminal report, or {@code null} when the run returned none
     * @param error the error that ended the run, or {@code null} for an outcome that is not a failure
     * @param beforeApply runs on the FX thread before the properties change; must not throw
     */
    void publishOutcome(
            final RunState state,
            final @Nullable JobReport report,
            final @Nullable AppError error,
            final Runnable beforeApply) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(beforeApply, "beforeApply");
        log.debug("publishing outcome {}, report present {}, failure present {}", state, report != null, error != null);
        Platform.runLater(() -> {
            beforeApply.run();
            this.report.set(report);
            this.failure.set(error);
            this.runState.set(state);
        });
    }

    private void resetToIdle() {
        resetTo(null, null, RunState.IDLE);
    }

    private void resetTo(final @Nullable String fileName, final @Nullable RunMode mode, final RunState state) {
        applyFigures(RunFigures.EMPTY);
        runFileName.set(fileName);
        runMode.set(mode);
        logEntries.clear();
        failure.set(null);
        report.set(null);
        waitingSeconds.set(NOT_WAITING);
        live.reset();
        review.reset();
        runState.set(state);
    }

    private void applyFigures(final RunFigures figures) {
        this.figures.set(figures);
        accepted.set(figures.accepted());
        flagged.set(figures.flagged());
        remaining.set(figures.remaining());
        total.set(figures.total());
        section.set(figures.section());
        sections.set(figures.sections());
        chunk.set(figures.chunk());
        chunks.set(figures.chunks());
        progressFraction.set(figures.fraction());
    }

    // A line that says what the previous one said is counted on it instead of repeated, so a stalled call that fails
    // four times reads as one line with "×4".
    private void appendToLog(final List<LogEntry> entries) {
        final List<LogEntry> merged = new ArrayList<>();
        for (final LogEntry entry : entries) {
            final int last = merged.size() - 1;
            if (last >= 0 && merged.get(last).saysTheSameAs(entry)) {
                merged.set(last, merged.get(last).repeatedBy(entry));
            } else {
                merged.add(entry);
            }
        }
        final int shown = logEntries.size() - 1;
        if (shown >= 0 && !merged.isEmpty() && logEntries.get(shown).saysTheSameAs(merged.getFirst())) {
            logEntries.set(shown, logEntries.get(shown).repeatedBy(merged.removeFirst()));
        }
        if (merged.size() >= MAX_LOG_ENTRIES) {
            logEntries.setAll(merged.subList(merged.size() - MAX_LOG_ENTRIES, merged.size()));
            return;
        }
        logEntries.addAll(merged);
        final int excess = logEntries.size() - MAX_LOG_ENTRIES;
        if (excess > 0) {
            logEntries.remove(0, excess);
        }
    }
}
