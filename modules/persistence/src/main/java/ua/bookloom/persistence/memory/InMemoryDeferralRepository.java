package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.DeferralRepository;
import ua.bookloom.api.project.Deferral;

/**
 * Stores a project's open deferrals over the shared {@link InMemoryStore}, idempotent on project, segment and
 * reason.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemoryDeferralRepository implements DeferralRepository {

    private final InMemoryStore store;

    @Override
    public Result<Deferral> add(final Deferral deferral) {
        Objects.requireNonNull(deferral, "deferral");
        try {
            final Map<String, Deferral> table = store.deferrals(deferral.projectId());
            final Deferral existing =
                    table.putIfAbsent(InMemoryStore.deferralKey(deferral.segmentId(), deferral.reason()), deferral);
            final Deferral result = existing != null ? existing : deferral;
            log.debug(
                    "Deferral add projectId={} segmentId={} reason={} added={}",
                    deferral.projectId(),
                    deferral.segmentId(),
                    deferral.reason(),
                    existing == null);
            return Result.ok(result);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<List<Deferral>> open(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        try {
            return Result.ok(List.copyOf(store.deferrals(projectId).values()));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Boolean> resolve(final String projectId, final String deferralId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(deferralId, "deferralId");
        try {
            final Map<String, Deferral> table = store.deferrals(projectId);
            final String matchingKey = table.entrySet().stream()
                    .filter(e -> e.getValue().id().equals(deferralId))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            final boolean resolved = matchingKey != null && table.remove(matchingKey) != null;
            log.debug("Deferral resolve projectId={} deferralId={} resolved={}", projectId, deferralId, resolved);
            return Result.ok(resolved);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Deferral storage failure", "The deferral could not be stored.", null, cause);
        log.error("Unexpected deferral repository failure code={}", error.code(), cause);
        return error;
    }
}
