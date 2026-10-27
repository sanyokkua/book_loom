package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
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
 * Stores a project's run started last over the shared {@link InMemoryStore}, by the SQLite adapter's rule: a saved
 * record replaces the held one when it is the same run or was started no earlier.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemoryRunRepository implements RunRepository {

    private final InMemoryStore store;

    @Override
    public Result<RunRecord> save(final RunRecord run) {
        Objects.requireNonNull(run, "run");
        try {
            final RunRecord held = store.keepRun(run);
            log.debug(
                    "Run saved projectId={} runId={} state={} kept={}",
                    run.projectId(),
                    run.runId(),
                    run.state(),
                    held.equals(run));
            return Result.ok(run);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<RunRecord>> latest(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            final Optional<RunRecord> latest = Optional.ofNullable(store.latestRun(projectId));
            log.debug("Run latest projectId={} found={}", projectId, latest.isPresent());
            return Result.ok(latest);
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
