package ua.bookloom.llm.client.openai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.provider.ProviderCallResult;

/**
 * OpenRouter's error answers, at the HTTP seam of the OpenAI-compatible dialect (D21). The bodies are invented in the
 * shapes OpenRouter documents and the shapes seen live (a 429 from a shared upstream pool); the provider in them is a
 * placeholder.
 */
class OpenRouterErrorRowsTest extends ErrorRowsWireMockSupport {

    @Override
    String folder() {
        return "openrouter";
    }

    @Override
    String resourcePath(final String fixture) {
        return "/ua/bookloom/llm/openrouter/" + fixture;
    }

    @Override
    ServerEngine engine() {
        return ServerEngine.OPENROUTER;
    }

    // IF a choice finished with an error were read as a reply, THEN an outage would look like an empty translation.
    @Test
    void chat_choiceFinishedWithError_isClassifiedByErrorType() {
        final AppError error = chatError("choice-error.200.json");

        assertThat(error.code()).isEqualTo(ErrorCode.upstream);
        assertThat(error.details()).startsWith("reason=provider-unavailable,");
        verifyOneRequest();
    }

    @Test
    void chat_status402CreditsLimit_isAuthWithCredits() {
        final AppError error = chatError("credits.402.json");

        assertThat(error.code()).isEqualTo(ErrorCode.auth);
        assertThat(error.details()).startsWith("reason=credits,");
        assertThat(error.message()).isEqualTo("OpenRouter refused the call: no credits or the key's limit is reached.");
        verifyOneRequest();
    }

    @Test
    void chat_status403ContentPolicy_isValidationBlocked() {
        final AppError error = chatError("content-policy.403.json");

        assertThat(error.code()).isEqualTo(ErrorCode.validation);
        assertThat(error.details()).startsWith("reason=blocked,");
        assertThat(error.message()).isEqualTo("The provider refused this passage.");
        verifyOneRequest();
    }

    @ParameterizedTest
    @ValueSource(strings = {"timeout.408.json", "timeout.504.json", "timeout.524.json"})
    void chat_statuses408_504_524_areTimeout(final String fixture) {
        assertThat(chatError(fixture).code()).isEqualTo(ErrorCode.timeout);
    }

    @ParameterizedTest
    @ValueSource(strings = {"provider-error.502.json", "provider-error.503.json", "provider-error.529.json"})
    void chat_statuses502_503_529_areUpstream(final String fixture) {
        assertThat(chatError(fixture).code()).isEqualTo(ErrorCode.upstream);
    }

    // The shared-pool 429 also names a limit source; only a 402 is about credits, so this one waits and is sent again.
    @Test
    void chat_status429_isRateLimitedAndKeepsRetryAfter() {
        final ProviderCallResult<ChatResponse> call =
                chatCall("rate-limit.429.json", aResponse().withHeader("Retry-After", "1"));

        assertThat(call.result().error()).isNotNull();
        assertThat(call.result().error().code()).isEqualTo(ErrorCode.rateLimited);
        assertThat(call.retryAfter()).isEqualTo("1");
    }

    @Test
    void chat_noEndpointForParameters_isValidationNoEndpoint() {
        final AppError error = chatError("no-endpoint.404.json");

        assertThat(error.code()).isEqualTo(ErrorCode.validation);
        assertThat(error.details()).startsWith("reason=no-endpoint-for-parameters,");
        assertThat(error.message())
                .isEqualTo("No provider of this model supports structured answers or turning reasoning off.");
        verifyOneRequest();
    }

    // The provider's own sentence explains the failure at TRACE only; the error holds the application's words.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "choice-error.200.json",
                "credits.402.json",
                "content-policy.403.json",
                "no-endpoint.404.json",
                "rate-limit.429.json"
            })
    void chat_anyRow_neverCarriesTheProvidersSentence(final String fixture) {
        final AppError error = chatError(fixture);

        assertThat(error.title() + error.message() + error.details())
                .doesNotContain("Provider returned error")
                .doesNotContain("Example Host")
                .doesNotContain("moderation")
                .doesNotContain("max_tokens")
                .doesNotContain("No endpoints found");
    }
}
