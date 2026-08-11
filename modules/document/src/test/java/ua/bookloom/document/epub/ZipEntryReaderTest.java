package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.document.model.CorruptContainerException;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.ZipEntryReader;

/**
 * {@code ZipEntryReader}'s entry capture (task 2.3).
 */
class ZipEntryReaderTest {

    @TempDir
    private Path tempDir;

    @Test
    void readAll_validZip_capturesEntriesInPhysicalOrder() {
        final Path zip = tempDir.resolve("book.epub");
        new EpubZipBuilder()
                .entry("mimetype", "application/epub+zip")
                .entry("second.txt", "b")
                .writeTo(zip);
        final byte[] bytes = readBytes(zip);

        final List<RawEntry> entries = ZipEntryReader.readAll(bytes);

        assertThat(entries).extracting(RawEntry::name).containsExactly("mimetype", "second.txt");
        assertThat(entries).extracting(RawEntry::order).containsExactly(0, 1);
        assertThat(new String(entries.get(0).content(), StandardCharsets.UTF_8)).isEqualTo("application/epub+zip");
    }

    @Test
    void readAll_notAZipFile_isACorruptContainerFailure() {
        final byte[] plainText = "this is not a zip file".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> ZipEntryReader.readAll(plainText)).isInstanceOf(CorruptContainerException.class);
    }

    private static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
