package ua.bookloom.pipeline.export;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 *     placeholders were written in, which the re-open check compares; a segment written in its source, or whose
 *     record holds no masked form, is absent
 */
@Slf4j
record EffectiveTargets(Document document, Map<String, String> maskedTargets) {

    /** Copies the map so the targets cannot change after construction. */
    EffectiveTargets {
        Objects.requireNonNull(document, "document");
        maskedTargets = Collections.unmodifiableMap(new LinkedHashMap<>(maskedTargets));
    }

    /**
     * Decides each segment's written text.
     *
     * @param opened the non-null book as the project holds it open
     * @param records the non-null stored records of the project
     * @param keptKinds the auxiliary kinds the brief keeps as source when the export starts; never null
     * @return the book to write and the masked form of every target written
     */
    static EffectiveTargets apply(
            final Document opened, final List<SegmentRecord> records, final Set<SegmentKind> keptKinds) {
        Objects.requireNonNull(opened, "opened");
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(keptKinds, "keptKinds");
        final Map<String, SegmentRecord> byId =
                records.stream().collect(Collectors.toMap(SegmentRecord::segmentId, Function.identity(), (a, b) -> a));
        log.debug(
                "Applying effective targets records={} document={} keptKinds={}",
                records.size(),
                opened.id(),
                keptKinds);
        final Map<String, String> masked = new LinkedHashMap<>();
        final List<Unit> units = opened.units().stream()
                .map(unit -> unit.withSegments(unit.segments().stream()
                        .map(segment -> decide(segment, byId.get(segment.id()), keptKinds, masked))
                        .toList()))
                .toList();
        log.debug("Effective targets applied document={} writtenWithTarget={}", opened.id(), masked.size());
        return new EffectiveTargets(opened.withUnits(units), masked);
    }

    private static Segment decide(
            final Segment segment,
            @Nullable final SegmentRecord record,
            final Set<SegmentKind> keptKinds,
            final Map<String, String> masked) {
        if (record == null || record.isKeptAsSource(keptKinds)) {
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
        if (maskedTarget != null) {
            masked.put(segment.id(), maskedTarget);
        }
        return segment.withDecision(record.status(), target);
    }
}
