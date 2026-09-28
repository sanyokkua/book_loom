package ua.bookloom.document.golden;

import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;

/** Test helper: a copy of a document in which the named segments, in any unit, carry an accepted target. */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AuxiliaryTargets {

    static Document with(Document document, Map<String, String> targetsBySegmentId) {
        return new Document(
                document.id(),
                document.format(),
                document.declaredLang(),
                document.detectedSourceLang(),
                document.charset(),
                document.hasBom(),
                document.contentHash(),
                document.metadata(),
                document.units().stream()
                        .map(unit -> retargeted(unit, targetsBySegmentId))
                        .toList());
    }

    private static Unit retargeted(Unit unit, Map<String, String> targetsBySegmentId) {
        return unit.withSegments(unit.segments().stream()
                .map(segment -> retargeted(segment, targetsBySegmentId))
                .toList());
    }

    private static Segment retargeted(Segment segment, Map<String, String> targetsBySegmentId) {
        final String target = targetsBySegmentId.get(segment.id());
        return target == null ? segment : segment.withDecision(SegmentStatus.ACCEPTED, target);
    }
}
