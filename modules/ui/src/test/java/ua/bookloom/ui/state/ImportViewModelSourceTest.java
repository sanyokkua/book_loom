package ua.bookloom.ui.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * The "named once" scenario of the import specification: the code that reads a book's title and author reads them
 * through the constants declared in {@code :api}, and spells neither key name itself.
 */
class ImportViewModelSourceTest {

    private static final Path SOURCE = Path.of("src/main/java/ua/bookloom/ui/state/ImportViewModel.java");

    // IF the view model spelled "title" or "author" itself, THEN a key edited in the reader would silently drop that
    // row from the card.
    @Test
    void source_readingTitleAndAuthor_containsNoKeyStringLiteral() throws IOException {
        final String source = Files.readString(SOURCE);

        assertThat(source).doesNotContain("\"title\"").doesNotContain("\"author\"");
    }

    // IF the view model read the keys through something other than the :api constants, THEN the literal-free source
    // would prove nothing about where the names come from.
    @Test
    void source_readingTitleAndAuthor_goesThroughTheApiConstants() throws IOException {
        final String source = Files.readString(SOURCE);

        assertThat(source).contains("MetadataKey.TITLE").contains("MetadataKey.AUTHOR");
    }
}
