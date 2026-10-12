package ua.bookloom.document.txt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.api.document.MetadataKey;

/** A plain-text book carries no metadata, however much its first lines look like a title page. */
class TxtFactsTest {

    @TempDir
    private Path tempDir;

    // IF a text file opens with lines that read like "date:" and "publisher:", THEN they are prose, not metadata.
    @Test
    void read_textThatLooksLikeATitlePage_hasNoMetadata() throws IOException {
        final Path file = Files.writeString(
                tempDir.resolve("book.txt"),
                "date: 1923\npublisher: Larkspur Press\n\nThe lamp burned low.\n",
                StandardCharsets.UTF_8);

        final Document document = new TxtReader(new OpenTxtRegistry()).read(file);

        assertThat(document.metadata()).doesNotContainKeys(MetadataKey.DATE.key(), MetadataKey.PUBLISHER.key());
        assertThat(document.metadata()).isEmpty();
    }
}
