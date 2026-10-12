package ua.bookloom.pipeline.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.inject.Guice;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.document.BookFormat;
import ua.bookloom.api.document.ByteSpanAnchor;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.SkeletonHandle;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.document.UnitRole;
import ua.bookloom.document.DocumentModule;

/** What the brief calls are told about the book: its metadata and a description cut to a fixed length. */
class BookFactsTest {

    private static final int SIX_HUNDRED = 600;

    @TempDir
    private Path tempDir;

    private static Segment segment(final String unit, final int order, final SegmentKind kind, final String masked) {
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
                0.0);
    }

    private static Unit unit(final String id, final UnitRole role, final List<Segment> segments) {
        return new Unit(id, 0, id + ".xhtml", "application/xhtml+xml", new SkeletonHandle(id), segments, role);
    }

    private static Document book(final Map<String, String> metadata, final List<Unit> units) {
        return new Document("book", BookFormat.EPUB, "en", null, null, null, "hash", metadata, units);
    }

    private static Document withDescriptions(final String... paragraphs) {
        final List<Segment> segments = IntStream.range(0, paragraphs.length)
                .mapToObj(
                        index -> segment(Unit.AUXILIARY_ID, index, SegmentKind.METADATA_DESCRIPTION, paragraphs[index]))
                .toList();
        return book(Map.of(), List.of(unit(Unit.AUXILIARY_ID, UnitRole.BODY, segments)));
    }

    private static String words(final int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(number -> String.format("w%03d", number))
                .collect(Collectors.joining(" "));
    }

    // IF the book declares title, author, year and publisher, THEN the facts hold each of them, the year as a number.
    @Test
    void of_fullMetadata_holdsTitleAuthorYearAndPublisher() {
        final Document document = book(
                Map.of("title", "The Lamp", "author", "Ada Quill", "date", "1923", "publisher", "Larkspur Press"),
                List.of());

        assertThat(BookFacts.of(document))
                .isEqualTo(new BookFacts("The Lamp", "Ada Quill", 1923, "Larkspur Press", null));
    }

    // IF the metadata has nothing, THEN every fact is absent, not blank.
    @Test
    void of_noMetadataAndNoDescription_hasEveryFactAbsent() {
        assertThat(BookFacts.of(book(Map.of(), List.of()))).isEqualTo(new BookFacts(null, null, null, null, null));
    }

    // IF a value is only white space, THEN the fact is absent.
    @Test
    void of_blankValues_areAbsent() {
        final Document document = book(Map.of("title", "  ", "author", "", "publisher", " \t"), List.of());

        assertThat(BookFacts.of(document)).isEqualTo(new BookFacts(null, null, null, null, null));
    }

    // IF the date is not a number (a hand-built map, not a reader's), THEN there is no year rather than a crash.
    @ParameterizedTest
    @ValueSource(strings = {"soon", "", "19x3"})
    void of_dateThatIsNotAYear_hasNoYear(final String date) {
        assertThat(BookFacts.of(book(Map.of("date", date), List.of())).year()).isNull();
    }

    // IF a value has stray spaces around it, THEN the fact is the stripped text.
    @Test
    void of_valuesWithSurroundingSpace_areStripped() {
        final Document document = book(Map.of("title", " The Lamp ", "date", " 1923 "), List.of());

        assertThat(BookFacts.of(document)).isEqualTo(new BookFacts("The Lamp", null, 1923, null, null));
    }

    // IF the description is short, THEN it is kept whole.
    @Test
    void of_shortDescription_isKeptWhole() {
        assertThat(BookFacts.of(withDescriptions("A tale of a lamp.")).description())
                .isEqualTo("A tale of a lamp.");
    }

    // IF the description is exactly 600 characters, THEN it is not cut.
    @Test
    void of_descriptionOfExactlySixHundredCharacters_isNotCut() {
        final String text = "a".repeat(SIX_HUNDRED);

        assertThat(BookFacts.of(withDescriptions(text)).description()).isEqualTo(text);
    }

    // IF the description is longer than 600 characters, THEN it is cut at the last word boundary that keeps it within
    // 600 characters: 130 words of "wNNN" make the first 120 words 599 characters.
    @Test
    void of_longDescription_isCutAtAWordBoundaryWithinSixHundredCharacters() {
        final String description = BookFacts.of(withDescriptions(words(130))).description();

        assertThat(description).hasSize(599).startsWith("w001 w002").endsWith(" w119 w120");
    }

    // IF the 601st character begins a new word, THEN the cut falls just before it and nothing is lost.
    @Test
    void of_wordEndingExactlyAtTheLimit_isKept() {
        final String text = "a".repeat(SIX_HUNDRED) + " tail words follow";

        assertThat(BookFacts.of(withDescriptions(text)).description()).isEqualTo("a".repeat(SIX_HUNDRED));
    }

    // IF there is no word boundary in the first 600 characters, THEN the text is cut at 600.
    @Test
    void of_oneLongWord_isCutAtSixHundredCharacters() {
        final String description =
                BookFacts.of(withDescriptions("a".repeat(700))).description();

        assertThat(description).isEqualTo("a".repeat(SIX_HUNDRED));
    }

    // IF a description has several paragraphs, THEN they are joined by one space in document order, and the cut
    // applies to the joined text.
    @Test
    void of_severalDescriptionParagraphs_areJoinedByOneSpaceInOrder() {
        final Document document = withDescriptions("First paragraph.", "Second paragraph.");

        assertThat(BookFacts.of(document).description()).isEqualTo("First paragraph. Second paragraph.");
    }

    // IF a description holds inline markup tokens, THEN the facts hold the readable text without the tokens.
    @Test
    void of_descriptionWithPlaceholderTokens_dropsTheTokens() {
        final Document document = withDescriptions("A tale of ⟦g0⟧the lamp⟦g1⟧ and  its keeper.");

        assertThat(BookFacts.of(document).description()).isEqualTo("A tale of the lamp and its keeper.");
    }

    // IF other segments are in the auxiliary unit or the body, THEN only the description segments are read.
    @Test
    void of_otherSegments_areNotTheDescription() {
        final Unit auxiliary = unit(
                Unit.AUXILIARY_ID,
                UnitRole.BODY,
                List.of(
                        segment(Unit.AUXILIARY_ID, 0, SegmentKind.METADATA_TITLE, "The Lamp"),
                        segment(Unit.AUXILIARY_ID, 1, SegmentKind.METADATA_DESCRIPTION, "A tale of a lamp.")));
        final Unit body =
                unit("c01", UnitRole.BODY, List.of(segment("c01", 0, SegmentKind.PARAGRAPH, "The lamp burned.")));

        assertThat(BookFacts.of(book(Map.of(), List.of(body, auxiliary))).description())
                .isEqualTo("A tale of a lamp.");
    }

    // IF the book has an annotation (FB2), THEN its paragraphs are the description, read through the real FB2 reader.
    @Test
    void of_fb2AnnotationAndPublishInfo_holdsYearPublisherAndTheAnnotationAsDescription() throws IOException {
        final Path file = Files.writeString(tempDir.resolve("book.fb2"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0">
                  <description>
                    <title-info>
                      <author><first-name>Ada</first-name><last-name>Quill</last-name></author>
                      <book-title>The Lamp</book-title>
                      <annotation><p>A keeper tends a lamp.</p><p>The sea rises.</p></annotation>
                      <date>1923</date>
                      <lang>en</lang>
                    </title-info>
                    <publish-info><publisher>Larkspur Press</publisher></publish-info>
                  </description>
                  <body><section><p>The lamp burned low.</p></section></body>
                </FictionBook>
                """, StandardCharsets.UTF_8);
        final DocumentPort documents =
                Guice.createInjector(new DocumentModule()).getInstance(DocumentPort.class);
        final Document document = documents.open(file).data();

        assertThat(BookFacts.of(document))
                .isEqualTo(new BookFacts(
                        "The Lamp", "Ada Quill", 1923, "Larkspur Press", "A keeper tends a lamp. The sea rises."));
    }
}
