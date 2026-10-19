package ua.bookloom.pipeline.glossary;

import com.google.inject.Guice;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.PlaceholderPair;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.document.DocumentModule;
import ua.bookloom.pipeline.eval.NamesBookFiles;
import ua.bookloom.pipeline.index.IndexTestBooks;

/**
 * The books the scan goldens are recorded over: the earth-gravity fixture in its four formats, the book-index sample,
 * the nine invented names-suite books and one invented multi-chapter book with front and back matter, headings, a title
 * line, a printing note, a foreign segment and a foreign inline run.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ScanGoldenBooks {

    static final String SAMPLE = "index-sample";
    static final String INVENTED = "invented-front-back";
    private static final Path FIXTURES = Path.of("../app/src/test/resources/fixtures/earth-gravity");
    private static final String NAMES_PREFIX = "names-";
    private static final List<String> EARTH_GRAVITY =
            List.of("earth-gravity.epub", "earth-gravity.fb2", "earth-gravity.md", "earth-gravity.txt");

    /** Every book id; a golden file is {@code golden/scans/<id>.json}. */
    static Stream<String> ids() {
        return Stream.of(
                        EARTH_GRAVITY.stream(),
                        Stream.of(SAMPLE, INVENTED),
                        NamesBookFiles.ids().stream().map(id -> NAMES_PREFIX + id))
                .flatMap(stream -> stream);
    }

    /** Opens or builds the book with the id; {@code dir} receives any file written for it. */
    static Document open(final String id, final Path dir) {
        if (SAMPLE.equals(id)) {
            return IndexTestBooks.sample();
        }
        if (INVENTED.equals(id)) {
            return invented();
        }
        final Path file = id.startsWith(NAMES_PREFIX)
                ? NamesBookFiles.write(id.substring(NAMES_PREFIX.length()), dir)
                : FIXTURES.resolve(id);
        return Objects.requireNonNull(
                Guice.createInjector(new DocumentModule())
                        .getInstance(DocumentPort.class)
                        .open(file)
                        .data(),
                id);
    }

    private static Document invented() {
        return new Document(
                INVENTED,
                BookFormat.EPUB,
                "en",
                null,
                null,
                null,
                "hash",
                Map.of(),
                List.of(front(), chapterOne(), chapterTwo(), auxiliary(), back()));
    }

    private static Unit front() {
        return unit(
                "front",
                0,
                UnitRole.FRONT_MATTER,
                segment("front", 0, SegmentKind.TITLE, "The Lantern Road", null, List.of()),
                paragraph("front", 1, "Copyright 1911 by the Quay Press. All rights reserved."),
                paragraph("front", 2, "For Orsolya Orsolya Orsolya, who waited at Marrow Quay."));
    }

    // A heading, a title line inside a paragraph, a foreign segment and an inline foreign run among running text.
    private static Unit chapterOne() {
        return unit(
                "ch1",
                1,
                UnitRole.BODY,
                segment("ch1", 0, SegmentKind.HEADING, "Chapter One: Ysolde Arrives", null, List.of()),
                paragraph(
                        "ch1", 1, "Ysolde stepped off the boat at Marrow Quay. She looked for Corvin among the crowd."),
                paragraph(
                        "ch1",
                        2,
                        "The Lantern Road\nCorvin met her at the gate. He took Ysolde’s bag and led her up the Lantern Road."),
                paragraph(
                        "ch1",
                        3,
                        "“Welcome,” said Corvin. “The road to Marrow Quay is long.” Ysolde nodded; she had walked it before."),
                segment("ch1", 4, SegmentKind.PARAGRAPH, "Corvin Corvin Corvin dit bonjour à Ysolde.", "fr", List.of()),
                foreignInline(),
                paragraph(
                        "ch1",
                        6,
                        "Mr. Pell, the harbour clerk, waved. Pell knew Ysolde well. He gave Corvin a letter."),
                paragraph("ch1", 7, "Printed by the Quay Press in 1911."));
    }

    private static Segment foreignInline() {
        return segment(
                "ch1",
                5,
                SegmentKind.PARAGRAPH,
                "Ysolde said ⟦g1⟧Gran Bazar Gran Bazar⟦g2⟧ to Corvin, and he smiled.",
                null,
                List.of(new PlaceholderPair("⟦g1⟧", "⟦g2⟧", "es")));
    }

    private static Unit chapterTwo() {
        return unit(
                "ch2",
                2,
                UnitRole.BODY,
                segment("ch2", 0, SegmentKind.HEADING, "Chapter Two", null, List.of()),
                paragraph(
                        "ch2",
                        1,
                        "Corvin lit the lamps along the Lantern Road. Ysolde carried the oil. She did not complain."),
                segment("ch2", 2, SegmentKind.VERSE_LINE, "O Marrow Quay, O Marrow Quay", null, List.of()),
                paragraph(
                        "ch2",
                        3,
                        "Pell met them at Marrow Quay again. He said Corvin had been right about the weather."),
                paragraph(
                        "ch2",
                        4,
                        "Ysolde laughed and Corvin laughed. The harbour was quiet, and the lamps burned until dawn."));
    }

    private static Unit auxiliary() {
        return unit(
                Unit.AUXILIARY_ID,
                3,
                UnitRole.BODY,
                segment(Unit.AUXILIARY_ID, 0, SegmentKind.METADATA_TITLE, "The Lantern Road", null, List.of()));
    }

    private static Unit back() {
        return unit(
                "back",
                4,
                UnitRole.BACK_MATTER,
                paragraph("back", 0, "Thanks to Ysolde, Corvin and Pell. Pell, Pell and Pell again."));
    }

    private static Unit unit(final String id, final int order, final UnitRole role, final Segment... segments) {
        return new Unit(
                id, order, id + ".xhtml", "application/xhtml+xml", new SkeletonHandle(id), List.of(segments), role);
    }

    private static Segment paragraph(final String unit, final int order, final String masked) {
        return segment(unit, order, SegmentKind.PARAGRAPH, masked, null, List.of());
    }

    private static Segment segment(
            final String unit,
            final int order,
            final SegmentKind kind,
            final String masked,
            @Nullable final String declaredLanguage,
            final List<PlaceholderPair> pairs) {
        return new Segment(
                unit + ":" + order,
                unit,
                order,
                kind,
                masked,
                masked,
                Map.of(),
                "hash",
                null,
                null,
                new ByteSpanAnchor(0, masked.length()),
                null,
                SegmentStatus.PENDING,
                0.0,
                declaredLanguage,
                pairs,
                List.of());
    }
}
