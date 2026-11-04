package ua.bookloom.pipeline.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Task 11.5 moves the run's drafts, batches and repairs onto the context builder: the old single-draft and batch
 * prompt builders are no longer reached from the run's packages, which plan every translate-family call instead.
 */
class ContextBuilderWiringTest {

    private static final Path MAIN = Path.of("src/main/java/ua/bookloom/pipeline");

    @ParameterizedTest
    @ValueSource(strings = {"run", "batch"})
    void runPackage_neverBuildsTheOldDraftPrompt(final String pkg) throws IOException {
        assertThat(javaFiles(MAIN.resolve(pkg)))
                .isNotEmpty()
                .noneMatch(file -> read(file).contains("DraftPromptBuilder"))
                .noneMatch(file -> read(file).contains("DRAFT_BATCH_JSON"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"run/PromptRequests.java", "batch/BatchPromptBuilder.java", "TranslateDraftPrompts.java"})
    void translateFamilyRequests_arePlannedThroughTheRunsPlanner(final String file) {
        assertThat(read(MAIN.resolve(file))).contains("TranslatePlanner");
    }

    private static List<Path> javaFiles(final Path directory) throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(file -> file.toString().endsWith(".java")).toList();
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
