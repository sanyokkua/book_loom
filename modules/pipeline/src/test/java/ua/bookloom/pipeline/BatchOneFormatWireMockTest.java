package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchDraftingJobTest.entry;
import static ua.bookloom.pipeline.BatchDraftingJobTest.items;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;

/**
 * One JSON batch format whatever is left set from before (redesign group 2): the retired
 * {@code bookloom.batch.format=markers} property is set, and nothing reads it any more, so the request that reaches the
 * wire is still the numbered JSON batch, in both dialects.
 */
class BatchOneFormatWireMockTest {

    private static final String PROPERTY = "bookloom.batch.format";
    private static final String[] SOURCES = {
        "The lantern swung over the empty quay.",
        "Marta counted the crates twice.",
        "Nobody answered from the boat.",
        "A gull settled on the rope.",
        "The tide had turned an hour ago.",
        "She folded the map and waited."
    };
    private static final String[] TARGETS = {
        "Ліхтар гойдався над порожньою набережною.",
        "Марта двічі перерахувала ящики.",
        "З човна ніхто не відповів.",
        "Чайка сіла на линву.",
        "Приплив повернув годину тому.",
        "Вона згорнула мапу й чекала."
    };

    @TempDir
    private Path tempDir;

    @BeforeEach
    void requestMarkers() {
        System.setProperty(PROPERTY, "markers");
    }

    @AfterEach
    void cleanUp() {
        System.clearProperty(PROPERTY);
        TranslationJobTestSupport.shutdownAll();
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_markersFormatRequestedByProperty_stillSendsTheJsonBatch(final ProviderKind kind) {
        final Path book = TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", SOURCES) + "\n");
        final TestProject project = project(book, brief("en", "uk"));
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubAlways(provider.reply(
                    items(
                            entry("1", TARGETS[0]),
                            entry("2", TARGETS[1]),
                            entry("3", TARGETS[2]),
                            entry("4", TARGETS[3]),
                            entry("5", TARGETS[4]),
                            entry("6", TARGETS[5])),
                    Duration.ZERO));

            batchedJob(project, provider.model(Duration.ofSeconds(5), ignored -> {}))
                    .run();

            assertThat(provider.chatBodies().getFirst())
                    .contains("\"items\"")
                    .contains("<s id=\\\"1\\\">" + SOURCES[0] + "</s>")
                    .contains("<s id=\\\"6\\\">" + SOURCES[5] + "</s>")
                    .doesNotContain("⟦s");
        }
    }
}
