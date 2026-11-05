package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static ua.bookloom.pipeline.BatchDraftingJobTest.entry;
import static ua.bookloom.pipeline.BatchDraftingJobTest.items;
import static ua.bookloom.pipeline.BatchedJobs.batchedJob;
import static ua.bookloom.pipeline.TranslationJobTestSupport.brief;
import static ua.bookloom.pipeline.TranslationJobTestSupport.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.pipeline.TranslationJobTestSupport.TestProject;
import ua.bookloom.pipeline.context.OutputReserve;

/**
 * The output cap a draft reaches the wire with ("Cap every call's output at the reserve the context builder
 * computes"): a model that always reasons gets at least the reasoning reserve even for a one-word segment, so its
 * thinking does not use up the answer's room, while a model that does not think keeps the small answer cap.
 */
class ReasoningReserveWireMockTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    // A model whose built-in row always reasons, and one whose row declares no thinking at all.
    private static final String ALWAYS_REASONS = "gpt-oss:20b";

    @TempDir
    private Path tempDir;

    @AfterEach
    void cleanUp() {
        TranslationJobTestSupport.shutdownAll();
    }

    @ParameterizedTest
    @CsvSource({"OLLAMA", "OPENAI_COMPATIBLE"})
    void draft_alwaysReasoningModelTinySegment_capIsAtLeastTheReasoningReserve(final ProviderKind kind) {
        assertThat(firstDraftCap(kind, ALWAYS_REASONS)).isGreaterThanOrEqualTo(OutputReserve.REASONING_RESERVE_MIN);
    }

    @ParameterizedTest
    @CsvSource({"OLLAMA", "OPENAI_COMPATIBLE"})
    void draft_modelThatDoesNotThinkTinySegment_keepsTheSmallAnswerCap(final ProviderKind kind) {
        assertThat(firstDraftCap(kind, WireMockProvider.MODEL_ID)).isLessThan(OutputReserve.REASONING_RESERVE_MIN);
    }

    private int firstDraftCap(final ProviderKind kind, final String modelId) {
        final TestProject project =
                project(TestBooks.markdown(tempDir.resolve("Book.md"), "Hello.\n"), brief("en", "uk"));
        try (WireMockProvider provider = new WireMockProvider(kind, modelId)) {
            provider.stubAlways(provider.reply(items(entry("1", "Привіт.")), Duration.ZERO));

            batchedJob(project, provider.model(Duration.ofSeconds(5), ignored -> {}))
                    .run();

            return capOf(provider.chatBodies().getFirst(), kind);
        }
    }

    private static int capOf(final String body, final ProviderKind kind) {
        try {
            final JsonNode json = MAPPER.readTree(body);
            final JsonNode cap =
                    kind == ProviderKind.OLLAMA ? json.path("options").path("num_predict") : json.path("max_tokens");
            assertThat(cap.isInt())
                    .as("the request carries an output cap: %s", body)
                    .isTrue();
            return cap.asInt();
        } catch (final com.fasterxml.jackson.core.JsonProcessingException cause) {
            throw new AssertionError("could not read the request body", cause);
        }
    }
}
