package ua.bookloom.api.persistence;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.project.SegmentCounts;
import ua.bookloom.api.project.SegmentRecord;

/**
 * Stores and reads a project's segment decisions — the source of truth the review desk, export, the chunker and
 * resume all read, so a caller's idea of "kept as source" is a single set of kinds applied consistently
 * ({@link SegmentRecord#isKeptAsSource(Set)}).
 */
public interface SegmentRepository {

    /**
     * Saves a project's segment records, document order taken from the order given.
     *
     * @param projectId the non-null project id
     * @param segments the non-null segment records, in document order
     * @return the number of records saved
     */
    Result<Integer> saveAll(String projectId, List<SegmentRecord> segments);

    /**
     * Finds one segment by id.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @return the segment if found, or empty when no segment holds that id
     */
    Result<Optional<SegmentRecord>> find(String projectId, String segmentId);

    /**
     * Finds the first {@code PENDING} segment in document order that is not kept as source.
     *
     * @param projectId the non-null project id
     * @param keptAuxiliaryKinds the non-null auxiliary kinds the brief keeps as source
     * @return the first eligible pending segment, or empty when none remain
     */
    Result<Optional<SegmentRecord>> firstPending(String projectId, Set<SegmentKind> keptAuxiliaryKinds);

    /**
     * Counts a project's segments by status, a record kept as source counted only as {@code sourceKept}.
     *
     * @param projectId the non-null project id
     * @param keptAuxiliaryKinds the non-null auxiliary kinds the brief keeps as source
     * @return the per-status counts, every record counted exactly once
     */
    Result<SegmentCounts> countsByStatus(String projectId, Set<SegmentKind> keptAuxiliaryKinds);

    /**
     * Lists a project's {@code FLAGGED} segments in document order, kept-as-source records left out.
     *
     * @param projectId the non-null project id
     * @param keptAuxiliaryKinds the non-null auxiliary kinds the brief keeps as source
     * @return the flagged segments in document order; never null, empty when none
     */
    Result<List<SegmentRecord>> flagged(String projectId, Set<SegmentKind> keptAuxiliaryKinds);

    /**
     * Lists a unit's segments in document order.
     *
     * @param projectId the non-null project id
     * @param unitId the non-null unit id
     * @return the unit's segments in document order; never null, empty when the unit has none
     */
    Result<List<SegmentRecord>> byUnit(String projectId, String unitId);

    /**
     * Lists every segment of a project in document order.
     *
     * @param projectId the non-null project id
     * @return all segments in document order; never null, empty when the project has none
     */
    Result<List<SegmentRecord>> all(String projectId);

    /**
     * Applies an update to one segment record atomically.
     *
     * @param projectId the non-null project id
     * @param segmentId the non-null segment id
     * @param update the non-null transformation applied to the current record
     * @return the updated record, or {@code validation} when {@code segmentId} does not belong to the project
     */
    Result<SegmentRecord> update(String projectId, String segmentId, UnaryOperator<SegmentRecord> update);
}
