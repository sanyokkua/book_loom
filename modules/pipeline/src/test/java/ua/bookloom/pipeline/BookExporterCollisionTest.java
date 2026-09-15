package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BookExporterTestSupport.documents;
import static ua.bookloom.pipeline.BookExporterTestSupport.errorOf;
import static ua.bookloom.pipeline.BookExporterTestSupport.snapshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.pipeline.BookExporterTestSupport.RecordingDocumentPort;

/** Collision preflight and fixed hidden-name behavior. */
class BookExporterCollisionTest {

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = documents();
    }

    // A hard-linked temporary path aliases the source and is preserved with both books untouched.
    @Test
    void export_temporaryHardLinkToSource_returnsValidationAndPreservesEveryEntry() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "SOURCE BYTES");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "DESTINATION BYTES");
        final Path temporary = Files.createLink(tempDir.resolve(".Book.uk.md"), source);
        final Document decided = snapshot(documents, source);
        final RecordingDocumentPort port = new RecordingDocumentPort(documents);

        final Result<Path> result = new BookExporter(port).export(request(source, destination), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(source)).isEqualTo("SOURCE BYTES");
        assertThat(Files.readString(destination)).isEqualTo("DESTINATION BYTES");
        assertThat(Files.readString(temporary)).isEqualTo("SOURCE BYTES");
        assertThat(Files.isSameFile(temporary, source)).isTrue();
        assertThat(port.writtenDocuments()).isEmpty();
        assertThat(port.writeDestinations()).isEmpty();
    }

    // A hard-linked temporary path aliases the destination and is preserved with both books untouched.
    @Test
    void export_temporaryHardLinkToDestination_returnsValidationAndPreservesEveryEntry() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "SOURCE BYTES");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "DESTINATION BYTES");
        final Path temporary = Files.createLink(tempDir.resolve(".Book.uk.md"), destination);
        final Document decided = snapshot(documents, source);

        final Result<Path> result =
                new BookExporter(documents).export(request(source, destination), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(source)).isEqualTo("SOURCE BYTES");
        assertThat(Files.readString(destination)).isEqualTo("DESTINATION BYTES");
        assertThat(Files.readString(temporary)).isEqualTo("DESTINATION BYTES");
        assertThat(Files.isSameFile(temporary, destination)).isTrue();
    }

    // A symlinked parent spelling still resolves the hidden temporary path to the source and is refused.
    @Test
    void export_symlinkedParentMakesTemporaryAliasSource_returnsValidationWithoutChanges() throws Exception {
        final Path real = Files.createDirectory(tempDir.resolve("real"));
        final Path alias = Files.createSymbolicLink(tempDir.resolve("alias"), real.getFileName());
        final Path source = TestBooks.markdown(real.resolve(".Book.md"), "SOURCE BYTES");
        final Path destination = Files.writeString(alias.resolve("Book.md"), "DESTINATION BYTES");
        final Document decided = snapshot(documents, source);
        final RecordingDocumentPort port = new RecordingDocumentPort(documents);

        final Result<Path> result = new BookExporter(port).export(request(source, destination), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(source)).isEqualTo("SOURCE BYTES");
        assertThat(Files.readString(destination)).isEqualTo("DESTINATION BYTES");
        assertThat(Files.isSymbolicLink(alias)).isTrue();
        assertThat(port.writtenDocuments()).isEmpty();
        assertThat(port.writeDestinations()).isEmpty();
    }

    // A destination chain ending at the absent temporary path is refused before opening or writing.
    @Test
    void export_destinationSymlinkChainToAbsentTemporary_returnsValidationBeforeDocumentAccess() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "SOURCE BYTES");
        final Path temporary = tempDir.resolve(".Book.uk.md");
        final Path hop = Files.createSymbolicLink(tempDir.resolve("hop.md"), temporary.getFileName());
        final Path destination = Files.createSymbolicLink(tempDir.resolve("Book.uk.md"), hop.getFileName());
        final Document decided = snapshot(documents, source);
        final RecordingDocumentPort port = new RecordingDocumentPort(documents);

        final Result<Path> result = new BookExporter(port).export(request(source, destination), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(source)).isEqualTo("SOURCE BYTES");
        assertThat(Files.readSymbolicLink(destination)).isEqualTo(hop.getFileName());
        assertThat(Files.readSymbolicLink(hop)).isEqualTo(temporary.getFileName());
        assertThat(Files.notExists(temporary)).isTrue();
        assertThat(port.openedDocuments()).isEmpty();
        assertThat(port.writtenDocuments()).isEmpty();
        assertThat(port.writeDestinations()).isEmpty();
    }

    // Filesystem resolution of a symlinked parent and '..' still identifies the absent temporary target.
    @Test
    void export_destinationTargetThroughSymlinkedParent_returnsValidationBeforeDocumentAccess() throws Exception {
        final Path real = Files.createDirectory(tempDir.resolve("real"));
        Files.createDirectory(real.resolve("nested"));
        final Path alias = Files.createSymbolicLink(tempDir.resolve("alias"), Path.of("real/nested"));
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "SOURCE BYTES");
        final Path temporary = real.resolve(".Book.uk.md");
        final Path linkTarget = Path.of("../alias/../.Book.uk.md");
        final Path destination = Files.createSymbolicLink(real.resolve("Book.uk.md"), linkTarget);
        final Document decided = snapshot(documents, source);
        final RecordingDocumentPort port = new RecordingDocumentPort(documents);

        final Result<Path> result = new BookExporter(port).export(request(source, destination), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(source)).isEqualTo("SOURCE BYTES");
        assertThat(Files.readSymbolicLink(destination)).isEqualTo(linkTarget);
        assertThat(Files.readSymbolicLink(alias)).isEqualTo(Path.of("real/nested"));
        assertThat(Files.notExists(temporary)).isTrue();
        assertThat(port.openedDocuments()).isEmpty();
        assertThat(port.writtenDocuments()).isEmpty();
        assertThat(port.writeDestinations()).isEmpty();
    }

    // A target that only lexically collapses onto the temporary name follows filesystem link semantics instead.
    @Test
    void export_nonAliasingTargetWithSymlinkBeforeParentTraversal_publishesNormally() throws Exception {
        final Path real = Files.createDirectory(tempDir.resolve("real"));
        final Path elsewhere = Files.createDirectory(tempDir.resolve("elsewhere"));
        Files.createDirectory(elsewhere.resolve("nested"));
        final Path alias = Files.createSymbolicLink(real.resolve("alias"), Path.of("../elsewhere/nested"));
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = Files.createSymbolicLink(real.resolve("Book.uk.md"), Path.of("alias/../.Book.uk.md"));
        final Document decided = snapshot(documents, source);

        final Result<Path> result =
                new BookExporter(documents).export(request(source, destination), decided, () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(Files.readString(source)).isEqualTo("NEW TRANSLATION");
        assertThat(Files.readString(destination)).isEqualTo("NEW TRANSLATION");
        assertThat(Files.isSymbolicLink(destination)).isFalse();
        assertThat(Files.readSymbolicLink(alias)).isEqualTo(Path.of("../elsewhere/nested"));
        assertThat(Files.notExists(elsewhere.resolve(".Book.uk.md"))).isTrue();
        assertThat(Files.notExists(real.resolve(".Book.uk.md"))).isTrue();
    }

    // A destination-only link cycle is unresolved, not a temporary alias, and normal overwrite publication replaces it.
    @Test
    void export_nonAliasingDestinationSymlinkCycle_publishesNormally() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = tempDir.resolve("Book.uk.md");
        final Path hop = Files.createSymbolicLink(tempDir.resolve("hop.md"), destination.getFileName());
        Files.createSymbolicLink(destination, hop.getFileName());
        final Document decided = snapshot(documents, source);

        final Result<Path> result =
                new BookExporter(documents).export(request(source, destination), decided, () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(Files.readString(source)).isEqualTo("NEW TRANSLATION");
        assertThat(Files.readString(destination)).isEqualTo("NEW TRANSLATION");
        assertThat(Files.isSymbolicLink(destination)).isFalse();
        assertThat(Files.readSymbolicLink(hop)).isEqualTo(destination.getFileName());
        assertThat(Files.notExists(tempDir.resolve(".Book.uk.md"))).isTrue();
    }

    // A stale ordinary hidden file is safe to overwrite and does not block a later successful attempt.
    @Test
    void export_nonAliasingStaleTemporaryFile_overwritesItAndPublishes() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = tempDir.resolve("Book.uk.md");
        final Path temporary = Files.writeString(tempDir.resolve(".Book.uk.md"), "STALE TEMPORARY");
        final Document decided = snapshot(documents, source);

        final Result<Path> result =
                new BookExporter(documents).export(request(source, destination), decided, () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(Files.readString(destination)).isEqualTo("NEW TRANSLATION");
        assertThat(Files.exists(temporary)).isFalse();
    }

    // The fixed hidden name retains the compound FB2 ZIP suffix needed by the real document resolver.
    @Test
    void export_zippedFb2Destination_writesExactCompoundSuffixTemporaryPath() {
        final Path source = TestBooks.zippedFb2(tempDir.resolve("Book.fb2.zip"), List.of("Hello."), "en");
        final Path destination = tempDir.resolve("Book.uk.fb2.zip");
        final Document decided = snapshot(documents, source);
        final RecordingDocumentPort port = new RecordingDocumentPort(documents);

        final Result<Path> result = new BookExporter(port).export(request(source, destination), decided, () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(port.writeDestinations()).containsExactly(tempDir.resolve(".Book.uk.fb2.zip"));
        assertThat(Files.exists(tempDir.resolve(".Book.uk.fb2.zip"))).isFalse();
    }

    private static TranslationRequest request(final Path source, final Path destination) {
        return new TranslationRequest(source, destination, "uk", "en", true);
    }
}
