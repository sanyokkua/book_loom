package ua.bookloom.llm.client.openai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;

/**
 * llama-server's error words, at the HTTP seam of the OpenAI-compatible dialect. The fixtures follow the research's
 * recorded shapes; {@code schema-not-applied.500.json} is constructed from the engine's source wording, so its status
 * and text are by decision, not observed on a machine.
 */
class LlamaServerErrorRowsWireMockTest extends ErrorRowsWireMockSupport {

    @Override
    String folder() {
        return "llamacpp";
    }

    @Test
    void chat_exceedContext_isContextWindowWithBothTypedCounts() {
        final AppError error = chatError("exceed-context.400.json");

        assertThat(error.code()).isEqualTo(ErrorCode.contextWindow);
        assertThat(error.details())
                .startsWith("reason=context-overflow,")
                .contains("promptTokens=9120")
                .contains("windowTokens=8192");
        verifyOneRequest();
    }

    @ParameterizedTest
    @CsvSource({
        "loading-model.503.json, modelUnavailable, model-loading",
        "schema-invalid-ref.400.json, validation, schema-invalid",
        "schema-not-applied.500.json, validation, schema-not-applied"
    })
    void chat_recordedBody_mapsToCodeAndReasonWithOneRequest(String fixture, ErrorCode code, String reason) {
        final AppError error = chatError(fixture);

        assertThat(error.code()).isEqualTo(code);
        assertThat(error.details()).startsWith("reason=" + reason + ",");
        assertThat(error.title() + error.message() + error.details()).doesNotContain("peg-gemma4");
        verifyOneRequest();
    }
}
