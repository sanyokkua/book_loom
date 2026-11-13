package ua.bookloom.pipeline.context;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;

/**
 * Reads the checked target of an earlier segment for a window's previous pairs (design D8). A model copies the
 * translation it is shown, so only text that was accepted, kept by a later stage or written by the person is offered;
 * a flagged, pending or still undecided neighbour gives its source alone.
 */
@FunctionalInterface
public interface AcceptedTargets {

    /**
     * The masked target a window may show for a segment.
     *
     * @param segmentId the non-null segment id
     * @return the target, or empty when the segment gives its source only; the store's error when it cannot be read
     */
    Result<Optional<String>> acceptedTarget(String segmentId);

    /**
     * The rule on one stored decision: an {@link SegmentStatus#ACCEPTED} or {@link SegmentStatus#REVISED} record (a
     * proofread rewrite that was kept is revised), or the person's own edit, shows its target as the person last left
     * it, else the machine's.
     *
     * @param record the non-null stored decision
     * @return the masked target, or empty when the record is not kept or has no target
     */
    static Optional<String> of(final SegmentRecord record) {
        Objects.requireNonNull(record, "record");
        final boolean kept = record.status() == SegmentStatus.ACCEPTED
                || record.status() == SegmentStatus.REVISED
                || record.path() == SegmentPath.USER;
        if (!kept) {
            return Optional.empty();
        }
        final @Nullable String target =
                record.userTarget() == null ? record.maskedMachineTarget() : record.maskedUserTarget();
        return target == null || target.isBlank() ? Optional.empty() : Optional.of(target);
    }
}
