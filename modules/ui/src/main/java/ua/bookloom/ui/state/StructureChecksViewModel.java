package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.BookPlan;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.RoundTripReport;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * Runs the two checks the structure screen shows beside the tree, the round trip and the chunk-budget plan, on the
 * background executor, and publishes what they found on the FX thread.
 *
 * <p>Each call to {@link #run} supersedes the one before it: an answer that arrives after a newer run was asked for is
 * dropped, so a slow check of a book the person has since left cannot overwrite the check of the book on show. Neither
 * check is ever a reason to block the screen, so a failure of either only shows in the state. The property is touched on
 * the FX thread only.
 */
@Slf4j
@Singleton
public final class StructureChecksViewModel {

    private final ProjectService projects;
    private final ExecutorService executor;
    private final ReadOnlyObjectWrapper<StructureChecks> state =
            new ReadOnlyObjectWrapper<>(new StructureChecks.Running());
    private long generation;

    /**
     * Receives the collaborators the injector owns.
     *
     * @param projects the port the two checks are asked of
     * @param executor the daemon executor the checks run on, never the FX thread
     */
    @Inject
    public StructureChecksViewModel(final ProjectService projects, @BackgroundExecutor final ExecutorService executor) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Where the checks stand.
     *
     * @return a read-only property; running from a {@link #run} until its answer is published
     */
    public ReadOnlyObjectProperty<StructureChecks> state() {
        return state.getReadOnlyProperty();
    }

    /**
     * Starts the checks of a project, replacing any earlier run.
     *
     * @param projectId the stored project to check
     */
    public void run(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        final long ticket = ++generation;
        log.debug("running the structure checks of project {} (run {})", projectId, ticket);
        state.set(new StructureChecks.Running());
        try {
            executor.execute(() -> {
                final StructureChecks.Finished found = check(projectId);
                Platform.runLater(() -> publish(ticket, found));
            });
        } catch (RuntimeException rejected) {
            log.warn("the structure checks of project {} could not be submitted", projectId, rejected);
            state.set(new StructureChecks.Finished(null, 0));
        }
    }

    private void publish(final long ticket, final StructureChecks.Finished found) {
        if (ticket != generation) {
            log.debug("the answer of run {} is dropped: run {} superseded it", ticket, generation);
            return;
        }
        log.debug("publishing the structure checks of run {}", ticket);
        state.set(found);
    }

    private StructureChecks.Finished check(final String projectId) {
        final RoundTripReport report = attempt("round trip", projectId, () -> projects.roundTrip(projectId));
        final BookPlan plan = attempt("plan", projectId, () -> projects.plan(projectId));
        if (report != null) {
            log.info(
                    "round trip of project {}: structure preserved {}, ids preserved {}, {} missing id(s), {} of {} segments",
                    projectId,
                    report.structurePreserved(),
                    report.idsPreserved(),
                    report.missingIds().size(),
                    report.copySegments(),
                    report.sourceSegments());
        }
        final int oversized = plan == null ? 0 : plan.oversizedSegmentIds().size();
        log.debug("plan of project {}: {} oversized segment(s)", projectId, oversized);
        return new StructureChecks.Finished(report, oversized);
    }

    private static <T> @Nullable T attempt(final String what, final String projectId, final Supplier<Result<T>> call) {
        try {
            final Result<T> answer = call.get();
            final AppError failure = answer.error();
            if (failure != null) {
                log.warn("the {} of project {} failed with {}", what, projectId, failure.code());
                return null;
            }
            return answer.data();
        } catch (Throwable thrown) {
            log.error("the project service threw instead of returning a result for the {}", what, thrown);
            return null;
        }
    }
}
