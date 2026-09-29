package ua.bookloom.pipeline.export;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.SegmentRecord;

/**
 * Lays a project's stored decisions over its opened book, so the writer sees, per segment, the text the file is to
 * hold: the person's edit, else the machine's, else the source.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Slf4j
final class DecidedBook {

    static Document apply(
            final Document opened, final List<SegmentRecord> records, final Set<SegmentKind> keptAuxiliaryKinds) {
        final Map<String, SegmentRecord> byId =
                records.stream().collect(Collectors.toMap(SegmentRecord::segmentId, Function.identity(), (a, b) -> a));
        log.debug("Applying {} stored records to document {}", records.size(), opened.id());
        final List<Unit> units = opened.units().stream()
                .map(unit -> unit.withSegments(unit.segments().stream()
                        .map(segment -> decide(segment, byId.get(segment.id()), keptAuxiliaryKinds))
                        .toList()))
                .toList();
        return opened.withUnits(units);
    }

    private static Segment decide(
            final Segment segment, @Nullable final SegmentRecord record, final Set<SegmentKind> keptAuxiliaryKinds) {
        if (record == null || record.isKeptAsSource(keptAuxiliaryKinds)) {
            return segment;
        }
        return switch (record.status()) {
            case ACCEPTED, REVISED ->
                withTarget(segment, record.status(), record.effectiveTarget().orElse(null));
            case FLAGGED -> withTarget(segment, SegmentStatus.FLAGGED, record.machineTarget());
            case PENDING -> segment;
        };
    }

    private static Segment withTarget(
            final Segment segment, final SegmentStatus status, @Nullable final String target) {
        return target == null ? segment : segment.withDecision(status, target);
    }
}
