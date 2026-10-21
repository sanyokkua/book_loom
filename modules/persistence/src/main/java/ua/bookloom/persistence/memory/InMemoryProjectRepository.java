package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.api.project.Project;

/**
 * Stores and retrieves a project over the shared {@link InMemoryStore}, keyed by its stable id, with the rules the
 * SQLite adapter keeps by its schema: a name is unique within one book ignoring case, every project of one content
 * shares the one book location, and deleting a project forgets its work.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemoryProjectRepository implements ProjectRepository {

    private final InMemoryStore store;

    @Override
    public Result<Project> save(final Project project) {
        Objects.requireNonNull(project, "project");
        final Lock writeLock = store.lock().writeLock();
        writeLock.lock();
        try {
            if (nameInUse(project)) {
                log.debug(
                        "Project save refused, name in use projectId={} hash={}", project.id(), project.contentHash());
                return Result.err(nameInUseError());
            }
            store.projects().put(project.id(), project);
            store.projects()
                    .replaceAll((id, held) -> held.contentHash().equals(project.contentHash())
                            ? withSource(held, project.source())
                            : held);
            log.debug("Project saved projectId={} hash={}", project.id(), project.contentHash());
            return Result.ok(project);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        } finally {
            writeLock.unlock();
        }
    }

    /** Another project of the same book already holds this name, compared by {@link Project#nameKey()}. */
    private boolean nameInUse(final Project project) {
        return store.projects().values().stream()
                .anyMatch(held -> !held.id().equals(project.id())
                        && held.contentHash().equals(project.contentHash())
                        && held.nameKey().equals(project.nameKey()));
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
        final Lock writeLock = store.lock().writeLock();
        writeLock.lock();
        try {
            final boolean removed = store.projects().remove(projectId) != null;
            store.forget(projectId);
            log.debug("Project delete projectId={} removed={}", projectId, removed);
            return Result.ok(removed);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Result<List<Project>> all() {
        try {
            return Result.ok(List.copyOf(store.projects().values()));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<Project>> findByPair(
            final String contentHash, @Nullable final String sourceLanguage, @Nullable final String targetLanguage) {
        Objects.requireNonNull(contentHash, "contentHash");
        try {
            final Optional<Project> found = store.projects().values().stream()
                    .filter(p -> p.contentHash().equals(contentHash))
                    .filter(p -> Objects.equals(p.brief().sourceLanguage(), sourceLanguage)
                            && Objects.equals(p.brief().targetLanguage(), targetLanguage))
                    .findFirst();
            log.debug(
                    "Project findByPair hash={} source={} target={} found={}",
                    contentHash,
                    sourceLanguage,
                    targetLanguage,
                    found.isPresent());
            return Result.ok(found);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<Project>> findByNameKey(final String contentHash, final String nameKey) {
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(nameKey, "nameKey");
        try {
            final Optional<Project> found = store.projects().values().stream()
                    .filter(p ->
                            p.contentHash().equals(contentHash) && p.nameKey().equals(nameKey))
                    .findFirst();
            log.debug("Project findByNameKey hash={} nameKey={} found={}", contentHash, nameKey, found.isPresent());
            return Result.ok(found);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<List<Project>> relocateBook(final String contentHash, final Path newPath) {
        Objects.requireNonNull(contentHash, "contentHash");
        Objects.requireNonNull(newPath, "newPath");
        try {
            final List<Project> moved = store.projects().values().stream()
                    .filter(p -> p.contentHash().equals(contentHash))
                    .map(p -> withSource(p, newPath))
                    .toList();
            moved.forEach(p -> store.projects().put(p.id(), p));
            log.debug("Book relocated hash={} projects={} path={}", contentHash, moved.size(), newPath);
            return Result.ok(moved);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<ExportRecord> recordExport(final String projectId, final ExportRecord record) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(record, "record");
        try {
            store.exports().put(projectId, record);
            log.debug("Export recorded projectId={} written={}", projectId, record.written());
            return Result.ok(record);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<ExportRecord>> lastExport(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            final Optional<ExportRecord> found =
                    Optional.ofNullable(store.exports().get(projectId));
            log.debug("Last export projectId={} found={}", projectId, found.isPresent());
            return Result.ok(found);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    private static Project withSource(final Project p, final Path source) {
        return new Project(
                p.id(),
                source,
                p.format(),
                p.contentHash(),
                p.brief(),
                p.name(),
                p.nameOrigin(),
                p.createdAt(),
                p.lastOpenedAt());
    }

    private static AppError nameInUseError() {
        return AppError.of(ErrorCode.validation, "Name in use", "Another project of this book already has that name.");
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Project storage failure", "The project could not be stored.", null, cause);
        log.error("Unexpected project repository failure code={}", error.code(), cause);
        return error;
    }
}
