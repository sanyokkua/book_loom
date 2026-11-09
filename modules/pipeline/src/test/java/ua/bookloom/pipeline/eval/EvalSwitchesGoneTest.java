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
 * No application source may read an eval switch, or a release could be steered by a variable.
 */
class EvalSwitchesGoneTest {

    private static final List<String> SWITCHES =
            List.of("BOOKLOOM_EVAL_" + "RULES", "bookloom.eval." + "rules", "BOOKLOOM_EVAL_" + "VARIANTS");

    @Test
    void sources_noEvalSwitch_remainsInMain() throws IOException {
        final Path modules = EvalProvenance.repoRoot().resolve("modules");

        assertThat(mainSources(modules))
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
