package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;

/** The written book carries no stress mark of a translated segment, and a source fallback keeps the source's own. */
class ExportStressTest {

    private static final String MARKED_SOURCE = "ма́ма";

    @Test
    void export_translatedSegment_writesNoStressMark() {
        final Map<String, String> written = written("uk", false);

        assertThat(written.get("s1")).isEqualTo("Вона прийшла додому.");
    }

    @Test
    void export_languageWithoutTheStripKey_keepsTheMark() {
        final Map<String, String> written = written("bg", false);

        assertThat(written.get("s1")).isEqualTo("Вона́ прийшла́ додо́му.");
    }

    @Test
    void export_fallbackSegment_keepsSourceMark() {
        final Map<String, String> written = written("uk", true);

        assertThat(written.get("s1")).isEqualTo(MARKED_SOURCE);
    }

    private static Map<String, String> written(final String target, final boolean isFallback) {
        final Segment segment = segment();
        final SegmentRecord record = record(segment);
        final Document opened = new Document(
                "d1",
                BookFormat.TXT,
                "uk",
                null,
                null,
                null,
                "hash",
                Map.of(),
                List.of(new Unit("c1", 1, "c1.txt", "text/plain", new SkeletonHandle("c1"), List.of(segment))));
        final EffectiveTargets targets = EffectiveTargets.apply(
                opened,
                List.of(record),
                Set.of(),
                (seg, masked) -> isFallback
                        ? Result.err(AppError.of(ErrorCode.validation, "t", "m", null, null))
                        : Result.ok(masked),
                "uk",
                target);
        final Map<String, String> written = new LinkedHashMap<>();
        targets.document().units().stream()
                .flatMap(unit -> unit.segments().stream())
                .forEach(seg ->
                        written.put(seg.id(), seg.targetInner() == null ? seg.sourceInner() : seg.targetInner()));
        return written;
    }

    private static Segment segment() {
        return new Segment(
                "s1",
                "c1",
                1,
                SegmentKind.PARAGRAPH,
                MARKED_SOURCE,
                MARKED_SOURCE,
                Map.of(),
                "hs1",
                null,
                null,
                new ByteSpanAnchor(0, MARKED_SOURCE.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    private static SegmentRecord record(final Segment segment) {
        final String target = "Вона́ прийшла́ додо́му.";
        return new SegmentRecord(
                "p1",
                segment.id(),
                segment.unit(),
                1,
                segment.kind(),
                SegmentStatus.ACCEPTED,
                target,
                target,
                null,
                null,
                0.9,
                null,
                List.of(),
                SegmentPath.DRAFT,
                0,
                false,
                null);
    }
}
