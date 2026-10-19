package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
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

/** A heading that is the author and the quoted title takes both decided renderings, never a second title (15h.E1). */
class TitleAuthorHeadingTest {

    private static final String BRIEF_TITLE = "Бурштиновий Приплив";
    private static final String MODEL_TITLE = "Янтарна Хвиля";
    private static final String AUTHOR = "Мара Восс";

    // IF the heading were left to its own translation, THEN the book carries the title two ways (the Oct 9 «Горючий
    // хром»).
    @Test
    void apply_headingOfTheAuthorAndTheQuotedTitle_isComposedFromTheDecidedTitleAndAuthor() {
        final List<Segment> auxiliary = new ArrayList<>();
        final List<Segment> body = new ArrayList<>();
        final List<SegmentRecord> records = new ArrayList<>();
        add(auxiliary, records, "aux:title", Unit.AUXILIARY_ID, SegmentKind.METADATA_TITLE, "Amber Tide", BRIEF_TITLE);
        add(auxiliary, records, "aux:author", Unit.AUXILIARY_ID, SegmentKind.METADATA_AUTHOR, "Mara Voss", AUTHOR);
        add(
                body,
                records,
                "c1:1",
                "c1",
                SegmentKind.HEADING,
                "Mara Voss, “Amber Tide”",
                AUTHOR + ", «" + MODEL_TITLE + "»");

        final Map<String, String> written = writtenTargets(auxiliary, body, records);

        assertThat(written.get("c1:1")).contains(BRIEF_TITLE, AUTHOR).doesNotContain(MODEL_TITLE);
    }

    private static Map<String, String> writtenTargets(
            final List<Segment> auxiliary, final List<Segment> body, final List<SegmentRecord> records) {
        final Document opened = new Document(
                "d1",
                BookFormat.TXT,
                "en",
                null,
                null,
                null,
                "hash",
                Map.of(),
                List.of(unit("c1", body), unit(Unit.AUXILIARY_ID, auxiliary)));
        final EffectiveTargets targets =
                EffectiveTargets.apply(opened, records, Set.of(), (segment, masked) -> Result.ok(masked), "en", "uk");
        final Map<String, String> written = new LinkedHashMap<>();
        targets.document().units().stream()
                .flatMap(unit -> unit.segments().stream())
                .filter(segment -> segment.targetInner() != null)
                .forEach(segment -> written.put(segment.id(), segment.targetInner()));
        return written;
    }

    private static void add(
            final List<Segment> into,
            final List<SegmentRecord> records,
            final String id,
            final String unit,
            final SegmentKind kind,
            final String text,
            final String target) {
        final Segment segment = new Segment(
                id,
                unit,
                1,
                kind,
                text,
                text,
                Map.of(),
                "h" + id,
                null,
                null,
                new ByteSpanAnchor(0, text.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
        into.add(segment);
        records.add(record(segment, target));
    }

    private static Unit unit(final String id, final List<Segment> segments) {
        return new Unit(id, 1, id + ".txt", "text/plain", new SkeletonHandle(id), segments);
    }

    private static SegmentRecord record(final Segment segment, @Nullable final String target) {
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
