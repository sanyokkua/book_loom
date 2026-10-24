package ua.bookloom.util.files;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;

/** A failed write, told by what the person can do about it. */
class FileFailuresTest {

    static Stream<Arguments> recognised() {
        return Stream.of(
                Arguments.of(new NoSuchFileException("/a/b/book.epub"), FailureReason.FOLDER_MISSING),
                Arguments.of(new AccessDeniedException("/a/book.epub"), FailureReason.ACCESS_DENIED),
                Arguments.of(
                        new FileSystemException("/a/book.epub", null, "Operation not permitted"),
                        FailureReason.ACCESS_DENIED),
                Arguments.of(
                        new FileSystemException("/Volumes/CD/book.epub", null, "Read-only file system"),
                        FailureReason.READ_ONLY),
                Arguments.of(new IOException("No space left on device"), FailureReason.DISK_FULL),
                Arguments.of(new IOException("There is not enough space on the disk"), FailureReason.DISK_FULL),
                Arguments.of(
                        new UncheckedIOException(
                                "Unable to write EPUB output", new IOException("No space left on device")),
                        FailureReason.DISK_FULL));
    }

    @ParameterizedTest
    @MethodSource("recognised")
    void reasonOf_aWriteFailureAPersonCanFix_isRecognised(final Throwable failure, final FailureReason expected) {
        assertThat(FileFailures.reasonOf(failure)).contains(expected);
    }

    // IF any I/O failure were called a full disk, THEN the advice would send the person looking in the wrong place.
    @Test
    void reasonOf_anUnrelatedFailure_isNotGuessed() {
        assertThat(FileFailures.reasonOf(new IOException("Stream closed"))).isEmpty();
    }

    // The real case, not a constructed exception: writing into a folder that is not there.
    @Test
    void reasonOf_writingIntoAMissingFolder_isFolderMissing(@TempDir final Path dir) {
        final Path target = dir.resolve("gone").resolve("book.txt");
        final Throwable thrown = writeFailure(target);

        assertThat(FileFailures.reasonOf(thrown)).contains(FailureReason.FOLDER_MISSING);
    }

    // IF the folder's path reached the details, THEN a dialog would show the person's home folder.
    @Test
    void writeError_diskFull_isValidationWithAdviceAndOnlyTheFoldersName() {
        final AppError error = FileFailures.writeError(
                FailureReason.DISK_FULL, Path.of("/Users/someone/Books"), new IOException("No space left on device"));

        assertThat(error.code()).isEqualTo(ErrorCode.validation);
        assertThat(error.message()).contains("free space");
        assertThat(error.details()).isEqualTo("reason=disk-full, folder=Books");
    }

    private static Throwable writeFailure(final Path target) {
        try {
            Files.writeString(target, "x");
            return new AssertionError("the write succeeded");
        } catch (IOException failure) {
            return failure;
        }
    }
}
