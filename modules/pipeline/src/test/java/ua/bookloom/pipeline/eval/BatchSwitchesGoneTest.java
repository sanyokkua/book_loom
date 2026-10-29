package ua.bookloom.pipeline.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Batches go out in one JSON format (task 2.3): no application source, build script or eval script may read the old
 * batch-format, notes or related-pairs switches, so neither a variable nor an eval option can bring them back.
 */
class BatchSwitchesGoneTest {

    // Built by concatenation so this file never holds the names it forbids.
    private static final List<String> SWITCHES = List.of(
            "BOOKLOOM_" + "BATCH_FORMAT",
            "BOOKLOOM_" + "BATCH_NOTES",
            "BOOKLOOM_" + "RELEVANT_PAIRS",
            "bookloom." + "batch.format",
            "bookloom." + "batch.notes",
            "bookloom." + "relevant.pairs",
            "BOOKLOOM_EVAL_" + "BATCH_FORMAT",
            "BOOKLOOM_EVAL_" + "BATCH_NOTES",
            "BOOKLOOM_EVAL_" + "RELEVANT_PAIRS",
            "--" + "batch-format",
            "--" + "relevant-pairs",
            "--" + "batch-notes");

    @Test
    void sources_noBatchFormatOrNotesSwitch_remainInMainOrScripts() throws IOException {
        final Path root = EvalProvenance.repoRoot();
        final List<Path> files = Stream.concat(
                        mainSources(root.resolve("modules")).stream(), scripts(root.resolve("scripts")).stream())
                .toList();

        assertThat(files)
                .isNotEmpty()
                .allSatisfy(file -> assertThat(read(file)).as(file.toString()).doesNotContain(SWITCHES));
    }

    private static List<Path> mainSources(final Path modules) throws IOException {
        try (Stream<Path> files = Files.walk(modules)) {
            return files.filter(file -> unix(file).contains("/src/main/"))
                    .filter(Files::isRegularFile)
                    .filter(file -> !unix(file).contains("/build/"))
                    .toList();
        }
    }

    private static List<Path> scripts(final Path scripts) throws IOException {
        try (Stream<Path> files = Files.walk(scripts)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> !unix(file).contains("/__pycache__/"))
                    .toList();
        }
    }

    private static String unix(final Path file) {
        return file.toString().replace('\\', '/');
    }

    private static String read(final Path file) {
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
        } catch (IOException e) {
            throw new UncheckedIOException(file.toString(), e);
        }
    }
}
