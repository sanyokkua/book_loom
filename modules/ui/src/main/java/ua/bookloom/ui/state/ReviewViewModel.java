package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiFunction;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.ReviewFilter;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.notify.ErrorPresenter;
import ua.bookloom.ui.notify.Toasts;

/**
 * What the review panel does: list the flagged segments through the review desk, hold the selected segment's editor
 * text, and accept, save, revert, skip, retry or apply a proposal to it.
 *
 * <p>A singleton because the screen is rebuilt on every visit while the count and the open list must outlive it. Every
 * desk call runs on the background executor and every property changes on the FX Application Thread. Every action is
 * unavailable while the run translates or a retry is in flight, and the desk answers {@code busy} if a run starts in
 * between; that answer is one warning, never a dialog.
 *
 * <p>The flagged count is read from the desk, not from the mirror's queue, because the mirror clears its queue when a
 * run session starts; the mirror's queue changing is only the cue to read it again. A refused accept or save is shown
 * in place through {@link #problem()}, and the segment keeps its status.
 */
@Slf4j
@Singleton
public final class ReviewViewModel {

    private final ReviewDesk desk;
    private final ReviewQueries queries;
    private final StateMirror mirror;
    private final CurrentProject current;
    private final ReviewRefusals refusals;
    private final ReviewRetry retry;
    private final ExecutorService executor;
    private final ObservableList<ReviewRow> rows = FXCollections.observableArrayList();
    private final ObservableList<ReviewRow> readOnlyRows = FXCollections.unmodifiableObservableList(rows);
    private final ReadOnlyObjectWrapper<ReviewFilter> filter = new ReadOnlyObjectWrapper<>(ReviewFilter.ALL_FLAGGED);
    private final ReadOnlyIntegerWrapper flaggedCount = new ReadOnlyIntegerWrapper();
    private final ReadOnlyObjectWrapper<@Nullable SegmentView> selected = new ReadOnlyObjectWrapper<>();
    private final ReviewEditor editor = new ReviewEditor(this::refreshAvailability);
    private final ReadOnlyStringWrapper problem = new ReadOnlyStringWrapper();
    private final ReviewAvailability availability;
    private final FlaggedCount counts;
    private final ListChangeListener<FlaggedRow> onMirrorQueue = change -> refreshCount();

    /**
     * Starts observing the run and the mirror's flagged queue.
     *
     * @param desk the review desk every read and action goes through
     * @param mirror the run's state, whose run state gates the actions and whose flagged queue prompts a recount
     * @param current the open book, whose project the desk is asked about
     * @param reviewMode the mode of this launch, which decides whether All segments is offered
     * @param toasts where an accept, a save and the busy refusal are announced
     * @param errors where a failure that is neither busy nor validation is shown
     * @param retry the model, the desk call and the hold on the run for a retry
     * @param executor the daemon executor desk calls run on, never the FX thread
     */
    @Inject
    public ReviewViewModel(
            final ReviewDesk desk,
            final StateMirror mirror,
            final CurrentProject current,
            final ReviewMode reviewMode,
            final Toasts toasts,
            final ErrorPresenter errors,
            final ReviewRetry retry,
            @BackgroundExecutor final ExecutorService executor) {
        this.desk = Objects.requireNonNull(desk, "desk");
        this.queries = new ReviewQueries(desk);
        this.counts = new FlaggedCount(queries, Objects.requireNonNull(executor, "executor"));
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.current = Objects.requireNonNull(current, "current");
        this.availability = new ReviewAvailability(mirror, Objects.requireNonNull(reviewMode, "reviewMode"));
        this.refusals = new ReviewRefusals(
                Objects.requireNonNull(toasts, "toasts"),
                Objects.requireNonNull(errors, "errors"),
                problem::set,
                editor::saveRefused);
        this.retry = Objects.requireNonNull(retry, "retry");
        this.executor = Objects.requireNonNull(executor, "executor");
        mirror.runState().addListener((observed, was, now) -> {
            refreshAvailability();
            counts.afterRun(now, this::refreshCount);
        });
        mirror.review().retryInFlight().addListener((observed, was, now) -> refreshAvailability());
        mirror.live().flaggedQueue().addListener(onMirrorQueue);
        refreshAvailability();
        log.debug("review view model ready");
    }

    /** The rows of the current list, in document order; unmodifiable. FX thread only. */
    public ObservableList<ReviewRow> rows() {
        return readOnlyRows;
    }

    /** The chip that is chosen, {@link ReviewFilter#ALL_FLAGGED} at first. FX thread only. */
    public ReadOnlyObjectProperty<ReviewFilter> filter() {
        return filter.getReadOnlyProperty();
    }

    /**
     * Whether the All segments browse is offered: only after an Unattended run has completed.
     *
     * @return a read-only property; FX thread only
     */
    public ReadOnlyBooleanProperty allSegmentsOffered() {
        return availability.allSegments();
    }

    /** How many segments the desk says are flagged, zero until read. FX thread only. */
    public ReadOnlyIntegerProperty flaggedCount() {
        return flaggedCount.getReadOnlyProperty();
    }

    /** The segment being reviewed as the desk last described it, {@code null} until one is selected. FX only. */
    public ReadOnlyObjectProperty<@Nullable SegmentView> selected() {
        return selected.getReadOnlyProperty();
    }

    /**
     * The target text in masked form, with the book's {@code ⟦gN⟧} tokens visible. The panel binds it both ways.
     *
     * @return the writable editor text; FX thread only
     */
    public StringProperty editorText() {
        return editor.text();
    }

    /** The editor's changed state and hint, its save state, its no-translation note and a refused save's tokens. */
    public ReviewEditor editor() {
        return editor;
    }

    /** Why the selected segment can only be read, or {@code null} while its actions are offered. FX thread only. */
    public ReadOnlyObjectProperty<@Nullable MessageKey> lockReason() {
        return availability.lockReason();
    }

    /** The reason the desk refused the last accept or save, worded by it; {@code null} when none. FX thread only. */
    public ReadOnlyStringProperty problem() {
        return problem.getReadOnlyProperty();
    }

    /** Whether Save edit, Revert and Skip are offered: a segment is selected and no run translates. FX only. */
    public ReadOnlyBooleanProperty actionsAvailable() {
        return availability.actions();
    }

    /** Why Accept is off for a segment that kept no translation, or {@code null}. FX thread only. */
    public ReadOnlyObjectProperty<@Nullable MessageKey> acceptNote() {
        return availability.acceptNote();
    }

    /** Whether Accept is offered: actions are, the editor is clean, the segment is flagged or accepted, translated. */
    public ReadOnlyBooleanProperty acceptAvailable() {
        return availability.accept();
    }

    /** Reads the flagged count again for the open book; call when the Translating screen is shown. FX thread only. */
    public void refreshCount() {
        final OpenedBook book = current.book().get();
        if (book != null) {
            counts.read(book.projectId(), flaggedCount.get(), this::publishCount);
        }
    }

    /** Reads the list and the count again for the open book; call when the panel opens. FX thread only. */
    public void open() {
        final OpenedBook book = current.book().get();
        if (book == null) {
            log.debug("review panel opened with no book open: nothing to list");
            rows.clear();
            return;
        }
        log.debug("review panel opened on project {} with filter {}", book.projectId(), filter.get());
        loadRows(book.projectId(), filter.get());
        refreshCount();
    }

    /**
     * Shows another list. All segments is refused unless it is offered.
     *
     * @param chosen the non-null chip's filter
     */
    public void selectFilter(final ReviewFilter chosen) {
        Objects.requireNonNull(chosen, "chosen");
        if (chosen == ReviewFilter.ALL_SEGMENTS && !availability.allSegments().get()) {
            log.debug("filter {} refused: All segments is not offered", chosen);
            return;
        }
        log.debug("filter changed from {} to {}", filter.get(), chosen);
        filter.set(chosen);
        final OpenedBook book = current.book().get();
        if (book != null) {
            loadRows(book.projectId(), chosen);
        }
    }

    /**
     * Opens a segment in the editor.
     *
     * @param segmentId the non-null id of a listed segment
     */
    public void select(final String segmentId) {
        Objects.requireNonNull(segmentId, "segmentId");
        final OpenedBook book = current.book().get();
        if (book == null) {
            log.debug("segment {} not selected: no book is open", segmentId);
            return;
        }
        log.debug("selecting segment {} of project {}", segmentId, book.projectId());
        problem.set(null);
        executor.execute(() -> readSegment(book.projectId(), segmentId));
    }

    /** Accepts the selected segment, if Accept is offered. FX thread only. */
    public void accept() {
        if (availability.accept().get()) {
            act("accept", desk::accept);
        } else {
            log.debug("accept not offered");
        }
    }

    /** Saves the editor's text as the person's edit, if there is one to save and actions are offered. FX thread only. */
    public void saveEdit() {
        final String text = editor.text().get();
        if (editor.savable().get()) {
            act("saveEdit", (projectId, segmentId) -> desk.saveEdit(projectId, segmentId, text));
        } else {
            log.debug("saveEdit not offered: the editor holds no change");
        }
    }

    public ReadOnlyBooleanProperty retryInFlight() {
        return mirror.review().retryInFlight();
    }

    /** Restores the machine target, if actions are offered. FX thread only. */
    public void revert() {
        act("revert", desk::revert);
    }

    /** Skips the selected segment and selects the flagged one the desk moves on to, if actions are offered. */
    public void skip() {
        act("skip", desk::skip);
    }

    /**
     * Retries the selected segment off the FX thread; a translating run makes it one busy warning, and no model
     * chosen a message in place. FX thread only.
     *
     * @param note the person's guidance for the model, or null for none
     * @param lowerTemperature whether the retry samples at a lower temperature
     */
    public void retry(final @Nullable String note, final boolean lowerTemperature) {
        final SegmentView segment = selected.get();
        if (segment == null
                || current.book().get() == null
                || mirror.review().retryInFlight().get()) {
            log.debug("retry not offered: segment {}", segment);
        } else if (availability.isTranslating()) {
            refusals.refuseBusy("retry", segment.segmentId());
        } else {
            final ReviewRetry.Pending pending = retry.begin(segment.segmentId(), note, lowerTemperature, problem::set);
            if (pending != null && !act("retry", pending.call())) {
                pending.abandon().run();
            }
        }
    }

    /** Applies the selected segment's backward-revision proposal as the person's edit. FX thread only. */
    public void acceptProposal() {
        act("acceptProposal", desk::acceptProposal);
    }

    // Answers whether the call was dispatched, so a caller holding something for it can let go when it was not.
    private boolean act(final String name, final BiFunction<String, String, Result<SegmentRecord>> call) {
        final SegmentView segment = selected.get();
        final OpenedBook book = current.book().get();
        final boolean offered = availability.actions().get();
        if (segment == null || book == null || !offered) {
            log.debug("{} not offered: segment {}, actions {}", name, segment, offered);
            return false;
        }
        problem.set(null);
        final String projectId = book.projectId();
        final List<String> order = rows.stream().map(ReviewRow::segmentId).toList();
        try {
            executor.execute(
                    () -> perform(name, projectId, segment, order, call.apply(projectId, segment.segmentId())));
            return true;
        } catch (RejectedExecutionException rejected) {
            refusals.notStarted(name, segment.segmentId(), rejected);
            return false;
        }
    }

    // After a decision the panel moves on (ReviewNext): it never keeps showing a segment that left the list.
    private void perform(
            final String name,
            final String projectId,
            final SegmentView segment,
            final List<String> order,
            final Result<SegmentRecord> result) {
        final SegmentRecord stored = result.data();
        if (stored == null) {
            final AppError error = Objects.requireNonNull(result.error(), "error");
            Platform.runLater(() -> refusals.show(name, segment.segmentId(), error));
            return;
        }
        log.info("review {} of segment {} left it {}", name, segment.segmentId(), stored.status());
        final int remaining = queries.refreshed(projectId, flaggedCount.get(), mirror.live());
        final List<SegmentView> listed = Objects.requireNonNullElse(queries.list(projectId, filter.get()), List.of());
        final String nextId = ReviewNext.after(name, segment.segmentId(), stored.segmentId(), order, listed);
        final SegmentView next = nextId == null ? null : queries.segment(projectId, nextId);
        Platform.runLater(() -> {
            publishCount(remaining);
            publishRows(listed);
            show(next);
            mirror.review().publishDecided(name, segment.segmentId());
            refusals.confirm(name, segment.locator(), remaining);
        });
    }

    private void loadRows(final String projectId, final ReviewFilter chosen) {
        executor.execute(() -> {
            final List<SegmentView> views = queries.list(projectId, chosen);
            if (views != null) {
                Platform.runLater(() -> publishRows(views));
            }
        });
    }

    // Re-reading the segment the person is typing into keeps the typing: a list refresh must not undo an edit.
    private void readSegment(final String projectId, final String segmentId) {
        final SegmentView view = queries.segment(projectId, segmentId);
        if (view != null) {
            Platform.runLater(() -> {
                if (editor.holdsTypingFor(view)) {
                    log.debug("segment {} re-read while edited; the typing is kept", segmentId);
                    selected.set(view);
                } else {
                    show(view);
                }
            });
        }
    }

    private void publishCount(final int flagged) {
        flaggedCount.set(flagged);
        counts.show(mirror, flagged);
    }

    private void publishRows(final List<SegmentView> views) {
        log.debug("listing {} segments", views.size());
        rows.setAll(views.stream().map(ReviewRow::of).toList());
    }

    private void show(final @Nullable SegmentView view) {
        if (view == null) {
            log.debug("no segment left to show: the panel shows its empty state");
            selected.set(null);
            refreshAvailability();
            return;
        }
        log.debug("showing segment {} with status {}", view.segmentId(), view.status());
        selected.set(view);
        editor.load(view);
    }

    private void refreshAvailability() {
        availability.refresh(selected.get(), editor.dirty().get());
    }
}
