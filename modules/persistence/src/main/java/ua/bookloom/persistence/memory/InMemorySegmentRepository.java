package ua.bookloom.persistence.memory;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.persistence.memory.InMemoryStore.ProjectSegments;

/**
 * Stores and reads a project's segment decisions over the shared {@link InMemoryStore}, applying
 * {@link SegmentRecord#isKeptAsSource(Set)} with whatever kept set a caller passes — never a set of its own — so
 * every screen and the job agree on what "kept as source" means for a given read.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class InMemorySegmentRepository implements SegmentRepository {

    private final InMemoryStore store;

    @Override
    public Result<Integer> saveAll(final String projectId, final List<SegmentRecord> segments) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segments, "segments");
        final Lock writeLock = store.lock().writeLock();
        writeLock.lock();
        try {
            store.segments(projectId).replaceAll(segments);
            log.debug("Segments saved projectId={} count={}", projectId, segments.size());
            return Result.ok(segments.size());
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public Result<Optional<SegmentRecord>> find(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        try {
            return Result.ok(
                    Optional.ofNullable(store.segments(projectId).byId().get(segmentId)));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    @Override
    public Result<Optional<SegmentRecord>> firstPending(
            final String projectId, final Set<SegmentKind> keptAuxiliaryKinds) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(keptAuxiliaryKinds, "keptAuxiliaryKinds");
        final Lock readLock = store.lock().readLock();
        readLock.lock();
        try {
            for (final SegmentRecord record : orderedRecords(projectId)) {
                if (record.status() == SegmentStatus.PENDING && !record.isKeptAsSource(keptAuxiliaryKinds)) {
                    return Result.ok(Optional.of(record));
                }
            }
            return Result.ok(Optional.empty());
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Result<SegmentCounts> countsByStatus(final String projectId, final Set<SegmentKind> keptAuxiliaryKinds) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(keptAuxiliaryKinds, "keptAuxiliaryKinds");
        final Lock readLock = store.lock().readLock();
        readLock.lock();
        try {
            int pending = 0;
            int accepted = 0;
            int revised = 0;
            int flagged = 0;
            int sourceKept = 0;
            for (final SegmentRecord record : orderedRecords(projectId)) {
                if (record.isKeptAsSource(keptAuxiliaryKinds)) {
                    sourceKept++;
                    continue;
                }
                switch (record.status()) {
                    case PENDING -> pending++;
                    case ACCEPTED -> accepted++;
                    case REVISED -> revised++;
                    case FLAGGED -> flagged++;
                }
            }
            return Result.ok(new SegmentCounts(pending, accepted, revised, flagged, sourceKept));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Result<List<SegmentRecord>> flagged(final String projectId, final Set<SegmentKind> keptAuxiliaryKinds) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(keptAuxiliaryKinds, "keptAuxiliaryKinds");
        final Lock readLock = store.lock().readLock();
        readLock.lock();
        try {
            final List<SegmentRecord> result = new ArrayList<>();
            for (final SegmentRecord record : orderedRecords(projectId)) {
                if (record.status() == SegmentStatus.FLAGGED && !record.isKeptAsSource(keptAuxiliaryKinds)) {
                    result.add(record);
                }
            }
            return Result.ok(List.copyOf(result));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Result<List<SegmentRecord>> byUnit(final String projectId, final String unitId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(unitId, "unitId");
        final Lock readLock = store.lock().readLock();
        readLock.lock();
        try {
            final List<SegmentRecord> result = new ArrayList<>();
            for (final SegmentRecord record : orderedRecords(projectId)) {
                if (record.unitId().equals(unitId)) {
                    result.add(record);
                }
            }
            return Result.ok(List.copyOf(result));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Result<List<SegmentRecord>> all(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        final Lock readLock = store.lock().readLock();
        readLock.lock();
        try {
            return Result.ok(List.copyOf(orderedRecords(projectId)));
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public Result<SegmentRecord> update(
            final String projectId, final String segmentId, final UnaryOperator<SegmentRecord> update) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(update, "update");
        try {
            final Map<String, SegmentRecord> byId = store.segments(projectId).byId();
            final AtomicReference<AppError> refused = new AtomicReference<>();
            final SegmentRecord updated = byId.compute(segmentId, (id, current) -> {
                if (current == null) {
                    refused.set(unknownSegment(projectId, id));
                    return null;
                }
                return update.apply(current);
            });
            final AppError error = refused.get();
            if (error != null) {
                log.debug(
                        "Segment update refused projectId={} segmentId={} code={}", projectId, segmentId, error.code());
                return Result.err(error);
            }
            log.debug("Segment updated projectId={} segmentId={} status={}", projectId, segmentId, updated.status());
            return Result.ok(updated);
        } catch (Throwable cause) {
            return Result.err(internalError(cause));
        }
    }

    /**
     * Returns this project's segment records in document order, missing ids (never expected in practice) skipped
     * rather than surfaced as a null element.
     */
    private List<SegmentRecord> orderedRecords(final String projectId) {
        final ProjectSegments segments = store.segments(projectId);
        final Map<String, SegmentRecord> byId = segments.byId();
        final List<SegmentRecord> result = new ArrayList<>(segments.orderedIds().size());
        for (final String id : segments.orderedIds()) {
            final SegmentRecord record = byId.get(id);
            if (record != null) {
                result.add(record);
            }
        }
        return result;
    }

    private static AppError unknownSegment(final String projectId, final String segmentId) {
        final AppError error = AppError.of(
                ErrorCode.validation,
                "Unknown segment",
                "No segment '" + segmentId + "' belongs to project '" + projectId + "'.");
        log.debug("Segment update refused as unknown projectId={} segmentId={}", projectId, segmentId);
        return error;
    }

    private static AppError internalError(final Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Segment storage failure", "The segment could not be stored.", null, cause);
        log.error("Unexpected segment repository failure code={}", error.code(), cause);
        return error;
    }
}
