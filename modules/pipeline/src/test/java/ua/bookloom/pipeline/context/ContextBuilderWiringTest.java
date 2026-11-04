package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Task 11.2 builds the context builder beside the run; the run's own packages do not call it until task 11.5. */
class ContextBuilderWiringTest {

    private static final Path MAIN = Path.of("src/main/java/ua/bookloom/pipeline");

    @ParameterizedTest
    @ValueSource(strings = {"run", "batch", "heal"})
    void runPackage_doesNotReferenceTheBuilderYet(final String pkg) throws IOException {
        try (Stream<Path> files = Files.walk(MAIN.resolve(pkg))) {
            assertThat(files.filter(file -> file.toString().endsWith(".java")))
                    .isNotEmpty()
                    .noneMatch(file -> read(file).contains("ContextBuilder"));
        }
    }

    private static String read(final Path file) {
        try {
            return Files.readString(file);
        } catch (IOException cause) {
            throw new IllegalStateException(file.toString(), cause);
        }
    }
}
