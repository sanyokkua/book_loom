package ua.bookloom.util.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A file written whole through a new hidden file beside it. */
class FileWritesTest {

    @TempDir
    private Path dir;

    @Test
    void replace_existingFile_holdsTheNewTextAndLeavesNoHiddenFile() throws IOException {
        final Path target = Files.writeString(dir.resolve("names.csv"), "old");

        FileWrites.replace(target, "new");

        assertThat(Files.readString(target)).isEqualTo("new");
        assertThat(entries()).containsExactly("names.csv");
    }

    // IF the first hidden name were reused, THEN a file of that name that belongs to someone else would be lost.
    @Test
    void reserveBeside_firstNameTaken_createsTheNextAndKeepsTheTakenOne() throws IOException {
        final Path target = dir.resolve("Book.uk.epub");
        final Path taken = Files.writeString(dir.resolve(".bookloom-0-Book.uk.epub"), "someone's");

        final Path reserved = FileWrites.reserveBeside(target);

        assertThat(reserved).isEqualTo(dir.resolve(".bookloom-1-Book.uk.epub"));
        assertThat(Files.readString(taken)).isEqualTo("someone's");
    }

    @Test
    void replace_missingFolder_throwsTheFoldersOwnFailure() {
        final Path target = dir.resolve("gone").resolve("names.csv");

        assertThatThrownBy(() -> FileWrites.replace(target, "x"))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(NoSuchFileException.class);
    }

    private List<String> entries() throws IOException {
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.map(path -> path.getFileName().toString()).toList();
        }
    }
}
