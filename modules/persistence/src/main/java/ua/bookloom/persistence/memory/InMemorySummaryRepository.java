package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SummaryRepository;
import ua.bookloom.api.project.RollingSummary;

/**
 * Stores a project's rolling summary over the shared {@link InMemoryStore} — one record per project, each
 * {@link #save} replacing whatever was there.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemorySummaryRepository implements SummaryRepository {

    private final InMemoryStore store;

    @Override
    public Result<RollingSummary> save(final RollingSummary summary) {
        Objects.requireNonNull(summary, "summary");
        try {
            store.summaries().put(summary.projectId(), summary);
            log.debug("Summary saved projectId={} version={}", summary.projectId(), summary.version());
            return Result.ok(summary);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<RollingSummary>> latest(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            return Result.ok(Optional.ofNullable(store.summaries().get(projectId)));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Summary storage failure", "The rolling summary could not be stored.", null, cause);
        log.error("Unexpected summary repository failure code={}", error.code(), cause);
        return error;
    }
}
