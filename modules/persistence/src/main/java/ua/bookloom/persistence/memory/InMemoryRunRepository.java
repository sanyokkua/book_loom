package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.RunRepository;
import ua.bookloom.api.project.RunRecord;

/**
 * Stores a project's translation-run history over the shared {@link InMemoryStore}, upserted by run id so every
 * state change of one run lands in one record.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemoryRunRepository implements RunRepository {

    private final InMemoryStore store;

    @Override
    public Result<RunRecord> save(final RunRecord run) {
        Objects.requireNonNull(run, "run");
        try {
            store.runs(run.projectId()).put(run.runId(), run);
            log.debug("Run saved projectId={} runId={} state={}", run.projectId(), run.runId(), run.state());
            return Result.ok(run);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<RunRecord>> latest(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            return Result.ok(store.runs(projectId).values().stream().max(Comparator.comparing(RunRecord::startedAt)));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Run storage failure", "The run record could not be stored.", null, cause);
        log.error("Unexpected run repository failure code={}", error.code(), cause);
        return error;
    }
}
