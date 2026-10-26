package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.ViewNames;

/**
 * Which workflow steps the person has completed, so the navigation can mark them.
 *
 * <p>Most marks are derived from state that already exists: a book is open, a run has started, the run completed, or
 * the reopened project's stored segments were decided by an earlier run (all of them: translated). Two
 * steps (the brief and the export) have no state of their own, so their actions say so through
 * {@link #markDone}; those marks belong to the open project and are dropped when it changes or clears. The
 * set is touched on the FX Application Thread only.
 *
 * <p>The brief's mark also needs usable languages, so a mark never outlives the choice it vouched for; a started run
 * marks it, because a run could not have started otherwise. A stopped or failed run counts as translated when no segment
 * is left undecided.
 */
@Slf4j
@Singleton
public final class WorkflowProgress {

    private final CurrentProject project;
    private final StateMirror mirror;
    private final PendingCount stored;
    private final Set<ViewNames> marked = EnumSet.noneOf(ViewNames.class);
    private final ObservableSet<ViewNames> done = FXCollections.observableSet(EnumSet.noneOf(ViewNames.class));
    private final ObservableSet<ViewNames> readOnlyDone = FXCollections.unmodifiableObservableSet(done);

    /**
     * Starts following the project and the run.
     *
     * @param project the open book, whose presence marks the import and whose change drops the explicit marks
     * @param mirror the run's state and file name
     * @param desk where the open project's stored decisions are counted, so a reopened project keeps its marks
     * @param executor the daemon executor the count is read on, never the FX thread
     */
    @Inject
    WorkflowProgress(
            final CurrentProject project,
            final StateMirror mirror,
            final ReviewDesk desk,
            @BackgroundExecutor final ExecutorService executor) {
        this.project = Objects.requireNonNull(project, "project");
        this.mirror = Objects.requireNonNull(mirror, "mirror");
        this.stored = new PendingCount(desk, executor);
        project.book().addListener((observed, old, current) -> {
            log.debug("the open project changed, so the explicit workflow marks are dropped");
            marked.clear();
            readStored(current);
            refresh();
        });
        stored.count().addListener((observed, old, current) -> refresh());
        stored.total().addListener((observed, old, current) -> refresh());
        mirror.runFileName().addListener((observed, old, current) -> refresh());
        mirror.runState().addListener((observed, old, current) -> refresh());
        mirror.figures().addListener((observed, old, current) -> refresh());
        project.brief().addListener((observed, old, current) -> refresh());
        readStored(project.book().get());
        refresh();
    }

    /**
     * The completed steps.
     *
     * @return an unmodifiable live set, never null; empty while nothing is done
     */
    public ObservableSet<ViewNames> done() {
        return readOnlyDone;
    }

    /**
     * Records that a step whose completion is not derived from any state was finished. Does nothing while no book is
     * open, because a mark belongs to a project.
     *
     * @param step the step to mark
     */
    public void markDone(final ViewNames step) {
        Objects.requireNonNull(step, "step");
        if (project.book().get() == null) {
            log.debug("step {} is not marked done: no book is open", step);
            return;
        }
        marked.add(step);
        refresh();
    }

    private void refresh() {
        final Set<ViewNames> wanted = EnumSet.noneOf(ViewNames.class);
        if (project.book().get() != null) {
            wanted.add(ViewNames.IMPORT);
            wanted.addAll(marked);
        }
        if (!hasUsableLanguages()) {
            wanted.remove(ViewNames.BOOK_BRIEF);
        }
        if (mirror.runFileName().get() != null || hasStoredDecisions()) {
            wanted.add(ViewNames.BOOK_BRIEF);
            wanted.add(ViewNames.NAMES_STYLE);
        }
        if (isTranslated() || isStoredTranslated()) {
            wanted.add(ViewNames.TRANSLATING);
        }
        apply(wanted);
    }

    private void readStored(final @Nullable OpenedBook book) {
        if (book == null) {
            stored.clear();
        } else {
            stored.refresh(book.projectId());
        }
    }

    // A run of an earlier launch decided segments of the reopened project, so its brief and names were settled then.
    // Not logged: refresh runs on every progress tick; the read and each mark it changes are logged.
    private boolean hasStoredDecisions() {
        return project.book().get() != null
                && stored.count().get() < stored.total().get();
    }

    private boolean isStoredTranslated() {
        return project.book().get() != null
                && stored.total().get() > 0
                && stored.count().get() == 0;
    }

    private boolean hasUsableLanguages() {
        final BookBrief brief = project.brief().get();
        return brief != null && BriefLanguages.isUsable(brief.sourceLanguage(), brief.targetLanguage());
    }

    // A run that ended early still translated the book when nothing is left to decide; a stop half way did not.
    private boolean isTranslated() {
        final RunState state = mirror.runState().get();
        final boolean endedEarly = state == RunState.STOPPED || state == RunState.FAILED;
        final RunFigures figures = mirror.figures().get();
        final boolean nothingLeft = figures.total() > 0 && figures.remaining() == 0;
        return state == RunState.COMPLETED || (endedEarly && nothingLeft);
    }

    private void apply(final Set<ViewNames> wanted) {
        for (final ViewNames step : ViewNames.values()) {
            if (wanted.contains(step) && done.add(step)) {
                log.debug("workflow step {} is now done", step);
            } else if (!wanted.contains(step) && done.remove(step)) {
                log.debug("workflow step {} is no longer done", step);
            }
        }
    }
}
