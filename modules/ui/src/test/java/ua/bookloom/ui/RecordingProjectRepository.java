package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.ProjectRepository;
import ua.bookloom.api.project.ExportRecord;
import ua.bookloom.api.project.Project;

/**
 * A hand-written {@link ProjectRepository} for the window's tests: it keeps each project's last {@link ExportRecord}
 * as the real store does, remembers every one it was asked to record, and holds no projects, since the window reads
 * those through the project service.
 */
public final class RecordingProjectRepository implements ProjectRepository {

    private final Map<String, ExportRecord> last = new ConcurrentHashMap<>();
    private final List<ExportRecord> recorded = new CopyOnWriteArrayList<>();
    private volatile @Nullable AppError recordFailure;

    /** Makes every later {@code recordExport} fail with {@code error}; {@code null} makes it succeed again. */
    public void failRecordingWith(@Nullable final AppError error) {
        recordFailure = error;
    }

    /** Every record the window asked to store, in order, including ones stored before the test began. */
    public List<ExportRecord> recorded() {
        return List.copyOf(recorded);
    }

    @Override
    public Result<ExportRecord> recordExport(final String projectId, final ExportRecord record) {
        final AppError failure = recordFailure;
        if (failure != null) {
            return Result.err(failure);
        }
        recorded.add(Objects.requireNonNull(record, "record"));
        last.put(Objects.requireNonNull(projectId, "projectId"), record);
        return Result.ok(record);
    }

    @Override
    public Result<Optional<ExportRecord>> lastExport(final String projectId) {
        return Result.ok(Optional.ofNullable(last.get(projectId)));
    }

    @Override
    public Result<Project> save(final Project project) {
        return Result.ok(project);
    }

    @Override
    public Result<Optional<Project>> find(final String projectId) {
        return Result.ok(Optional.empty());
    }

    @Override
    public Result<Boolean> delete(final String projectId) {
        return Result.ok(false);
    }

    @Override
    public Result<List<Project>> all() {
        return Result.ok(List.of());
    }

    @Override
    public Result<Optional<Project>> findByPair(
            final String contentHash, @Nullable final String sourceLanguage, @Nullable final String targetLanguage) {
        return Result.ok(Optional.empty());
    }

    @Override
    public Result<Optional<Project>> findByNameKey(final String contentHash, final String nameKey) {
        return Result.ok(Optional.empty());
    }

    @Override
    public Result<List<Project>> relocateBook(final String contentHash, final Path newPath) {
        return Result.ok(List.of());
    }
}
