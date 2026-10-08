package ua.bookloom.pipeline.review;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.persistence.SegmentRepository;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.pipeline.DisplayText;

/**
 * The next paragraph's target a retry shows after the preceding ones its first draft saw: the run's batch showed the
 * model what came next, and by the time of a retry the next paragraph usually has a target.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FollowingTarget {

    /**
     * The target of the paragraph after {@code record} in its unit.
     *
     * @param segments the store the unit is read from
     * @param record the segment being retried
     * @return the next paragraph's target as plain text if it has one, or empty if not; or the storage error
     */
    static Result<Optional<String>> of(final SegmentRepository segments, final SegmentRecord record) {
        final Result<List<SegmentRecord>> unit = segments.byUnit(record.projectId(), record.unitId());
        if (unit.isErr()) {
            return Result.err(Objects.requireNonNull(unit.error()));
        }
        final Optional<String> next = Objects.requireNonNull(unit.data()).stream()
                .filter(other -> other.ord() > record.ord())
                .min(Comparator.comparingInt(SegmentRecord::ord))
                .map(FollowingTarget::effectiveTarget);
        log.debug("retry: segment={} followingTarget={}", record.segmentId(), next.isPresent());
        return Result.ok(next);
    }

    private static @Nullable String effectiveTarget(final SegmentRecord record) {
        final String masked = record.userTarget() == null ? record.maskedMachineTarget() : record.maskedUserTarget();
        return masked == null || DisplayText.of(masked).isEmpty() ? null : DisplayText.of(masked);
    }
}
