package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.project.Project;

/**
 * Stores and retrieves a project over the shared {@link InMemoryStore}, keyed by its stable id.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemoryProjectRepository implements ProjectRepository {

    private final InMemoryStore store;

    @Override
    public Result<Project> save(final Project project) {
        Objects.requireNonNull(project, "project");
        try {
            store.projects().put(project.id(), project);
            log.debug("Project saved projectId={}", project.id());
            return Result.ok(project);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<Project>> find(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            return Result.ok(Optional.ofNullable(store.projects().get(projectId)));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Boolean> delete(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            final boolean removed = store.projects().remove(projectId) != null;
            log.debug("Project delete projectId={} removed={}", projectId, removed);
            return Result.ok(removed);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Project storage failure", "The project could not be stored.", null, cause);
        log.error("Unexpected project repository failure code={}", error.code(), cause);
        return error;
    }
}
