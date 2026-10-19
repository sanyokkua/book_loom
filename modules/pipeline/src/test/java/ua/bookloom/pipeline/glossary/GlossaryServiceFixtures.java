package ua.bookloom.pipeline.glossary;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.GlossaryEntry;
import ua.bookloom.api.project.Project;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.api.project.SegmentRecord;
import ua.bookloom.api.project.TermType;

/** Builds the documents, glossary entries and decided segments the glossary service tests read. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GlossaryServiceFixtures {

    static final String PROJECT = "p1";

    static GlossaryEntry entry(
            final String term, @Nullable final String target, final TermType type, final boolean locked) {
        return new GlossaryEntry(GlossaryIds.of(PROJECT, term), PROJECT, term, target, type, Gender.MALE, locked);
    }

    /** A one-body-unit book whose paragraphs are the given lines, plus an auxiliary unit holding {@code auxiliary}. */
    static Document book(final List<String> body, final String auxiliary) {
        final Unit bodyUnit = unit("ch1", 0, body);
        final Unit auxUnit = unit(Unit.AUXILIARY_ID, 1, List.of(auxiliary));
        return new Document("d1", BookFormat.TXT, "en", null, null, null, "hash", Map.of(), List.of(bodyUnit, auxUnit));
    }

    static List<String> repeated(final String line, final int times) {
        return IntStream.range(0, times).mapToObj(index -> line).toList();
    }

    static SegmentRecord decided(final String segmentId, final String target) {
        return new SegmentRecord(
                PROJECT,
                segmentId,
                "ch1",
                0,
                SegmentKind.PARAGRAPH,
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

    private static Unit unit(final String id, final int order, final List<String> lines) {
        final List<Segment> segments = IntStream.range(0, lines.size())
                .mapToObj(index -> segment(id, index, lines.get(index)))
                .toList();
        return new Unit(id, order, id + ".txt", "text/plain", new SkeletonHandle(id), segments);
    }

    private static Segment segment(final String unit, final int order, final String masked) {
        return new Segment(
                unit + ":" + order,
                unit,
                order,
                SegmentKind.PARAGRAPH,
                masked,
                masked,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0);
    }

    static Project project(final BookBrief brief) {
        return new Project(PROJECT, Path.of("book.txt"), BookFormat.TXT, "hash", brief);
    }

    static BookBrief withTarget(final BookBrief brief, final String target) {
        return new BookBrief(
                brief.sourceLanguage(),
                target,
                brief.genre(),
                brief.register(),
                brief.voiceEra(),
                brief.audience(),
                brief.names(),
                brief.foreignPassages(),
                brief.footnotes(),
                brief.units(),
                brief.balance(),
                brief.alsoTranslate(),
                brief.dial());
    }
}
