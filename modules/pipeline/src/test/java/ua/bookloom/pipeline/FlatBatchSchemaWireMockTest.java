package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchDraftingJobTest.entry;
import static ua.bookloom.pipeline.BatchDraftingJobTest.items;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.ReviewMode;
import ua.bookloom.api.pipeline.RunRequest;
import ua.bookloom.api.project.LexiconEntry;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.prompt.PromptTemplates;

/**
 * Arm 5b kept the flat batch schema: in both dialects a batch over a recurring lexicon term carries no key-terms block
 * and asks for no {@code terms}, on the wire.
 */
class FlatBatchSchemaWireMockTest {

    private static final String[] SOURCES = {
        "The old master walked to the harbour.", "The young master counted the crates.",
    };

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUp() {
        TranslationJobTestSupport.shutdownAll();
    }

    @ParameterizedTest
    @EnumSource(ProviderKind.class)
    void run_recurringTermInBatch_sendsNoKeyTermsBlockAndNoTerms(final ProviderKind kind) {
        final String body = firstBatchBody(kind);

        assertThat(body)
                .doesNotContain("[Key terms")
                .doesNotContain("\"terms\"")
                .contains("\"items\"", "master");
    }

    private String firstBatchBody(final ProviderKind kind) {
        final Path book = TestBooks.markdown(tempDir.resolve("Book.md"), String.join("\n\n", SOURCES) + "\n");
        final TestProject project = project(book, brief("en", "uk"));
        project.stores().lexicon().put(LexiconEntry.of(project.id(), "master"));
        try (WireMockProvider provider = new WireMockProvider(kind)) {
            provider.stubAlways(provider.reply(
                    items(
                            entry("1", "Старий майстер пішов до гавані."),
                            entry("2", "Молодий майстер перелічив ящики.")),
                    Duration.ZERO));

            batchedJob(
                            project,
                            provider.model(Duration.ofSeconds(5), ignored -> {}),
                            new RunRequest(project.id(), ReviewMode.UNATTENDED),
                            new PromptTemplates())
                    .run();

            final List<String> bodies = provider.chatBodies();
            assertThat(bodies).isNotEmpty();
            return bodies.getFirst();
        }
    }
}
