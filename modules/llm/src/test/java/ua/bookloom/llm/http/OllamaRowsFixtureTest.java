package ua.bookloom.llm.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every Ollama row has a recorded body to prove it, and ids are unique across the catalogue. */
class OllamaRowsFixtureTest {

    @Test
    void rows_everyOllamaRowHasAFixtureFileNamedAfterIt() throws IOException, URISyntaxException {
        final Path folder = Path.of(Objects.requireNonNull(getClass().getResource("/errors/ollama/"))
                .toURI());
        final List<String> files;
        try (Stream<Path> listing = Files.list(folder)) {
            files = listing.map(path -> path.getFileName().toString()).toList();
        }

        assertThat(OllamaRows.ROWS)
                .isNotEmpty()
                .allSatisfy(row -> assertThat(files)
                        .anyMatch(name -> name.startsWith(row.id().substring("ollama-".length()) + ".")));
    }

    @Test
    void rows_idsAreUniqueAcrossTheCatalogue() {
        assertThat(ErrorRows.ORDERED.stream().map(ErrorRow::id)).doesNotHaveDuplicates();
    }
}
