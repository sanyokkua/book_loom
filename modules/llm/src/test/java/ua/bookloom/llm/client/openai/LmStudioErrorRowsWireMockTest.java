package ua.bookloom.llm.client.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;

/**
 * LM Studio's error words, at the HTTP seam of the OpenAI-compatible dialect. The bodies are the ones the research
 * recorded; the fixtures named in {@link #STATUS_BY_DECISION} carry a status chosen by decision, not observed:
 * {@code terminated} was not reproduced on a real machine (its recording attempt is a later step), so it is stored as a
 * {@code 400} and as a {@code 200}, and the {@code 501} and the llama-server {@code 500} are constructed from code.
 */
class LmStudioErrorRowsWireMockTest extends ErrorRowsWireMockSupport {

    static final String STATUS_BY_DECISION = "status chosen by decision, not observed";
    private static final String LARGER_CONTEXT = "Reload the model with a larger context.";

    @Override
    String folder() {
        return "lmstudio";
    }

    @ParameterizedTest
    @CsvSource({
        "no-models-loaded.400.json, modelUnavailable, no-model-loaded",
        "model-unloaded.400.json, modelUnavailable, model-unloaded",
        "model-unloaded.200.sse, modelUnavailable, model-unloaded",
        "context-exceeded.200.sse, contextWindow, context-overflow",
        "tokens-to-keep.400.json, contextWindow, context-overflow",
        "grammar-too-large.400.json, validation, grammar-too-large",
        "grammar-too-large-wrapped.400.json, validation, grammar-too-large",
        "schema-invalid-parser.400.json, validation, schema-invalid",
        "schema-invalid-pointer.400.json, validation, schema-invalid",
        "schema-invalid-config.400.json, validation, schema-invalid",
        "engine-unreachable.500.json, upstream, engine-unreachable",
        "template-rejected.500.json, validation, template-rejected"
    })
    void chat_recordedBody_mapsToCodeAndReasonWithOneRequest(String fixture, ErrorCode code, String reason) {
        final AppError error = chatError(fixture);

        assertThat(error.code()).isEqualTo(code);
        assertThat(error.details()).startsWith("reason=" + reason + ",");
        verifyOneRequest();
    }

    // STATUS_BY_DECISION: terminated is never validation, whatever status it arrives with.
    @ParameterizedTest
    @CsvSource({
        "terminated.400.json, upstream, terminated",
        "terminated.200.json, upstream, terminated",
        "structured-unavailable.501.json, validation, structured-unavailable"
    })
    void chat_statusByDecisionBody_mapsByItsWordsWhateverTheStatus(String fixture, ErrorCode code, String reason) {
        final AppError error = chatError(fixture);

        assertThat(error.code()).isEqualTo(code).isNotEqualTo(ErrorCode.internal);
        assertThat(error.details()).startsWith("reason=" + reason + ",");
        verifyOneRequest();
    }

    @ParameterizedTest
    @CsvSource({"context-exceeded.200.sse", "tokens-to-keep.400.json"})
    void chat_overflowForms_messageAsksForALargerContext(String fixture) {
        assertThat(chatError(fixture).message()).contains(LARGER_CONTEXT);
    }

    @ParameterizedTest
    @CsvSource({
        "terminated.200.json",
        "no-models-loaded.400.json",
        "model-unloaded.200.sse",
        "grammar-too-large-wrapped.400.json",
        "schema-invalid-pointer.400.json",
        "template-rejected.500.json",
        "engine-unreachable.500.json"
    })
    void chat_anyRow_neverCarriesTheBodyInTheError(String fixture) {
        final AppError error = chatError(fixture);

        assertThat(error.title() + error.message() + error.details())
                .doesNotContain("fetch failed")
                .doesNotContain("Jinja")
                .doesNotContain("Pointer")
                .doesNotContain("failed to parse grammar")
                .doesNotContain("Please load");
    }
}
