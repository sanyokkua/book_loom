package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.SegmentPreview;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Lists the source segments of the part of the book picked on the structure screen, with the chunk a run would pack
 * each into, on the background executor, and publishes the answer on the FX thread.
 *
 * <p>Each {@link #show} supersedes the one before it: an answer that arrives after a newer pick is dropped, so a slow
 * listing of a chapter the person has left cannot overwrite the one on show. The listing follows the brief, so the screen
 * asks again with {@link #refresh} when a brief save finishes. The state is touched on the FX thread only.
 */
@Slf4j
@Singleton
public final class StructureSegmentsViewModel {

    private final ProjectService projects;
    private final ExecutorService executor;
    private final ReadOnlyObjectWrapper<StructureSegments> state =
            new ReadOnlyObjectWrapper<>(new StructureSegments.Idle());
    private long generation;
    private @Nullable String shownProject;
    private List<String> shownUnits = List.of();
    private @Nullable BookBrief shownBrief;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param projects the port the listing is asked of
     * @param executor the daemon executor the listing runs on, never the FX thread
     */
    @Inject
    public StructureSegmentsViewModel(
            final ProjectService projects, @BackgroundExecutor final ExecutorService executor) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Where the listing stands.
     *
     * @return a read-only property; idle until the first {@link #show}
     */
    public ReadOnlyObjectProperty<StructureSegments> state() {
        return state.getReadOnlyProperty();
    }

    /**
     * Lists the segments of some units, replacing any earlier listing.
     *
     * @param projectId the stored project
     * @param unitIds the units to list in reading order; none leaves the list idle
     */
    public void show(final String projectId, final List<String> unitIds) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(unitIds, "unitIds");
        final long ticket = ++generation;
        shownProject = projectId;
        shownUnits = List.copyOf(unitIds);
        shownBrief = null;
        log.debug("listing the segments of project {} units {} (run {})", projectId, unitIds, ticket);
        if (unitIds.isEmpty()) {
            state.set(new StructureSegments.Idle());
            return;
        }
        state.set(new StructureSegments.Loading());
        final List<String> units = shownUnits;
        try {
            executor.execute(() -> {
                final StructureSegments found = load(projectId, units);
                Platform.runLater(() -> publish(ticket, found));
            });
        } catch (RuntimeException rejected) {
            log.warn("the segment listing of project {} could not be submitted", projectId, rejected);
            state.set(new StructureSegments.Failed(ErrorCode.internal));
        }
    }

    /**
     * Lists the segments of some units unless exactly these, under this brief, are on show or on their way. A screen
     * that is built again for the same book calls this, so that returning to it does not read the units again; a
     * listing made by any other call, or under another brief, is asked for again.
     *
     * @param projectId the stored project
     * @param unitIds the units to list in reading order; none leaves the list idle
     * @param brief the brief as it stands now, which decides the chunks, or {@code null} when none is known
     */
    public void showUnlessShown(final String projectId, final List<String> unitIds, final @Nullable BookBrief brief) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(unitIds, "unitIds");
        final StructureSegments now = state.get();
        final boolean answered = now instanceof StructureSegments.Loaded || now instanceof StructureSegments.Loading;
        if (answered
                && projectId.equals(shownProject)
                && unitIds.equals(shownUnits)
                && Objects.equals(brief, shownBrief)) {
            log.debug("the segments of project {} units {} are already on show", projectId, unitIds);
            return;
        }
        show(projectId, unitIds);
        shownBrief = brief;
    }

    /** Asks again for what is on show, as the brief it follows has changed. */
    public void refresh() {
        final String project = shownProject;
        if (project != null && !shownUnits.isEmpty()) {
            log.debug("the brief changed; listing the segments of project {} again", project);
            show(project, shownUnits);
        }
    }

    /** Forgets the part on show, as the screen is left. */
    public void clear() {
        generation++;
        shownProject = null;
        shownUnits = List.of();
        shownBrief = null;
        state.set(new StructureSegments.Idle());
    }

    private void publish(final long ticket, final StructureSegments found) {
        if (ticket != generation) {
            log.debug("the listing of run {} is dropped: run {} superseded it", ticket, generation);
            return;
        }
        state.set(found);
    }

    private StructureSegments load(final String projectId, final List<String> unitIds) {
        final List<StructureSegmentRow> rows = new ArrayList<>();
        int chunks = 0;
        try {
            for (final String unitId : unitIds) {
                final Result<List<SegmentPreview>> answer = projects.segments(projectId, unitId);
                final AppError failure = answer.error();
                if (failure != null) {
                    log.warn("the segments of unit {} failed with {}", unitId, failure.code());
                    return new StructureSegments.Failed(failure.code());
                }
                chunks += append(rows, Objects.requireNonNull(answer.data(), "data"));
            }
        } catch (Throwable thrown) {
            log.error("the project service threw instead of returning the segments", thrown);
            return new StructureSegments.Failed(ErrorCode.internal);
        }
        log.debug("listed {} segment(s) in {} planned chunk(s) of project {}", rows.size(), chunks, projectId);
        return new StructureSegments.Loaded(rows, chunks);
    }

    private static int append(final List<StructureSegmentRow> rows, final List<SegmentPreview> unit) {
        int previous = 0;
        int chunks = 0;
        for (final SegmentPreview preview : unit) {
            final boolean start = preview.chunkIndex() > 0 && preview.chunkIndex() != previous;
            rows.add(new StructureSegmentRow(preview, start));
            previous = preview.chunkIndex();
            chunks = Math.max(chunks, preview.chunkCount());
        }
        return chunks;
    }
}
