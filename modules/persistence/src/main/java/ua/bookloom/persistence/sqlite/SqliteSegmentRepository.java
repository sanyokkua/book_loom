package ua.bookloom.persistence.sqlite;

import com.google.inject.Inject;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.statement.PreparedBatch;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;

/**
 * Stores a project's segment decisions in {@code segments}, one JSON document per segment; {@code ord} keeps the
 * order {@link #saveAll} was given, and every read lists in that order. The status and unit are columns so the common
 * reads are indexed; whether a record is kept as source is decided by {@link SegmentRecord#isKeptAsSource(Set)} with
 * the caller's set, as the in-memory adapter does.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class SqliteSegmentRepository implements SegmentRepository {

    private static final String INSERT = """
            INSERT INTO segments (project_id, segment_id, ord, unit_id, status, json)
            VALUES (:project, :segment, :ord, :unit, :status, :json)
            """;
    private static final String UPDATE = """
            UPDATE segments SET unit_id = :unit, status = :status, json = :json
            WHERE project_id = :project AND segment_id = :segment
            """;
    private static final String SELECT = "SELECT json FROM segments WHERE project_id = :project ";
    private static final String BY_ORDER = " ORDER BY ord";
    private static final String STATUS_COUNTS = """
            SELECT status, count(*) AS n FROM segments WHERE project_id = :project AND unit_id <> :aux
            GROUP BY status
            """;

    private final SqliteDatabase db;
    private final JsonCodec codec;

    @Override
    public Result<Integer> saveAll(final String projectId, final List<SegmentRecord> segments) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segments, "segments");
        log.debug("Segments saveAll projectId={} count={}", projectId, segments.size());
        return db.write("segment saveAll", h -> {
            h.createUpdate("DELETE FROM segments WHERE project_id = :project")
                    .bind("project", projectId)
                    .execute();
            if (!segments.isEmpty()) {
                final PreparedBatch batch = h.prepareBatch(INSERT);
                for (int ord = 0; ord < segments.size(); ord++) {
                    bindRow(batch, projectId, segments.get(ord))
                            .bind("ord", ord)
                            .add();
                }
                batch.execute();
            }
            return segments.size();
        });
    }

    @Override
    public Result<Optional<SegmentRecord>> find(final String projectId, final String segmentId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        log.debug("Segment find projectId={} segmentId={}", projectId, segmentId);
        return db.read("segment find", h -> findIn(h, projectId, segmentId));
    }

    @Override
    public Result<Optional<SegmentRecord>> firstPending(
            final String projectId, final Set<SegmentKind> keptAuxiliaryKinds) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(keptAuxiliaryKinds, "keptAuxiliaryKinds");
        return db.read("segment firstPending", h -> {
            final Optional<SegmentRecord> first = byStatus(h, projectId, SegmentStatus.PENDING).stream()
                    .filter(record -> !record.isKeptAsSource(keptAuxiliaryKinds))
                    .findFirst();
            log.debug("Segment firstPending projectId={} found={}", projectId, first.isPresent());
            return first;
        });
    }

    @Override
    public Result<SegmentCounts> countsByStatus(final String projectId, final Set<SegmentKind> keptAuxiliaryKinds) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(keptAuxiliaryKinds, "keptAuxiliaryKinds");
        return db.read("segment countsByStatus", h -> {
            final Map<SegmentStatus, Integer> counts = new EnumMap<>(SegmentStatus.class);
            h.createQuery(STATUS_COUNTS)
                    .bind("project", projectId)
                    .bind("aux", Unit.AUXILIARY_ID)
                    .map((rs, ctx) -> Map.entry(SegmentStatus.valueOf(rs.getString("status")), rs.getInt("n")))
                    .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
            int sourceKept = 0;
            for (final SegmentRecord record : query(h, projectId, "AND unit_id = :unit", "unit", Unit.AUXILIARY_ID)) {
                if (record.isKeptAsSource(keptAuxiliaryKinds)) {
                    sourceKept++;
                } else {
                    counts.merge(record.status(), 1, Integer::sum);
                }
            }
            final SegmentCounts result = toCounts(counts, sourceKept);
            log.debug("Segment countsByStatus projectId={} counts={}", projectId, result);
            return result;
        });
    }

    @Override
    public Result<List<SegmentRecord>> flagged(final String projectId, final Set<SegmentKind> keptAuxiliaryKinds) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(keptAuxiliaryKinds, "keptAuxiliaryKinds");
        return db.read("segment flagged", h -> {
            final List<SegmentRecord> flagged = byStatus(h, projectId, SegmentStatus.FLAGGED).stream()
                    .filter(record -> !record.isKeptAsSource(keptAuxiliaryKinds))
                    .toList();
            log.debug("Segment flagged projectId={} count={}", projectId, flagged.size());
            return flagged;
        });
    }

    @Override
    public Result<List<SegmentRecord>> byUnit(final String projectId, final String unitId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(unitId, "unitId");
        log.debug("Segment byUnit projectId={} unitId={}", projectId, unitId);
        return db.read("segment byUnit", h -> query(h, projectId, "AND unit_id = :unit", "unit", unitId));
    }

    @Override
    public Result<List<SegmentRecord>> all(final String projectId) {
        Objects.requireNonNull(projectId, "projectId");
        log.debug("Segment all projectId={}", projectId);
        return db.read("segment all", h -> query(h, projectId, "", "project", projectId));
    }

    @Override
    public Result<SegmentRecord> update(
            final String projectId, final String segmentId, final UnaryOperator<SegmentRecord> update) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(update, "update");
        return db.write("segment update", h -> updateIn(h, projectId, segmentId, update))
                .flatMap(Function.identity());
    }

    private Result<SegmentRecord> updateIn(
            final Handle h, final String projectId, final String segmentId, final UnaryOperator<SegmentRecord> update) {
        final Optional<SegmentRecord> held = findIn(h, projectId, segmentId);
        if (held.isEmpty()) {
            log.debug("Segment update refused as unknown projectId={} segmentId={}", projectId, segmentId);
            return Result.err(unknownSegment(projectId, segmentId));
        }
        final SegmentRecord updated = update.apply(held.get());
        writeRow(h, codec, projectId, segmentId, updated);
        log.debug("Segment updated projectId={} segmentId={} status={}", projectId, segmentId, updated.status());
        return Result.ok(updated);
    }

    /** Rewrites one existing segment row, keeping its order; the checkpoint shares it. */
    static void writeRow(
            final Handle h,
            final JsonCodec codec,
            final String projectId,
            final String segmentId,
            final SegmentRecord record) {
        h.createUpdate(UPDATE)
                .bind("project", projectId)
                .bind("segment", segmentId)
                .bind("unit", record.unitId())
                .bind("status", record.status().name())
                .bind("json", codec.write(record))
                .execute();
    }

    static AppError unknownSegment(final String projectId, final String segmentId) {
        return AppError.of(
                ErrorCode.validation,
                "Unknown segment",
                "No segment '" + segmentId + "' belongs to project '" + projectId + "'.");
    }

    private PreparedBatch bindRow(final PreparedBatch batch, final String projectId, final SegmentRecord record) {
        return batch.bind("project", projectId)
                .bind("segment", record.segmentId())
                .bind("unit", record.unitId())
                .bind("status", record.status().name())
                .bind("json", codec.write(record));
    }

    private Optional<SegmentRecord> findIn(final Handle h, final String projectId, final String segmentId) {
        return h.createQuery(SELECT + "AND segment_id = :segment")
                .bind("project", projectId)
                .bind("segment", segmentId)
                .mapTo(String.class)
                .findOne()
                .map(json -> codec.read(json, SegmentRecord.class));
    }

    private List<SegmentRecord> byStatus(final Handle h, final String projectId, final SegmentStatus status) {
        return query(h, projectId, "AND status = :status", "status", status.name());
    }

    private List<SegmentRecord> query(
            final Handle h, final String projectId, final String filter, final String name, final String value) {
        return List.copyOf(h.createQuery(SELECT + filter + BY_ORDER)
                .bind("project", projectId)
                .bind(name, value)
                .mapTo(String.class)
                .map(json -> codec.read(json, SegmentRecord.class))
                .list());
    }

    private static SegmentCounts toCounts(final Map<SegmentStatus, Integer> counts, final int sourceKept) {
        return new SegmentCounts(
                counts.getOrDefault(SegmentStatus.PENDING, 0),
                counts.getOrDefault(SegmentStatus.ACCEPTED, 0),
                counts.getOrDefault(SegmentStatus.REVISED, 0),
                counts.getOrDefault(SegmentStatus.FLAGGED, 0),
                sourceKept);
    }
}
