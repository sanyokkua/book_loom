package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.export.ExportJobFixture.error;
import static ua.bookloom.pipeline.export.ExportJobFixture.hiddenFiles;
import static ua.bookloom.pipeline.export.ExportJobFixture.ok;
import static ua.bookloom.pipeline.export.ExportJobFixture.read;
import static ua.bookloom.pipeline.export.ExportJobFixture.request;
import static ua.bookloom.pipeline.export.ExportJobFixture.segment;
import static ua.bookloom.pipeline.export.ExportJobFixture.zipEntry;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentKind;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.document.Unit;
import ua.bookloom.api.pipeline.ExportReport;
import ua.bookloom.api.pipeline.SideFile;
import ua.bookloom.api.pipeline.SourceFallback;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.TestDocuments;

/**
 * The written book replaces the destination only once it re-opens with the source's body segments, each carrying the
 * placeholders written into it — so a paragraph whose formatting was damaged never slips through a matching count.
 */
class ExportJobVerificationTest {

    private static final String IMAGE_PARAGRAPH = "Before <img src=\"fig1.png\" alt=\"Figure 1\"/> after";

    @TempDir
    private Path tempDir;

    private ExportJobFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new ExportJobFixture();
    }

    // A written book that re-opens with 2 of its 3 paragraphs never replaces the existing destination.
    @Test
    void run_writtenBookLosesAParagraph_returnsValidationAndLeavesDestination() throws IOException {
        final String id =
                fixture.importBook(TestBooks.markdown(tempDir.resolve("Book.md"), "One.\n\nTwo.\n\nThree."), "en");
        final Path destination = TestBooks.markdown(tempDir.resolve("Book.uk.md"), "Existing.");
        final ExportServiceImpl service = fixture.serviceOver(new ParagraphDroppingPort(fixture.documents()));

        final Result<ExportReport> result = ExportJobFixture.export(service, request(id, destination, true));

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(destination)).isEqualTo("Existing.");
        assertThat(hiddenFiles(tempDir)).isEmpty();
    }

    // The count still matches, yet the translated paragraph lost an image when written: it is written again in its
    // source and the export succeeds, naming it by its locator.
    @Test
    void run_writtenParagraphLosesAPlaceholder_writesItInSourceAndNamesIt() {
        final String id = fiveChapterBook();
        fixture.accept(id, "ch05.xhtml:11", "⟦g0⟧Він⟦g1⟧ побачив ⟦g2⟧ і ⟦g3⟧.");
        final Path destination = tempDir.resolve("Book.uk.epub");
        final ExportServiceImpl service =
                fixture.serviceOver(new FragmentDroppingPort(fixture.documents(), "ch05.xhtml:11", "g3", false));

        final ExportReport report = ok(ExportJobFixture.export(service, request(id, destination, false)));

        assertThat(report.sourceFallbacks()).containsExactly(new SourceFallback("ch05.xhtml:11", "ch5 · p12"));
        assertThat(zipEntry(destination, "ch05.xhtml"))
                .contains("<b>He</b> saw ")
                .doesNotContain("Він");
        assertThat(hiddenFiles(tempDir)).isEmpty();
    }

    // A paragraph whose markup changes even when written in its source still refuses the export, naming it.
    @Test
    void run_paragraphDamagedEvenInSource_returnsValidationNamingIt() {
        final String id = fiveChapterBook();
        fixture.accept(id, "ch05.xhtml:11", "⟦g0⟧Він⟦g1⟧ побачив ⟦g2⟧ і ⟦g3⟧.");
        final Path destination = tempDir.resolve("Book.uk.epub");
        final ExportServiceImpl service =
                fixture.serviceOver(new FragmentDroppingPort(fixture.documents(), "ch05.xhtml:11", "g3", true));

        final Result<ExportReport> result = ExportJobFixture.export(service, request(id, destination, false));

        final AppError failure = error(result);
        assertThat(failure.code()).isEqualTo(ErrorCode.validation);
        assertThat(failure.message()).contains("ch5 · p12");
        assertThat(destination).doesNotExist();
        assertThat(hiddenFiles(tempDir)).isEmpty();
    }

    // A stored target whose placeholders no longer match its segment is written in the source before writing, and the
    // report counts it as pending rather than written.
    @Test
    void run_oneStoredTargetWithBrokenPlaceholders_writesItInSourceAndReportsIt() throws IOException {
        final String id = fixture.importBook(
                TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door.\n\nShe left."), "en");
        brokenTarget(id, "Book.md:0");
        fixture.accept(id, "Book.md:1", "Вона пішла.");
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportReport report = ok(fixture.export(request(id, destination, false)));

        assertThat(report.sourceFallbacks())
                .extracting(SourceFallback::segmentId)
                .containsExactly("Book.md:0");
        assertThat(report.written()).isEqualTo(1);
        assertThat(Files.readString(destination))
                .contains("He opened the *old* door.")
                .contains("Вона пішла.");
    }

    // A segment flagged with no target is written in its source and the report names it, so the person can find every
    // place the book is not a translation; it is not counted as a broken-formatting fallback.
    @Test
    void run_flaggedSegmentWithNoTarget_isListedAsASourceFallbackWithNoTarget() {
        final String id = fixture.importBook(
                TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the door.\n\nShe left."), "en");
        fixture.decide(
                id,
                "Book.md:0",
                record -> record.withStatus(SegmentStatus.FLAGGED).withMachineTarget(null, null));
        fixture.accept(id, "Book.md:1", "Вона пішла.");

        final ExportReport report = ok(fixture.export(request(id, tempDir.resolve("Book.uk.md"), false)));

        assertThat(report.sourceFallbacks())
                .containsExactly(new SourceFallback("Book.md:0", "ch1 · p01", SourceFallback.Reason.NO_TARGET));
        assertThat(report.pending()).isEqualTo(1);
        assertThat(report.written()).isEqualTo(1);
    }

    // A segment flagged for a quote or script check keeps its draft as the machine target: the export writes the draft,
    // counts it as written and does not list it as a source fallback.
    @Test
    void run_flaggedSegmentWithAMachineTarget_isWrittenAsTheDraftAndNotListed() throws IOException {
        final String id = fixture.importBook(
                TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the door.\n\nShe left."), "en");
        fixture.decide(
                id,
                "Book.md:0",
                record -> record.withStatus(SegmentStatus.FLAGGED)
                        .withMachineTarget("Він «відчинив «двері.", "Він «відчинив «двері."));
        fixture.accept(id, "Book.md:1", "Вона пішла.");
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportReport report = ok(fixture.export(request(id, destination, false)));

        assertThat(report.sourceFallbacks()).isEmpty();
        assertThat(report.written()).isEqualTo(2);
        assertThat(Files.readString(destination))
                .contains("Він «відчинив «двері.")
                .contains("Вона пішла.");
    }

    // Two broken targets are both written in the source and both listed, in book order.
    @Test
    void run_twoStoredTargetsWithBrokenPlaceholders_listsBoth() throws IOException {
        final String id = fixture.importBook(
                TestBooks.markdown(tempDir.resolve("Book.md"), "He opened the *old* door.\n\nShe *left*."), "en");
        brokenTarget(id, "Book.md:0");
        brokenTarget(id, "Book.md:1");
        final Path destination = tempDir.resolve("Book.uk.md");

        final ExportReport report = ok(fixture.export(request(id, destination, false)));

        assertThat(report.sourceFallbacks())
                .extracting(SourceFallback::segmentId)
                .containsExactly("Book.md:0", "Book.md:1");
        assertThat(report.written()).isZero();
        assertThat(Files.readString(destination)).isEqualTo("He opened the *old* door.\n\nShe *left*.");
    }

    // An accepted record whose masked target lost its closing token — as a stored machine target can be after a
    // gate change — is not written as it stands.
    private void brokenTarget(final String projectId, final String segmentId) {
        fixture.decide(
                projectId,
                segmentId,
                record ->
                        record.withStatus(SegmentStatus.ACCEPTED).withMachineTarget("Зламано *тут", "Зламано ⟦g0⟧тут"));
    }

    // A translated alt text sits inside the translated paragraph's image markup, which the check sets aside.
    @Test
    void run_translatedAltTextInTranslatedEpubParagraph_verifiesAndWritesIt() {
        final String id = fixture.importBook(
                TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of(IMAGE_PARAGRAPH)), "en"), "en");
        fixture.accept(id, fixture.recordOfKind(id, SegmentKind.ALT).segmentId(), "Рисунок 1");
        fixture.accept(id, fixture.bodyRecords(id).getFirst().segmentId(), "Перед ⟦g0⟧ після");
        final Path destination = tempDir.resolve("Book.uk.epub");

        ok(fixture.export(request(id, destination, false)));

        assertThat(zipEntry(destination, "OEBPS/ch0.xhtml")).contains("<img src=\"fig1.png\" alt=\"Рисунок 1\" />");
    }

    // The pending paragraph is written as its source, its image carrying the translated description.
    @Test
    void run_translatedAltTextInPendingEpubParagraph_writesTheSourceParagraphWithIt() {
        final String id = fixture.importBook(
                TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of(IMAGE_PARAGRAPH)), "en"), "en");
        fixture.accept(id, fixture.recordOfKind(id, SegmentKind.ALT).segmentId(), "Рисунок 1");
        final Path destination = tempDir.resolve("Book.uk.epub");

        ok(fixture.export(request(id, destination, false)));

        assertThat(zipEntry(destination, "OEBPS/ch0.xhtml"))
                .contains("<p>Before <img src=\"fig1.png\" alt=\"Рисунок 1\" /> after</p>");
    }

    // In Markdown the description is the text between ![ and ](, set aside by the check the same way.
    @Test
    void run_translatedMarkdownAltText_verifiesAndWritesIt() {
        final String id = fixture.importBook(
                TestBooks.markdown(tempDir.resolve("Book.md"), "See ![Figure 1](fig1.png) below."), "en");
        fixture.accept(id, fixture.recordOfKind(id, SegmentKind.ALT).segmentId(), "Рисунок 1");
        fixture.accept(id, fixture.bodyRecords(id).getFirst().segmentId(), "Дивіться ⟦g0⟧ нижче.");
        final Path destination = tempDir.resolve("Book.uk.md");

        ok(fixture.export(request(id, destination, false)));

        assertThat(read(destination)).isEqualTo("Дивіться ![Рисунок 1](fig1.png) нижче.");
    }

    // Translated alike, the NCX label merges with its navigation label on re-opening; auxiliary text is not counted.
    @Test
    void run_ncxLabelTranslatedLikeItsNavigationLabel_exports() {
        final String id = fixture.importBook(
                TestBooks.epubWithNcx(tempDir.resolve("Book.epub"), List.of(List.of("One.")), "Storm", "The Storm"),
                "en");
        fixture.records(id).stream()
                .filter(record -> record.kind() == SegmentKind.NAV_LABEL)
                .forEach(record -> fixture.accept(id, record.segmentId(), "Буря"));
        final Path destination = tempDir.resolve("Book.uk.epub");

        final ExportReport report = ok(fixture.export(request(id, destination, false)));

        assertThat(report.verifiedSegments()).isEqualTo(1);
        assertThat(zipEntry(destination, "OEBPS/toc.ncx")).contains("<text>Буря</text>");
    }

    // A folder removed as the export starts gives the write's own error and leaves no file behind.
    @Test
    void run_destinationFolderDeleted_returnsWriteErrorAndLeavesNothing() throws IOException {
        final String id = fixture.importBook(TestBooks.markdown(tempDir.resolve("Book.md"), "One."), "en");
        final Path folder = Files.createDirectory(tempDir.resolve("out"));
        final Path destination = folder.resolve("Book.uk.md");
        final var job = ok(fixture.service().newExport(request(id, destination, false), null));
        Files.delete(folder);

        final Result<ExportReport> result = job.run();

        assertThat(error(result).code()).isEqualTo(ErrorCode.internal);
        assertThat(folder).doesNotExist();
    }

    // The source's bytes changed after it was opened, so the stored decisions no longer describe it.
    @Test
    void run_sourceChangedAfterImport_returnsValidationAndWritesNothing() throws IOException {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.");
        final String id = fixture.importBook(source, "en");
        Files.writeString(source, "Changed.");
        final Path destination = tempDir.resolve("Book.uk.md");

        final Result<ExportReport> result = fixture.export(request(id, destination, false));

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(destination).doesNotExist();
        assertThat(hiddenFiles(tempDir)).isEmpty();
    }

    // A failed verification writes no side file either, even the report that would describe it.
    @Test
    void run_failedVerificationWithReportChosen_writesNeitherBookNorReport() {
        final String id =
                fixture.importBook(TestBooks.markdown(tempDir.resolve("Book.md"), "One.\n\nTwo.\n\nThree."), "en");
        final Path destination = tempDir.resolve("Book.uk.md");
        final ExportServiceImpl service = fixture.serviceOver(new ParagraphDroppingPort(fixture.documents()));

        final Result<ExportReport> result =
                ExportJobFixture.export(service, request(id, destination, false, Set.of(SideFile.QUALITY_REPORT)));

        assertThat(error(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(destination).doesNotExist();
        assertThat(tempDir.resolve("Book.uk.report.md")).doesNotExist();
        assertThat(hiddenFiles(tempDir)).isEmpty();
    }

    /** Five chapters of twelve paragraphs; {@code ch05.xhtml:11} holds a bold word and two images. */
    private String fiveChapterBook() {
        final List<String> names = IntStream.rangeClosed(1, 5)
                .mapToObj(number -> String.format("ch%02d.xhtml", number))
                .toList();
        final List<List<String>> chapters = IntStream.rangeClosed(1, 5)
                .mapToObj(number -> IntStream.range(0, 12)
                        .mapToObj(index -> number == 5 && index == 11
                                ? "<b>He</b> saw <img src=\"a.png\"/> and <img src=\"b.png\"/>."
                                : "Chapter " + number + " line " + index + ".")
                        .toList())
                .toList();
        return fixture.importBook(TestBooks.epubAtRoot(tempDir.resolve("Book.epub"), names, chapters), "en");
    }

    /** A writer whose output loses its last paragraph after the real write. */
    private static final class ParagraphDroppingPort extends TestDocuments.ForwardingPort {

        ParagraphDroppingPort(final DocumentPort delegate) {
            super(delegate);
        }

        @Override
        public Result<Path> write(
                final Document document,
                final Path destination,
                @Nullable final String sourceLanguage,
                final String targetLanguage) {
            final Result<Path> written = super.write(document, destination, sourceLanguage, targetLanguage);
            try {
                final String text = Files.readString(destination);
                Files.writeString(destination, text.substring(0, text.lastIndexOf("\n\n")));
            } catch (IOException cause) {
                throw new UncheckedIOException(cause);
            }
            return written;
        }
    }

    /** A writer that drops one placeholder's fragment from one segment's target before the real write. */
    private static final class FragmentDroppingPort extends TestDocuments.ForwardingPort {

        private final String segmentId;
        private final String placeholder;
        private final boolean evenInSource;

        FragmentDroppingPort(
                final DocumentPort delegate,
                final String segmentId,
                final String placeholder,
                final boolean evenInSource) {
            super(delegate);
            this.segmentId = segmentId;
            this.placeholder = placeholder;
            this.evenInSource = evenInSource;
        }

        @Override
        public Result<Path> write(
                final Document document,
                final Path destination,
                @Nullable final String sourceLanguage,
                final String targetLanguage) {
            return super.write(damaged(document), destination, sourceLanguage, targetLanguage);
        }

        private Document damaged(final Document document) {
            final Segment target = segment(document, segmentId);
            final String fragment = Objects.requireNonNull(target.placeholders().get(placeholder), "fragment");
            final String inner = Objects.requireNonNull(target.targetInner(), "target");
            if (inner.equals(target.sourceInner()) && !evenInSource) {
                return document;
            }
            final List<Unit> units = document.units().stream()
                    .map(unit -> unit.withSegments(unit.segments().stream()
                            .map(candidate -> candidate.id().equals(segmentId)
                                    ? candidate.withDecision(candidate.status(), inner.replace(fragment, ""))
                                    : candidate)
                            .toList()))
                    .toList();
            return document.withUnits(units);
        }
    }
}
