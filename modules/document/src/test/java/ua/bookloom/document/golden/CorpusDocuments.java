package ua.bookloom.document.golden;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.Unit;

/**
 * Flattening a {@link Document} into the shapes the corpus verification's probes compare against one another —
 * every segment regardless of unit, an ordered identity tuple per segment, and source text keyed by segment id
 * (design.md D6, task 11.1).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CorpusDocuments {

    static List<Segment> segmentsOf(Document document) {
        final List<Segment> segments = new ArrayList<>();
        for (final Unit unit : document.units()) {
            segments.addAll(unit.segments());
        }
        return segments;
    }

    /** The {@code (unit order, segment id, kind, anchor)} tuple design.md D6 compares P0/P3/P4 by, in order. */
    static List<String> tuplesOf(Document document) {
        final List<String> tuples = new ArrayList<>();
        for (final Unit unit : document.units()) {
            for (final Segment segment : unit.segments()) {
                tuples.add(unit.order() + "|" + segment.id() + "|" + segment.kind() + "|" + segment.anchor());
            }
        }
        return tuples;
    }

    /**
     * The tuples without the anchor: a translation that changes text length legitimately moves every following
     * {@code ByteSpanAnchor} in a TXT or Markdown book, so the mutation probe compares identity and kind only —
     * the zero-edit idempotence probe keeps {@link #tuplesOf}, where the anchors must not move.
     */
    static List<String> tuplesWithoutAnchorOf(Document document) {
        final List<String> tuples = new ArrayList<>();
        for (final Unit unit : document.units()) {
            for (final Segment segment : unit.segments()) {
                tuples.add(unit.order() + "|" + segment.id() + "|" + segment.kind());
            }
        }
        return tuples;
    }

    static Map<String, String> sourceById(Document document) {
        final Map<String, String> byId = new LinkedHashMap<>();
        for (final Segment segment : segmentsOf(document)) {
            byId.put(segment.id(), segment.sourceInner());
        }
        return byId;
    }
}
