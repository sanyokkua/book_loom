package ua.bookloom.pipeline;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BookExporterTestSupport.documents;
import static ua.bookloom.pipeline.BookExporterTestSupport.error;
import static ua.bookloom.pipeline.BookExporterTestSupport.errorOf;
import static ua.bookloom.pipeline.BookExporterTestSupport.snapshot;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.DocumentPort;
import ua.bookloom.api.pipeline.TranslationRequest;
import ua.bookloom.pipeline.BookExporterTestSupport.AbsentClosePort;
import ua.bookloom.pipeline.BookExporterTestSupport.CloseFailurePort;
import ua.bookloom.pipeline.BookExporterTestSupport.DestinationCreatingMoveOperation;
import ua.bookloom.pipeline.BookExporterTestSupport.ScriptedMoveOperation;
import ua.bookloom.pipeline.BookExporterTestSupport.SingleCloseFailurePort;
import ua.bookloom.pipeline.BookExporterTestSupport.ThrowingMoveOperation;
import ua.bookloom.pipeline.BookExporterTestSupport.WriteFailurePort;

/** Publication modes, move/close failures, and temporary-path collision guards. */
class BookExporterFilesystemTest {

    @TempDir
    private Path tempDir;

    private DocumentPort documents;

    @BeforeEach
    void setUp() {
        documents = documents();
    }

    // Overwrite uses atomic replacement, then falls back to a plain replacement only for unsupported atomic moves.
    @Test
    void export_atomicMoveUnsupported_fallsBackToReplaceExisting() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "OLD DESTINATION");
        final Document decided = snapshot(documents, source);
        final AtomicMoveNotSupportedException unsupported =
                new AtomicMoveNotSupportedException("temporary", "destination", "test filesystem");
        final ScriptedMoveOperation moves = new ScriptedMoveOperation()
                .answer(Result.err(error(ErrorCode.internal, unsupported)))
                .answer(Result.ok(destination));

        final Result<Path> result =
                new BookExporter(documents, moves).export(request(source, destination, true), decided, () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(Files.readString(destination)).isEqualTo("NEW TRANSLATION");
        assertThat(moves.optionCalls())
                .containsExactly(List.of(ATOMIC_MOVE, REPLACE_EXISTING), List.of(REPLACE_EXISTING));
    }

    // A regular move failure becomes internal with the original cause and the temporary output is removed.
    @Test
    void export_ordinaryMoveFailure_returnsInternalWithCauseAndCleansTemporary() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "OLD DESTINATION");
        final Path temporary = tempDir.resolve(".Book.uk.md");
        final Document decided = snapshot(documents, source);
        final IOException moveFailure = new IOException("scripted move failure");
        final ScriptedMoveOperation moves =
                new ScriptedMoveOperation().answer(Result.err(error(ErrorCode.internal, moveFailure)));

        final Result<Path> result =
                new BookExporter(documents, moves).export(request(source, destination, true), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.internal);
        assertThat(errorOf(result).cause()).isSameAs(moveFailure);
        assertThat(Files.readString(destination)).isEqualTo("OLD DESTINATION");
        assertThat(Files.exists(temporary)).isFalse();
    }

    // With overwrite off an existing destination is a validation conflict and is never replaced.
    @Test
    void export_overwriteDisabledWithExistingDestination_returnsValidationAndPreservesDestination() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "OLD DESTINATION");
        final Document decided = snapshot(documents, source);

        final Result<Path> result =
                new BookExporter(documents).export(request(source, destination, false), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(errorOf(result).cause()).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(destination)).isEqualTo("OLD DESTINATION");
        assertThat(Files.exists(tempDir.resolve(".Book.uk.md"))).isFalse();
    }

    // With overwrite on a validated temporary book atomically replaces the previous destination.
    @Test
    void export_overwriteEnabledWithExistingDestination_replacesDestination() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "OLD DESTINATION");
        final Document decided = snapshot(documents, source);

        final Result<Path> result =
                new BookExporter(documents).export(request(source, destination, true), decided, () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(Files.readString(destination)).isEqualTo("NEW TRANSLATION");
        assertThat(Files.exists(tempDir.resolve(".Book.uk.md"))).isFalse();
    }

    // A destination appearing at publication time is still a validation conflict when overwrite is disabled.
    @Test
    void export_destinationCreatedBeforePublication_returnsValidationAndPreservesLateFile() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = tempDir.resolve("Book.uk.md");
        final Document decided = snapshot(documents, source);
        final DestinationCreatingMoveOperation lateConflict = new DestinationCreatingMoveOperation("LATE DESTINATION");

        final Result<Path> result = new BookExporter(documents, lateConflict)
                .export(request(source, destination, false), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(destination)).isEqualTo("LATE DESTINATION");
        assertThat(Files.exists(tempDir.resolve(".Book.uk.md"))).isFalse();
        assertThat(lateConflict.optionCalls()).containsExactly(List.of());
    }

    // Both close operations are attempted; the first standalone close error blocks publication and remains primary.
    @Test
    void export_bothClosesFail_preservesFirstErrorAndPreventsPublication() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "OLD DESTINATION");
        final Document decided = snapshot(documents, source);
        final AppError first = error(ErrorCode.internal, new IOException("first close"));
        final AppError second = error(ErrorCode.internal, new IOException("second close"));
        final CloseFailurePort port = new CloseFailurePort(documents, first, second);

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination, true), decided, () -> false);

        assertThat(result.error()).isSameAs(first);
        assertThat(port.closedDocuments()).hasSize(2);
        assertThat(Files.readString(destination)).isEqualTo("OLD DESTINATION");
        assertThat(Files.exists(tempDir.resolve(".Book.uk.md"))).isFalse();
    }

    // A lone source or temporary close error blocks publication while the other close is still attempted.
    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void export_oneRequiredCloseFails_preservesThatErrorAndAttemptsBothCloses(final int failingIndex) throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "OLD DESTINATION");
        final Document decided = snapshot(documents, source);
        final AppError closeFailure = error(ErrorCode.internal, new IOException("scripted close"));
        final SingleCloseFailurePort port = new SingleCloseFailurePort(documents, failingIndex, closeFailure);

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination, true), decided, () -> false);

        assertThat(result.error()).isSameAs(closeFailure);
        assertThat(port.closedDocuments()).hasSize(2);
        assertThat(port.openedDocuments())
                .allSatisfy(
                        document -> assertThat(documents.close(document).data()).isFalse());
        assertThat(Files.readString(destination)).isEqualTo("OLD DESTINATION");
        assertThat(Files.exists(tempDir.resolve(".Book.uk.md"))).isFalse();
    }

    // Result.ok(false) still means close succeeded and does not prevent publication.
    @Test
    void export_closeReportsAlreadyAbsent_stillPublishesValidatedBook() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = tempDir.resolve("Book.uk.md");
        final Document decided = snapshot(documents, source);
        final AbsentClosePort port = new AbsentClosePort(documents);

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination, true), decided, () -> false);

        assertThat(result.data()).isEqualTo(destination);
        assertThat(port.closedDocuments()).hasSize(2);
        assertThat(Files.readString(destination)).isEqualTo("NEW TRANSLATION");
    }

    // A runtime fault at the move seam is wrapped as internal with its original cause and cleaned independently.
    @Test
    void export_moveOperationThrows_returnsInternalWithCauseAndCleansTemporary() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "OLD DESTINATION");
        final IllegalStateException failure = new IllegalStateException("scripted unexpected fault");
        final Document decided = snapshot(documents, source);

        final Result<Path> result = new BookExporter(documents, new ThrowingMoveOperation(failure))
                .export(request(source, destination, true), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.internal);
        assertThat(errorOf(result).cause()).isSameAs(failure);
        assertThat(Files.readString(destination)).isEqualTo("OLD DESTINATION");
        assertThat(Files.exists(tempDir.resolve(".Book.uk.md"))).isFalse();
    }

    // A write error stays primary when source close and deletion of a non-empty temporary directory also fail.
    @Test
    void export_writeCloseAndCleanupFail_preservesWriteError() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "NEW TRANSLATION");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "OLD DESTINATION");
        final Path temporary = tempDir.resolve(".Book.uk.md");
        assertThat(Files.createDirectory(temporary)).isEqualTo(temporary);
        assertThat(Files.writeString(temporary.resolve("kept.txt"), "kept")).exists();
        final Document decided = snapshot(documents, source);
        final AppError writeFailure = error(ErrorCode.validation, null);
        final WriteFailurePort writePort = new WriteFailurePort(documents, writeFailure);
        final CloseFailurePort port =
                new CloseFailurePort(writePort, error(ErrorCode.internal, null), error(ErrorCode.internal, null));

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination, true), decided, () -> false);

        assertThat(result.error()).isSameAs(writeFailure);
        assertThat(port.closedDocuments()).hasSize(1);
        assertThat(Files.readString(destination)).isEqualTo("OLD DESTINATION");
        assertThat(temporary.resolve("kept.txt")).exists();
    }

    // A temporary path equal to the source is rejected before either source or destination bytes are touched.
    @Test
    void export_temporaryPathEqualsSource_returnsValidationWithoutTouchingEitherBook() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve(".Book.md"), "SOURCE BYTES");
        final Path destination = Files.writeString(tempDir.resolve("Book.md"), "DESTINATION BYTES");
        final Document decided = snapshot(documents, source);
        final BookExporterTestSupport.RecordingDocumentPort port =
                new BookExporterTestSupport.RecordingDocumentPort(documents);

        final Result<Path> result =
                new BookExporter(port).export(request(source, destination, true), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(source)).isEqualTo("SOURCE BYTES");
        assertThat(Files.readString(destination)).isEqualTo("DESTINATION BYTES");
        assertThat(port.writtenDocuments()).isEmpty();
        assertThat(port.writeDestinations()).isEmpty();
    }

    // A temporary symlink to the destination is rejected before it can be followed, replaced, or cleaned up.
    @Test
    void export_temporaryPathAliasesDestination_returnsValidationWithoutTouchingEitherBook() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "SOURCE BYTES");
        final Path destination = Files.writeString(tempDir.resolve("Book.uk.md"), "DESTINATION BYTES");
        final Path temporary = Files.createSymbolicLink(tempDir.resolve(".Book.uk.md"), destination.getFileName());
        final Document decided = snapshot(documents, source);

        final Result<Path> result =
                new BookExporter(documents).export(request(source, destination, true), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(source)).isEqualTo("SOURCE BYTES");
        assertThat(Files.readString(destination)).isEqualTo("DESTINATION BYTES");
        assertThat(Files.isSymbolicLink(temporary)).isTrue();
    }

    // A dangling temporary symlink to a destination is still occupied and refused before a writer can create it.
    @Test
    void export_danglingTemporarySymlinkToDestination_returnsValidationWithoutCreatingDestination() throws Exception {
        final Path source = TestBooks.markdown(tempDir.resolve("Book.md"), "SOURCE BYTES");
        final Path destination = tempDir.resolve("Book.uk.md");
        final Path temporary = Files.createSymbolicLink(tempDir.resolve(".Book.uk.md"), destination.getFileName());
        final Document decided = snapshot(documents, source);

        final Result<Path> result =
                new BookExporter(documents).export(request(source, destination, true), decided, () -> false);

        assertThat(errorOf(result).code()).isEqualTo(ErrorCode.validation);
        assertThat(Files.readString(source)).isEqualTo("SOURCE BYTES");
        assertThat(Files.exists(destination)).isFalse();
        assertThat(Files.isSymbolicLink(temporary)).isTrue();
    }

    private static TranslationRequest request(final Path source, final Path destination, final boolean overwrite) {
        return new TranslationRequest(source, destination, "uk", "en", overwrite);
    }
}
