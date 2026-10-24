package ua.bookloom.pipeline.export;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.TestDocuments.documents;
import static ua.bookloom.pipeline.export.BookExporterTestSupport.snapshot;
import static ua.bookloom.pipeline.export.BookExporterTestSupport.sourceOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.pipeline.TestBooks;
import ua.bookloom.pipeline.export.BookExporterTestSupport.RecordingDocumentPort;

/**
 * The temporary book is always a new file under a name nothing else uses, so whatever already stands beside the
 * destination — a person's hidden file, a hard link to a book, a link chain — is never written over or deleted.
 */
class BookExporterCollisionTest {

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = documents();
    }

    // IF the old fixed hidden name were still used, THEN a hard link there to the source would be written through.
    @Test
    void export_hiddenHardLinkToSource_isLeftAloneAndTheBookPublishes() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "SOURCE BYTES");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "DESTINATION BYTES");
        final Path hidden = Files.createLink(tempDir.resolve(".Book.uk.md"), source);
        final Document decided = snapshot(documents, source);

        final Result<Path> result =
                new BookExporter(documents).export(request(source, destination), sourceOrder(decided), () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(Files.readString(source)).isEqualTo("SOURCE BYTES");
        assertThat(Files.isSameFile(hidden, source)).isTrue();
    }

    // IF a stale or foreign hidden file were overwritten or cleaned up, THEN the export would destroy a file it never
    // made.
    @Test
    void export_staleHiddenFileOfTheOldName_isKeptAsItWas() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = tempDir.resolve("Book.uk.md");
        final Path hidden = Files.writeString(tempDir.resolve(".Book.uk.md"), "SOMEONE'S HIDDEN FILE");
        final Document decided = snapshot(documents, source);

        final Result<Path> result =
                new BookExporter(documents).export(request(source, destination), sourceOrder(decided), () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(Files.readString(destination)).isEqualTo("NEW TRANSLATION");
        assertThat(Files.readString(hidden)).isEqualTo("SOMEONE'S HIDDEN FILE");
    }

    // IF a taken temporary name were reused, THEN two exports into one folder could write into each other's file.
    @Test
    void export_firstTemporaryNameTaken_writesThroughTheNextAndKeepsTheTakenOne() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = tempDir.resolve("Book.uk.md");
        final Path taken = Files.writeString(tempDir.resolve(".bookloom-0-Book.uk.md"), "ANOTHER EXPORT");
        final Document decided = snapshot(documents, source);
        final RecordingDocumentPort port = new RecordingDocumentPort(documents);

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination), sourceOrder(decided), () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(port.writeDestinations()).containsExactly(tempDir.resolve(".bookloom-1-Book.uk.md"));
        assertThat(Files.readString(taken)).isEqualTo("ANOTHER EXPORT");
        assertThat(Files.exists(tempDir.resolve(".bookloom-1-Book.uk.md"))).isFalse();
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
                new BookExporter(documents).export(request(source, destination), sourceOrder(decided), () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(Files.readString(destination)).isEqualTo("NEW TRANSLATION");
        assertThat(Files.isSymbolicLink(destination)).isFalse();
        assertThat(Files.readSymbolicLink(hop)).isEqualTo(destination.getFileName());
    }

    // The temporary name ends with the destination's own, so the compound FB2 ZIP suffix still names the format.
    @Test
    void export_zippedFb2Destination_writesThroughATemporaryWithTheCompoundSuffix() {
        final Path source = TestBooks.zippedFb2(tempDir.resolve("Book.fb2.zip"), List.of("Hello."), "en");
        final Path destination = tempDir.resolve("Book.uk.fb2.zip");
        final Document decided = snapshot(documents, source);
        final RecordingDocumentPort port = new RecordingDocumentPort(documents);

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination), sourceOrder(decided), () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(port.writeDestinations()).containsExactly(tempDir.resolve(".bookloom-0-Book.uk.fb2.zip"));
        assertThat(Files.exists(tempDir.resolve(".bookloom-0-Book.uk.fb2.zip"))).isFalse();
    }

    private static ExportPlan request(final Path source, final Path destination) {
        return new ExportPlan(source, destination, null, "uk", true);
    }
}
