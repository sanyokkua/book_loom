package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.pipeline.ProjectService;
import ua.bookloom.api.pipeline.ProjectSummary;

/**
 * What the import does with a project it lets go of: releasing the book that was open, and removing a project that
 * must not stay. Both run on the background executor and report a failure by log only, because the screen has already
 * moved on and a failed release says nothing about the book now shown.
 */
@Slf4j
final class ProjectDisposal {

    private final ProjectService projects;
    private final ExecutorService executor;

    ProjectDisposal(final ProjectService projects, final ExecutorService executor) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /** Closes the project of a book that is no longer open; nothing to do for {@code null}. */
    void release(final @Nullable OpenedBook previous) {
        if (previous == null) {
            log.debug("no previous book to release");
            return;
        }
        try {
            executor.execute(() -> closeGuarded(previous.projectId()));
            log.debug("releasing project {} submitted", previous.projectId());
        } catch (RuntimeException rejected) {
            log.warn("releasing the previous project could not be submitted", rejected);
        }
    }

    /** The ids stored now, taken before an import runs; {@code null} when they cannot be listed, so nothing is then removed later. Background thread. */
    @Nullable
    Set<String> storedIds() {
        try {
            final List<ProjectSummary> all = projects.list().data();
            return all == null
                    ? null
                    : all.stream().map(summary -> summary.project().id()).collect(Collectors.toSet());
        } catch (Throwable thrown) {
            // Even an Error: the import that follows must still answer, or no later book could be opened.
            log.warn("the stored projects could not be listed before an import", thrown);
            return null;
        }
    }

    /** Removes a stored project on the background executor. */
    void removeLater(final String projectId) {
        try {
            executor.execute(() -> remove(projectId));
        } catch (RuntimeException rejected) {
            log.warn("removing project {} could not be submitted", projectId, rejected);
        }
    }

    /**
     * Removes the project a replacing import replaces, once the import stored the new book; a book that was not stored
     * keeps it, and an import that reset the same project keeps it too. Background thread.
     *
     * @param replacedId the project the person chose to replace
     * @param createdId the project the import stored, or {@code null} when it stored none
     */
    void removeReplaced(final String replacedId, final @Nullable String createdId) {
        if (createdId == null) {
            log.warn("the new book was not stored: project {} is kept", replacedId);
        } else if (createdId.equals(replacedId)) {
            log.debug("the import reused project {}: nothing is removed", replacedId);
        } else {
            log.debug("project {} was created: project {} is removed", createdId, replacedId);
            remove(replacedId);
        }
    }

    /** Removes a stored project on the calling thread, which must not be the FX thread. */
    void remove(final String projectId) {
        try {
            final AppError failure = projects.remove(projectId).error();
            if (failure != null) {
                log.warn("removing project {} failed with {}", projectId, failure.code());
            } else {
                log.info("project {} removed by an import", projectId);
            }
        } catch (Throwable thrown) {
            log.error("the project service threw while removing project {}", projectId, thrown);
        }
    }

    private void closeGuarded(final String projectId) {
        log.debug(
                "releasing project {} on {}", projectId, Thread.currentThread().getName());
        try {
            final Result<Boolean> released = projects.close(projectId);
            final AppError failure = released.error();
            if (failure != null) {
                log.warn("releasing project {} failed with {}", projectId, failure.code());
            } else {
                log.info("released project {}", projectId);
                log.debug("released project {}; it was open: {}", projectId, released.data());
            }
        } catch (Throwable thrown) {
            log.error("the project service threw while releasing project {}", projectId, thrown);
        }
    }
}
