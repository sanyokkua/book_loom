package ua.bookloom.pipeline.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.SegmentRecord;

/**
 * A project's stored decisions laid over its opened book, so the writer sees, per segment, the text the file is to
 * hold: the person's edit, else the machine's, else the source. A PENDING record, a FLAGGED record no draft ever passed
 * for, and a record kept as source by choice all keep their source.
 *
 * @param document the opened book with each written target set on its segment
 * @param maskedTargets each segment written with a target mapped to that target's masked form — the order its
 *     placeholders were written in, which the re-open check compares; a segment written in its source is absent
 * @param sourceFallbacks the segments written in their source although their record holds a target, because that
 *     target's placeholders no longer match the segment's, in book order
 */
@Slf4j
record EffectiveTargets(Document document, Map<String, String> maskedTargets, List<String> sourceFallbacks) {

    /** Copies the map and the list so the targets cannot change after construction. */
    EffectiveTargets {
        Objects.requireNonNull(document, "document");
        maskedTargets = Collections.unmodifiableMap(new LinkedHashMap<>(maskedTargets));
        sourceFallbacks = List.copyOf(sourceFallbacks);
    }

    /**
     * Decides each segment's written text.
     *
     * @param opened the non-null book as the project holds it open
     * @param records the non-null stored records of the project
     * @param keptKinds the auxiliary kinds the brief keeps as source when the export starts; never null
     * @param placeholdersMatch whether a stored masked target still passes the segment's placeholder gate; never null
     * @return the book to write, the masked form of every target written, and the targets written as source instead
     */
    static EffectiveTargets apply(
            final Document opened,
            final List<SegmentRecord> records,
            final Set<SegmentKind> keptKinds,
            final BiPredicate<Segment, String> placeholdersMatch) {
        Objects.requireNonNull(opened, "opened");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(keptKinds, "keptKinds");
        Objects.requireNonNull(placeholdersMatch, "placeholdersMatch");
        final Map<String, SegmentRecord> byId =
                records.stream().collect(Collectors.toMap(SegmentRecord::segmentId, Function.identity(), (a, b) -> a));
        log.debug(
                "Applying effective targets records={} document={} keptKinds={}",
                records.size(),
                opened.id(),
                keptKinds);
        final Map<String, String> masked = new LinkedHashMap<>();
        final List<String> fallbacks = new ArrayList<>();
        final Decision decision = new Decision(keptKinds, placeholdersMatch, masked, fallbacks);
        final List<Unit> units = opened.units().stream()
                .map(unit -> unit.withSegments(unit.segments().stream()
                        .map(segment -> decide(segment, byId.get(segment.id()), decision))
                        .toList()))
                .toList();
        log.debug(
                "Effective targets applied document={} writtenWithTarget={} sourceFallbacks={}",
                opened.id(),
                masked.size(),
                fallbacks);
        return new EffectiveTargets(opened.withUnits(units), masked, fallbacks);
    }

    /** What deciding one segment reads and fills. */
    private record Decision(
            Set<SegmentKind> keptKinds,
            BiPredicate<Segment, String> placeholdersMatch,
            Map<String, String> masked,
            List<String> fallbacks) {}

    private static Segment decide(
            final Segment segment, @Nullable final SegmentRecord record, final Decision decision) {
        if (record == null || record.isKeptAsSource(decision.keptKinds())) {
            log.trace("segment={} written as source: {}", segment.id(), record == null ? "no record" : "kept");
            return segment;
        }
        final String target =
                switch (record.status()) {
                    case ACCEPTED, REVISED, FLAGGED -> record.effectiveTarget().orElse(null);
                    case PENDING -> null;
                };
        if (target == null) {
            log.trace("segment={} status={} written as source: no target", segment.id(), record.status());
            return segment;
        }
        final String maskedTarget =
                record.userTarget() != null ? record.maskedUserTarget() : record.maskedMachineTarget();
        if (maskedTarget == null || !decision.placeholdersMatch().test(segment, maskedTarget)) {
            log.warn(
                    "segment={} status={} written as source: its target's placeholders do not match the segment's",
                    segment.id(),
                    record.status());
            decision.fallbacks().add(segment.id());
            return segment;
        }
        decision.masked().put(segment.id(), maskedTarget);
        return segment.withDecision(record.status(), target);
    }
}
