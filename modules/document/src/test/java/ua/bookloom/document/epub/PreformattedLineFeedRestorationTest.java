package ua.bookloom.document.epub;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.document.fixture.EpubFixtures;
import ua.bookloom.document.model.RawEntry;
import ua.bookloom.document.model.ZipEntryReader;

/**
 * The compounding-mutation trap task 6.1 names, isolated from {@link EpubWriterTest}'s general write-path
 * coverage because it needed a class of its own to stay under the file-length limit: {@code EpubWriter} mutates
 * the tree its registry holds live (F9), so a restore written to prepend a line feed onto that live tree —
 * rather than onto a clone — would add a second line feed on a second {@code write()} call and a third on a
 * third. Calling {@code write()} twice on the <strong>same</strong> opened document, through the same registry,
 * is the only way to observe that: {@link ua.bookloom.document.golden.EpubGoldenRoundTripTest}'s fixed-point
 * sweep opens a fresh document from the first output for its second write, which would not catch this.
 */
class PreformattedLineFeedRestorationTest {

    @TempDir
    private Path tempDir;

    // reassembly without target changes is canonical-equal to the source: writing the same
    // open document twice does not compound the restored leading line feed onto itself.
    @Test
    void write_samePoemDocumentTwice_producesByteIdenticalOutputBothTimes() {
        final Path epub = EpubFixtures.preTwoLeadingLineFeeds(tempDir.resolve("book.epub"));
        final OpenEpubRegistry registry = new OpenEpubRegistry();
        final Document document = new EpubReader(registry).read(epub);
        final EpubWriter writer = new EpubWriter(registry);

        final Path firstOutput = writer.write(document, tempDir.resolve("first.epub"), "uk");
        final Path secondOutput = writer.write(document, tempDir.resolve("second.epub"), "uk");

        assertThat(contentDocumentBytesOf(firstOutput)).isEqualTo(contentDocumentBytesOf(secondOutput));
    }

    private static byte[] contentDocumentBytesOf(Path epub) {
        final List<RawEntry> entries = ZipEntryReader.readAll(readAllBytes(epub));
        for (final RawEntry entry : entries) {
            if (entry.name().equals("OEBPS/c01.xhtml")) {
                return entry.content();
            }
        }
        throw new AssertionError("no OEBPS/c01.xhtml entry in " + epub);
    }

    private static byte[] readAllBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
