package ua.bookloom.document.md;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ua.bookloom.api.document.Document;
import ua.bookloom.document.fixture.TargetedDocuments;

/** A Markdown export the source encoding cannot hold is written whole as UTF-8, never as {@code ?} (task 5.5). */
class MarkdownEncodingSwitchTest {

    @TempDir
    private Path tempDir;

    private static final Charset LATIN_1 = StandardCharsets.ISO_8859_1;

    private static final String SOURCE = "# Le café\n\nLe café du coin était fermé ce matin-là, et personne ne savait "
            + "pourquoi. Les élèves attendaient devant la porte, sous la pluie fine.\n";

    private final OpenMarkdownRegistry registry = new OpenMarkdownRegistry();

    // WHEN a Latin-1 Markdown file is exported with a target holding a character Latin-1 lacks, THEN the whole
    // output decodes as UTF-8, holds that character, and has no question mark in its place.
    @Test
    void write_latin1SourceWithCharacterOutsideLatin1_writesWholeFileAsUtf8() throws IOException {
        final Path file = tempDir.resolve("chapter.md");
        Files.write(file, SOURCE.getBytes(LATIN_1));
        final Document document = new MarkdownReader(registry).read(file);
        assertThat(document.charset()).isEqualTo("ISO-8859-1");

        final Path output = new MarkdownWriter(registry)
                .write(TargetedDocuments.withTarget(document, 1, "Voilà Ÿ."), tempDir.resolve("out.md"), "uk");

        final String text = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
        assertThat(text).isEqualTo("# Le café\n\nVoilà Ÿ.\n").doesNotContain("?");
    }
}
