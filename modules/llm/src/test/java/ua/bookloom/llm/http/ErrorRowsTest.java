package ua.bookloom.llm.http;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.FailureReason;

/** The catalogue's shape: unique row names, one meaning per reason, and words matched in any order. */
class ErrorRowsTest {

    @Test
    void rows_ids_areUnique() {
        assertThat(ErrorRows.ORDERED).extracting(ErrorRow::id).isNotEmpty().doesNotHaveDuplicates();
    }

    // IF two rows with one reason disagreed on the code or the retry, THEN the same failure would be handled two ways.
    @Test
    void rows_sameReason_shareCodeAndRetry() {
        final Map<FailureReason, Set<List<Object>>> byReason = ErrorRows.ORDERED.stream()
                .collect(groupingBy(ErrorRow::reason, mapping(row -> List.of(row.code(), row.retry()), toSet())));

        assertThat(byReason.values())
                .allSatisfy(handling -> assertThat(handling).hasSize(1));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "request (696 tokens) exceeds the available context size (512 tokens)",
                "the context window was exceeded by the request"
            })
    void contextRow_verbFirstAndContextFirst_bothMatch(final String body) {
        assertThat(ErrorRows.match(400, body))
                .hasValueSatisfying(row -> assertThat(row.id()).isEqualTo("context-words"));
    }

    // IF a Go client's deadline ("context deadline exceeded") read as an overflow, THEN an outage would flag the
    // segment and never be retried.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"error\":\"Post \\\"http://127.0.0.1:1/completion\\\": context deadline exceeded\"}",
                "context deadline exceeded (Client.Timeout exceeded while awaiting headers)",
                "the request context timed out and exceeded its budget",
                "context canceled: the wait exceeded the limit"
            })
    void contextRow_deadlineOrTimeoutWording_isNoOverflow(final String body) {
        assertThat(ErrorRows.match(500, body)).isEmpty();
        assertThat(ErrorRows.matchText(body)).isEmpty();
    }

    // A proxy's own 502 page says nothing about Ollama's cloud; the status alone decides it.
    @Test
    void match_plainBadGatewayPage_isNoCloudRow() {
        assertThat(ErrorRows.match(502, "<html>502 Bad Gateway</html>")).isEmpty();
    }

    @Test
    void match_wordsNoRowKnows_isEmpty() {
        assertThat(ErrorRows.match(400, "{\"error\":\"invalid option: temperature\"}"))
                .isEmpty();
    }

    // One case per engine row: the words alone select it, and the id is what DEBUG diagnostics name.
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "400|{\"error\":\"terminated\"}|lmstudio-terminated",
                "200|{\"error\":\"terminated.\"}|lmstudio-terminated",
                "400|{\"error\":\"No models loaded. Please load a model.\"}|lmstudio-no-model-loaded",
                "400|{\"error\":\"Model unloaded.\"}|lmstudio-model-unloaded",
                "400|{\"error\":\"model 'x' is not loaded\"}|lmstudio-model-unloaded",
                "200|event: error\\ndata: {\"message\":\"Context size has been exceeded.\"}|lmstudio-context-exceeded",
                "400|The number of tokens to keep from the initial prompt is greater than the context length.|"
                        + "lmstudio-tokens-to-keep",
                "400|Failed to initialize samplers: failed to parse grammar|lmstudio-grammar-too-large",
                "501|{\"error\":\"structured output is unavailable\"}|lmstudio-structured-unavailable",
                "500|Jinja Exception: roles must alternate|lmstudio-template-rejected",
                "500|Engine protocol predict request failed: fetch failed|lmstudio-engine-unreachable",
                "400|JSON schema error at #: unrecognized type banana|lmstudio-schema-invalid",
                "400|Invalid structured output configuration: data/k|lmstudio-schema-invalid",
                "400|ValueError: Pointer '/definitions/x' does not exist|lmstudio-schema-invalid",
                "400|Unable to generate parser for this template|lmstudio-schema-invalid",
                "400|cannot resolve $ref #/$defs/nope|lmstudio-schema-invalid",
                "400|{\"type\":\"exceed_context_size_error\",\"n_prompt_tokens\":9120,\"n_ctx\":8192}|"
                        + "llama-exceed-context",
                "503|{\"error\":{\"message\":\"Loading model\"}}|llama-model-loading",
                "500|unparsed peg-gemma4 output|llama-schema-not-applied",
                "500|does not match the expected peg-native format|llama-schema-not-applied"
            })
    void match_engineWords_selectsTheRow(int status, String body, String expectedId) {
        assertThat(ErrorRows.match(status, body))
                .hasValueSatisfying(row -> assertThat(row.id()).isEqualTo(expectedId));
    }

    // "Loading model" in a non-503 reply is no loading signal; the row names the status.
    @Test
    void match_loadingModelWithAnotherStatus_isNotTheLoadingRow() {
        assertThat(ErrorRows.match(400, "Loading model")).isEmpty();
    }

    // IF "No models loaded" were read by the unloaded row, THEN the two reasons would swap places.
    @Test
    void match_noModelsLoaded_comesBeforeModelUnloaded() {
        assertThat(ErrorRows.match(400, "No models loaded; the model is not loaded"))
                .hasValueSatisfying(row -> assertThat(row.id()).isEqualTo("lmstudio-no-model-loaded"));
    }

    // IF the broad context words won, THEN the LM Studio advice to reload with a larger context would never show.
    @Test
    void match_lmStudioOverflow_comesBeforeTheContextWords() {
        assertThat(ErrorRows.match(400, "Context size has been exceeded. The context is too long"))
                .hasValueSatisfying(row -> assertThat(row.id()).isEqualTo("lmstudio-context-exceeded"));
    }

    // IF the schema row won, THEN an oversized grammar would read as a bad schema.
    @Test
    void match_grammarAndSchemaWords_grammarWins() {
        assertThat(ErrorRows.match(400, "JSON schema error; Failed to initialize samplers: failed to parse grammar"))
                .hasValueSatisfying(row -> assertThat(row.id()).isEqualTo("lmstudio-grammar-too-large"));
    }
}
