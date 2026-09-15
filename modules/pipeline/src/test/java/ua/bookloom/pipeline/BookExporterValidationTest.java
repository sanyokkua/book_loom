package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BookExporterTestSupport.documents;
import static ua.bookloom.pipeline.BookExporterTestSupport.error;
import static ua.bookloom.pipeline.BookExporterTestSupport.errorOf;
import static ua.bookloom.pipeline.BookExporterTestSupport.onlyDecision;
import static ua.bookloom.pipeline.BookExporterTestSupport.opened;
import static ua.bookloom.pipeline.BookExporterTestSupport.snapshot;
import static ua.bookloom.pipeline.BookExporterTestSupport.zipEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.pipeline.BookExporterTestSupport.CloseFailurePort;
import ua.bookloom.pipeline.BookExporterTestSupport.CountMismatchPort;
import ua.bookloom.pipeline.BookExporterTestSupport.OpenFailurePort;
import ua.bookloom.pipeline.BookExporterTestSupport.RecordingDocumentPort;
import ua.bookloom.pipeline.BookExporterTestSupport.TemporaryOpenFailurePort;

/** Written-book validation, source-change detection, cleanup, and retry behavior. */
class BookExporterValidationTest {

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = documents();
    }

    // A temporary book with fewer segments is refused, both opened documents are released, and the old book stays.
    @Test
    void export_temporarySegmentCountMismatch_preservesDestinationReleasesDocumentsAndCleansTemporary()
            throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "One.\n\nTwo.\n\nThree.");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "OLD DESTINATION");
        final Path temporary = tempDir.resolve(".Book.uk.md");
        final Document decided = snapshot(documents, source);
        final CountMismatchPort port = new CountMismatchPort(documents, temporary::equals);

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination, true), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(destination)).isEqualTo("OLD DESTINATION");
        assertThat(Files.exists(temporary)).isFalse();
        assertThat(port.openedDocuments()).hasSize(2);
        assertThat(port.closedDocuments()).hasSize(2);
        assertThat(port.openedDocuments())
                .allSatisfy(
                        document -> assertThat(documents.close(document).data()).isFalse());
    }

    // A failed reopen propagates the port error, releases the source, and removes the fully written temporary book.
    @Test
    void export_temporaryReopenFails_propagatesErrorReleasesSourceAndCleansTemporary() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Hello.");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "OLD DESTINATION");
        final Path temporary = tempDir.resolve(".Book.uk.md");
        final Document decided = snapshot(documents, source);
        final AppError reopenFailure = error(ErrorCode.validation, null);
        final TemporaryOpenFailurePort port = new TemporaryOpenFailurePort(documents, temporary::equals, reopenFailure);

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination, true), decided, () -> false);

        assertThat(result.error()).isSameAs(reopenFailure);
        assertThat(Files.readString(destination)).isEqualTo("OLD DESTINATION");
        assertThat(Files.exists(temporary)).isFalse();
        assertThat(port.openedDocuments()).singleElement().satisfies(document -> {
            assertThat(port.closedDocuments()).containsExactly(document);
            assertThat(documents.close(document).data()).isFalse();
        });
    }

    // The temporary reopen error remains the exact primary error when the later source close also fails.
    @Test
    void export_temporaryReopenAndSourceCloseFail_preservesReopenError() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Hello.");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "OLD DESTINATION");
        final Path temporary = tempDir.resolve(".Book.uk.md");
        final Document decided = snapshot(documents, source);
        final AppError reopenFailure = error(ErrorCode.validation, null);
        final TemporaryOpenFailurePort reopenPort =
                new TemporaryOpenFailurePort(documents, temporary::equals, reopenFailure);
        final CloseFailurePort port =
                new CloseFailurePort(reopenPort, error(ErrorCode.internal, null), error(ErrorCode.internal, null));

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination, true), decided, () -> false);

        assertThat(result.error()).isSameAs(reopenFailure);
        assertThat(port.closedDocuments()).hasSize(1);
        assertThat(Files.readString(destination)).isEqualTo("OLD DESTINATION");
        assertThat(Files.exists(temporary)).isFalse();
    }

    // A source-open port failure is propagated unchanged and a stale non-colliding temporary entry is removed.
    @Test
    void export_sourceOpenFails_preservesPortErrorAndCleansTemporary() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Hello.");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "OLD DESTINATION");
        final Path temporary = Files.writeString(tempDir.resolve(".Book.uk.md"), "STALE TEMPORARY");
        final Document decided = snapshot(documents, source);
        final AppError openFailure = error(ErrorCode.validation, null);

        final Result<Path> result = new BookExporter(new OpenFailurePort(documents, openFailure))
                .export(request(source, destination, true), decided, () -> false);

        assertThat(result.error()).isSameAs(openFailure);
        assertThat(Files.readString(destination)).isEqualTo("OLD DESTINATION");
        assertThat(Files.exists(temporary)).isFalse();
    }

    // A changed source is detected by its content hash before any temporary or destination write.
    @Test
    void export_sourceBytesChanged_returnsValidationWithoutWriting() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "Original.");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "OLD DESTINATION");
        final Document decided = snapshot(documents, source);
        Files.writeString(source, "Changed.");
        final RecordingDocumentPort port = new RecordingDocumentPort(documents);

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination, true), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(destination)).isEqualTo("OLD DESTINATION");
        assertThat(Files.exists(tempDir.resolve(".Book.uk.md"))).isFalse();
        assertThat(port.openedDocuments()).singleElement().satisfies(document -> {
            assertThat(port.closedDocuments()).containsExactly(document);
            assertThat(documents.close(document).data()).isFalse();
        });
    }

    // A missing destination folder lets the real writer classify the failure and leaves no output behind.
    @Test
    void export_destinationDirectoryDeleted_propagatesWriteErrorAndLeavesNothing() {
        final Path sourceDir = tempDir.resolve("source");
        final Path destinationDir = tempDir.resolve("destination");
        assertThat(sourceDir.toFile().mkdir()).isTrue();
        assertThat(destinationDir.toFile().mkdir()).isTrue();
        final Path source = TestBooks.markdown(sourceDir.resolve("Book.md"), "Hello.");
        final Path destination = destinationDir.resolve("Book.uk.md");
        final Document decided = snapshot(documents, source);
        assertThat(destinationDir.toFile().delete()).isTrue();
        final RecordingDocumentPort port = new RecordingDocumentPort(documents);

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination, true), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.internal);
        assertThat(errorOf(result).cause()).isNotNull();
        assertThat(Files.exists(destination)).isFalse();
        assertThat(Files.exists(destinationDir.resolve(".Book.uk.md"))).isFalse();
        assertThat(port.openedDocuments()).singleElement().satisfies(document -> {
            assertThat(port.closedDocuments()).containsExactly(document);
            assertThat(documents.close(document).data()).isFalse();
        });
    }

    // Cancellation observes both books already closed; the same accepted EPUB snapshot then retries without mutation.
    @Test
    void export_cancelledAcceptedEpubAfterClosure_reusesSnapshotOnFreshSuccessfulRetry() throws Exception {
        final Path source =
                TestBooks.epub(tempDir.resolve("Book.epub"), List.of(List.of("Tom &amp; <i>Jerry</i> ran.")), "en");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.epub"), "OLD DESTINATION");
        final Document decided = acceptedEpubDecision(source);
        final RecordingDocumentPort firstPort = new RecordingDocumentPort(documents);
        final RecordingDocumentPort retryPort = new RecordingDocumentPort(documents);

        final Result<Path> cancelled = new BookExporter(firstPort)
                .export(request(source, destination, true), decided, cancelAfterClosure(firstPort));
        assertCancelledAttempt(cancelled, destination, decided, firstPort);

        final Result<Path> retried =
                new BookExporter(retryPort).export(request(source, destination, true), decided, () -> false);
        assertSuccessfulRetry(retried, destination, decided, firstPort, retryPort);
    }

    private Document acceptedEpubDecision(final Path source) {
        final Document original = opened(documents, source);
        final Segment segment = original.units().getFirst().segments().getFirst();
        final Result<String> restored = documents.unmask(original.format(), segment, "TOM & ⟦g0⟧JERRY⟦g1⟧ RAN.");
        final Document decided = onlyDecision(
                original, SegmentStatus.ACCEPTED, Objects.requireNonNull(restored.data(), "restored target"));
        assertThat(documents.close(original).data()).isTrue();
        return decided;
    }

    private BooleanSupplier cancelAfterClosure(final RecordingDocumentPort port) {
        return () -> {
            assertThat(port.openedDocuments()).hasSize(2);
            assertThat(port.closedDocuments()).containsExactlyElementsOf(port.openedDocuments());
            assertThat(port.openedDocuments())
                    .allSatisfy(document ->
                            assertThat(documents.close(document).data()).isFalse());
            return true;
        };
    }

    private void assertCancelledAttempt(
            final Result<Path> cancelled,
            final Path destination,
            final Document decided,
            final RecordingDocumentPort port)
            throws Exception {
        assertThat(errorOf(cancelled).code()).isEqualTo(ErrorCode.cancelled);
        assertThat(Files.readString(destination)).isEqualTo("OLD DESTINATION");
        assertThat(Files.exists(tempDir.resolve(".Book.uk.epub"))).isFalse();
        assertThat(port.openedDocuments()).extracting(Document::id).doesNotContain(decided.id());
        assertThat(port.writtenDocuments())
                .singleElement()
                .satisfies(written -> assertThat(
                                written.units().getFirst().segments().getFirst().status())
                        .isEqualTo(SegmentStatus.ACCEPTED));
    }

    private void assertSuccessfulRetry(
            final Result<Path> retried,
            final Path destination,
            final Document decided,
            final RecordingDocumentPort firstPort,
            final RecordingDocumentPort retryPort) {
        assertThat(retried.data()).isEqualTo(destination);
        assertThat(retryPort.openedDocuments())
                .extracting(Document::id)
                .doesNotContainAnyElementsOf(
                        firstPort.openedDocuments().stream().map(Document::id).toList())
                .doesNotContain(decided.id());
        assertThat(retryPort.writtenDocuments())
                .singleElement()
                .satisfies(written -> assertThat(
                                written.units().getFirst().segments().getFirst().targetInner())
                        .isEqualTo("TOM &amp; <i>JERRY</i> RAN."));
        assertThat(zipEntry(destination, "OEBPS/ch0.xhtml")).contains("<p>TOM &amp; <i>JERRY</i> RAN.</p>");
        assertThat(zipEntry(destination, "OEBPS/content.opf"))
                .contains("<dc:language>uk</dc:language>")
                .doesNotContain("<dc:language>en</dc:language>");
        assertThat(Files.exists(tempDir.resolve(".Book.uk.epub"))).isFalse();
    }

    private static TranslationRequest request(final Path source, final Path destination, final boolean overwrite) {
        return new TranslationRequest(source, destination, "uk", "en", overwrite);
    }
}
