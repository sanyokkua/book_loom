package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.BiFunction;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
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
 * What the review desk does: list the segments of a filter through the review desk port, hold the selected segment's
 * editor text, and accept, save, undo, retry, leave for later or apply a proposal to it.
 *
 * <p>A singleton: the count, the list and the marks of "later" outlive the dialog. Desk calls run on the background
 * executor, properties change on the FX thread. Actions are off while the run translates or a retry is in flight; a run
 * started in between makes the desk answer {@code busy} (one warning). The to-review count is read from the desk, not
 * the mirror's queue (cleared when a run starts), which only cues a re-read. A refused accept or save is shown in place
 * through {@link #problem()}, and the segment keeps its status.
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
    private final ReviewActs acts;
    private final ReviewReads reads;
    private final ReviewListing list = new ReviewListing();
    private final ReadOnlyObjectWrapper<ReviewFilter> filter = list.filter();
    private final ReadOnlyObjectWrapper<@Nullable SegmentView> selected = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<ReviewMainAction> mainAction =
            new ReadOnlyObjectWrapper<>(new ReviewMainAction(MessageKey.REVIEW_MAIN_ACCEPT, false));
    private final ReviewEditor editor = new ReviewEditor(this::refreshAvailability);
    private final ReadOnlyStringWrapper problem = new ReadOnlyStringWrapper();
    private final ReviewAvailability availability;
    private final FlaggedCount counts;
    private final ListChangeListener<FlaggedRow> onMirrorQueue = change -> refreshCount();

    /**
     * @param desk the review desk every read and action goes through
     * @param mirror the run's state, whose run state gates the actions and whose flagged queue prompts a recount
     * @param current the open book, whose project the desk is asked about
     * @param reviewMode the mode a run keeps or the next would use; All segments no longer depends on it, so it is
     *     only checked present, keeping one constructor for the injector and the tests
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
            final Provider<ReviewMode> reviewMode,
            final Toasts toasts,
            final ErrorPresenter errors,
            final ReviewRetry retry,
            @BackgroundExecutor final ExecutorService executor) {
        this.desk = Objects.requireNonNull(desk, "desk");
        Objects.requireNonNull(reviewMode, "reviewMode");
        this.queries = new ReviewQueries(desk);
        Objects.requireNonNull(executor, "executor");
        this.reads = new ReviewReads(queries, executor);
        this.counts = new FlaggedCount(queries, executor);
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.current = Objects.requireNonNull(current, "current");
        this.availability = new ReviewAvailability(mirror, this::refreshAvailability);
        this.refusals = new ReviewRefusals(
                Objects.requireNonNull(toasts, "toasts"), Objects.requireNonNull(errors, "errors"), problem::set);
        this.retry = Objects.requireNonNull(retry, "retry");
        this.acts = new ReviewActs(queries, mirror, refusals, executor);
        mirror.runState().addListener((observed, was, now) -> {
            refreshAvailability();
            counts.afterRun(now, this::refreshCount);
        });
        mirror.live().flaggedQueue().addListener(onMirrorQueue);
        refreshAvailability();
        log.debug("review view model ready");
    }

    /** The rows of the current list, in document order; unmodifiable. FX thread only. */
    public ObservableList<ReviewRow> rows() {
        return list.rows();
    }

    /** The filter that is chosen, {@link ReviewFilter#ALL_FLAGGED} at first. FX thread only. */
    public ReadOnlyObjectProperty<ReviewFilter> filter() {
        return filter.getReadOnlyProperty();
    }

    /** How many segments the desk says are flagged, zero until read. FX thread only. */
    public ReadOnlyIntegerProperty flaggedCount() {
        return counts.flagged();
    }

    /** The "y" of "x of y left": the to-review count when the desk last opened, raised if more arrive. FX only. */
    public ReadOnlyIntegerProperty deskTotal() {
        return counts.total();
    }

    /** The segment being reviewed as the desk last described it, {@code null} until one is selected. FX only. */
    public ReadOnlyObjectProperty<@Nullable SegmentView> selected() {
        return selected.getReadOnlyProperty();
    }

    /** The target text in masked form, {@code ⟦gN⟧} tokens visible; the desk binds it both ways. FX thread only. */
    public StringProperty editorText() {
        return editor.text();
    }

    /** The editor's changed state, its save state, its no-translation note and a refused save's tokens. */
    public ReviewEditor editor() {
        return editor;
    }

    /** What the desk's one main button says and whether it can be pressed. FX thread only. */
    public ReadOnlyObjectProperty<ReviewMainAction> mainAction() {
        return mainAction.getReadOnlyProperty();
    }

    /** Why the selected segment can only be read, or {@code null} while its actions are offered. FX thread only. */
    public ReadOnlyObjectProperty<@Nullable MessageKey> lockReason() {
        return availability.lockReason();
    }

    /** Whether a run translates now, so the desk is read-only and offers Pause. FX thread only. */
    public ReadOnlyBooleanProperty translating() {
        return availability.translating();
    }

    /** The reason the desk refused the last accept or save, worded by it; {@code null} when none. FX thread only. */
    public ReadOnlyStringProperty problem() {
        return problem.getReadOnlyProperty();
    }

    /** Whether the actions on the selected segment are offered: one is selected and no run translates. FX only. */
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

    /** Whether a retry of a segment is running; the desk is read-only meanwhile. FX thread only. */
    public ReadOnlyBooleanProperty retryInFlight() {
        return mirror.review().retryInFlight();
    }

    /** Reads the flagged count again for the open book; call when the Translating screen is shown. FX thread only. */
    public void refreshCount() {
        final OpenedBook book = current.book().get();
        if (book != null) {
            counts.read(book.projectId(), counts.flagged().get(), this::publishCount);
        }
    }

    /** Reads the list and the count again for the open book, keeping the selection and any typing. FX thread only. */
    public void open() {
        final OpenedBook book = current.book().get();
        if (book == null) {
            log.debug("review list read with no book open: nothing to list");
            list.clear();
            return;
        }
        log.debug("review list read again on project {} with filter {}", book.projectId(), filter.get());
        final String projectId = book.projectId();
        final ReviewFilter chosen = filter.get();
        reads.list(projectId, chosen, list.marks(projectId), list::publish);
        refreshCount();
    }

    /**
     * Opens the desk on a filter and a segment: reads the list, selects the named segment (or the first row) and
     * starts the "x of y" afresh. A named segment the filter does not list is looked for under All segments.
     *
     * @param chosen the filter to show
     * @param segmentId the segment to select, or {@code null} for the first row
     */
    public void openOn(final ReviewFilter chosen, final @Nullable String segmentId) {
        Objects.requireNonNull(chosen, "chosen");
        log.debug("desk opens on filter {} at segment {}", chosen, segmentId);
        filter.set(chosen);
        counts.restartTotal();
        load(chosen, segmentId, true);
        refreshCount();
    }

    /** Shows another list, keeping the selected segment when the list holds it, else selecting its first row. */
    public void selectFilter(final ReviewFilter chosen) {
        Objects.requireNonNull(chosen, "chosen");
        log.debug("filter changed from {} to {}", filter.get(), chosen);
        filter.set(chosen);
        final SegmentView shown = selected.get();
        load(chosen, shown == null ? null : shown.segmentId(), false);
    }

    /** Opens a listed segment in the editor. FX thread only. */
    public void select(final String segmentId) {
        Objects.requireNonNull(segmentId, "segmentId");
        final OpenedBook book = current.book().get();
        if (book == null) {
            log.debug("segment {} not selected: no book is open", segmentId);
            return;
        }
        log.debug("selecting segment {} of project {}", segmentId, book.projectId());
        problem.set(null);
        reads.segment(book.projectId(), segmentId, this::showKeepingTyping);
    }

    /** Selects the row {@code delta} places (negative: up) from the selected one, stopping at either end. */
    public void move(final int delta) {
        final SegmentView shown = selected.get();
        final String to = list.moved(shown == null ? null : shown.segmentId(), delta);
        if (to != null) {
            select(to);
        }
    }

    /** Marks the selected segment "later" for this application run and moves on; the desk is not called. */
    public void leaveForLater() {
        final SegmentView segment = selected.get();
        final OpenedBook book = current.book().get();
        if (segment == null || book == null) {
            log.debug("leave for later not offered: segment {}", segment);
            return;
        }
        log.info("review leave-for-later segment={}", segment.segmentId());
        final String next = list.leaveForLater(book.projectId(), segment.segmentId());
        if (next != null) {
            select(next);
        }
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
        final String text = editor.storedText();
        if (editor.savable().get()) {
            act("saveEdit", (projectId, segmentId) -> desk.saveEdit(projectId, segmentId, text));
        } else {
            log.debug("saveEdit not offered: the editor holds no change");
        }
    }

    /** Restores the machine target, if actions are offered. FX thread only. */
    public void revert() {
        act("revert", desk::revert);
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
        if (segment == null || current.book().get() == null || retryInFlight().get()) {
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

    private boolean act(final String name, final BiFunction<String, String, Result<SegmentRecord>> call) {
        final SegmentView segment = selected.get();
        final OpenedBook book = current.book().get();
        final boolean offered = availability.actions().get();
        if (segment == null || book == null || !offered) {
            log.debug("{} not offered: segment {}, actions {}", name, segment, offered);
            return false;
        }
        problem.set(null);
        final ReviewActs.Request request = new ReviewActs.Request(
                name,
                book.projectId(),
                segment,
                list.order(),
                filter.get(),
                counts.flagged().get(),
                list.marks(book.projectId()));
        return acts.act(request, call, this::done);
    }

    private void done(final ReviewActs.Done done) {
        publishCount(done.remaining());
        list.publish(done.listed(), done.rows());
        show(done.next());
        final SegmentView segment = done.request().segment();
        mirror.review().publishDecided(done.request().name(), segment.segmentId());
        refusals.confirm(done.request().name(), segment.locator(), done.remaining());
    }

    private void load(final ReviewFilter chosen, final @Nullable String segmentId, final boolean widen) {
        final OpenedBook book = current.book().get();
        if (book == null) {
            log.debug("filter {} not listed: no book is open", chosen);
            list.clear();
            show(null);
            return;
        }
        final String projectId = book.projectId();
        reads.opened(projectId, chosen, segmentId, widen, list.marks(projectId), (opened, built) -> {
            filter.set(opened.filter());
            list.publish(opened.views(), built);
            showKeepingTyping(opened.segment());
        });
    }

    // Re-reading the segment the person is typing into keeps the typing: a list refresh must not undo an edit.
    private void showKeepingTyping(final @Nullable SegmentView view) {
        if (view != null && editor.holdsTypingFor(view)) {
            log.debug("segment {} re-read while edited; the typing is kept", view.segmentId());
            selected.set(view);
        } else {
            show(view);
        }
    }

    private void publishCount(final int flagged) {
        counts.publish(mirror, flagged);
    }

    private void show(final @Nullable SegmentView view) {
        if (view == null) {
            log.debug("no segment left to show: the desk shows its empty state");
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
        mainAction.set(ReviewMainAction.of(
                editor.changed().getValue(),
                editor.marks().getValue().isComplete(),
                availability.actions().get(),
                availability.accept().get()));
    }
}
