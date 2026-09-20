package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BookExporterTestSupport.documents;
import static ua.bookloom.pipeline.BookExporterTestSupport.onlyDecision;
import static ua.bookloom.pipeline.BookExporterTestSupport.opened;
import static ua.bookloom.pipeline.BookExporterTestSupport.segmentCount;
import static ua.bookloom.pipeline.BookExporterTestSupport.zipEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.pipeline.TranslationRequest;

/** Real same-format exports with decisions applied to freshly opened document state. */
class BookExporterFormatTest {

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = documents();
    }

    // A real EPUB unmask restores inline markup, and export writes exactly that paragraph and target language.
    @Test
    void export_acceptedEpubDecision_restoresMarkupAndReplacesLanguage() {
        final Path source =
                TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of("Tom &amp; <i>Jerry</i> ran.")), "en");
        final Path destination = tempDir.resolve("Book.uk.epub");
        final Document opened = opened(documents, source);
        final Segment segment = opened.units().getFirst().segments().getFirst();
        final Result<String> restored = documents.unmask(opened.format(), segment, "TOM & ⟦g0⟧JERRY⟦g1⟧ RAN.");
        final Document decided = onlyDecision(
                opened, SegmentStatus.ACCEPTED, Objects.requireNonNull(restored.data(), "restored target"));
        assertThat(documents.close(opened).data()).isTrue();

        final Result<Path> result =
                new BookExporter(documents).export(request(source, destination, true), decided, () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(firstMatch(zipEntry(destination, "OEBPS/ch0.xhtml"), "<p>.*?</p>"))
                .isEqualTo("<p>TOM &amp; <i>JERRY</i> RAN.</p>");
        assertThat(firstMatch(zipEntry(destination, "OEBPS/content.opf"), "<dc:language>.*?</dc:language>"))
                .isEqualTo("<dc:language>uk</dc:language>");
        assertThat(zipEntry(destination, "OEBPS/content.opf")).doesNotContain("<dc:language>en</dc:language>");
        final Document exported = opened(documents, destination);
        assertThat(segmentCount(exported)).isEqualTo(segmentCount(decided));
        assertThat(documents.close(exported).data()).isTrue();
    }

    // FB2 receives the target language by replacement and remains parseable with the source segment count.
    @Test
    void export_fb2Snapshot_replacesLanguageAndRetainsSegmentCount() throws Exception {
        final Path source = TestBooks.fb2(tempDir.resolve("Book.fb2"), List.of("Hello."), "en");
        final Path destination = tempDir.resolve("Book.uk.fb2");
        final Document decided = opened(documents, source);
        assertThat(documents.close(decided).data()).isTrue();

        final Result<Path> result =
                new BookExporter(documents).export(request(source, destination, true), decided, () -> false);

        final String exportedXml = Files.readString(destination);
        assertThat(result.data()).isEqualTo(destination);
        assertThat(firstMatch(exportedXml, "<lang>.*?</lang>")).isEqualTo("<lang>uk</lang>");
        assertThat(exportedXml).doesNotContain("<lang>en</lang>");
        final Document exported = opened(documents, destination);
        assertThat(segmentCount(exported)).isEqualTo(segmentCount(decided));
        assertThat(documents.close(exported).data()).isTrue();
    }

    // A FLAGGED Markdown segment with no retained target exports its source text exactly.
    @Test
    void export_flaggedMarkdownWithoutTarget_keepsLiteralSource() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door.");
        final Path destination = tempDir.resolve("Book.uk.md");
        final Document opened = opened(documents, source);
        final Document decided = onlyDecision(opened, SegmentStatus.FLAGGED, null);
        assertThat(documents.close(opened).data()).isTrue();

        final Result<Path> result =
                new BookExporter(documents).export(request(source, destination, true), decided, () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(Files.readString(destination)).isEqualTo("He opened the *old* door.");
    }

    // Decisions are grafted by id onto fresh records while the fresh document, skeleton and anchor stay intact.
    @Test
    void export_closedDecisionSnapshot_usesFreshDocumentIdentityAndAnchors() {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Hello.");
        final Path destination = tempDir.resolve("Book.uk.md");
        final Document original = opened(documents, source);
        final Document decided = onlyDecision(original, SegmentStatus.ACCEPTED, "Вітаю.");
        assertThat(documents.close(original).data()).isTrue();
        final BookExporterTestSupport.RecordingDocumentPort port =
                new BookExporterTestSupport.RecordingDocumentPort(documents);

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination, true), decided, () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(port.openedDocuments()).hasSize(2);
        assertThat(port.writtenDocuments()).singleElement().satisfies(written -> {
            final Document fresh = port.openedDocuments().getFirst();
            assertThat(written.id()).isEqualTo(fresh.id()).isNotEqualTo(decided.id());
            assertThat(written.units().getFirst().skeleton())
                    .isSameAs(fresh.units().getFirst().skeleton());
            assertThat(written.units().getFirst().segments().getFirst().anchor())
                    .isSameAs(fresh.units().getFirst().segments().getFirst().anchor());
            assertThat(written.units().getFirst().segments().getFirst().id())
                    .isEqualTo(decided.units().getFirst().segments().getFirst().id());
            assertThat(written.units().getFirst().segments().getFirst().status())
                    .isEqualTo(SegmentStatus.ACCEPTED);
            assertThat(written.units().getFirst().segments().getFirst().targetInner())
                    .isEqualTo("Вітаю.");
        });
        assertThat(port.openedDocuments())
                .allSatisfy(
                        document -> assertThat(documents.close(document).data()).isFalse());
    }

    // Reordered decisions still attach to their original segment IDs and write in fresh source order.
    @Test
    void export_reversedDecisionEnumeration_matchesTranslationsBySegmentId() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "First.\n\nSecond.");
        final Path destination = tempDir.resolve("Book.uk.md");
        final Document original = opened(documents, source);
        final Unit unit = original.units().getFirst();
        final Segment first = unit.segments().getFirst().withDecision(SegmentStatus.ACCEPTED, "Перший.");
        final Segment second = unit.segments().get(1).withDecision(SegmentStatus.ACCEPTED, "Другий.");
        final Document reversed = original.withUnits(List.of(unit.withSegments(List.of(second, first))));
        assertThat(documents.close(original).data()).isTrue();
        final BookExporterTestSupport.RecordingDocumentPort port =
                new BookExporterTestSupport.RecordingDocumentPort(documents);

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination, true), reversed, () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(port.writtenDocuments().getFirst().units().getFirst().segments())
                .extracting(Segment::id, Segment::targetInner)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(first.id(), "Перший."),
                        org.assertj.core.groups.Tuple.tuple(second.id(), "Другий."));
        assertThat(Files.readString(destination)).isEqualTo("Перший.\n\nДругий.");
    }

    private static String firstMatch(final String text, final String regularExpression) {
        final Matcher matcher = Pattern.compile(regularExpression).matcher(text);
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }

    private static TranslationRequest request(final Path source, final Path destination, final boolean overwrite) {
        return new TranslationRequest(source, destination, "uk", "en", overwrite);
    }
}
