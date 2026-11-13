package ua.bookloom.llm.verify;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.FinishReason;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.llm.VerificationStage;

/** An address saved before user names were refused never puts them into the test dialog or its curl line. */
class VerificationExchangesTest {

    @ParameterizedTest
    @CsvSource({
        "http://user:pass@127.0.0.1:11434,http://127.0.0.1:11434/api/version",
        "http://user@127.0.0.1:11434/,http://127.0.0.1:11434/api/version",
        "http://127.0.0.1:11434,http://127.0.0.1:11434/api/version"
    })
    void connection_storedAddressWithUserInfo_showsTheUrlWithoutIt(final String address, final String shown) {
        final ProviderConfig config = new ProviderConfig(
                "old",
                ProviderKind.OLLAMA,
                URI.create(address),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);

        final String url = VerificationExchanges.connection(
                        config, new StageOutcome(VerificationStage.CONNECTION, StageStatus.PASSED, null, null))
                .url();

        assertThat(url).isEqualTo(shown);
    }

    // IF the cached prompt tokens were dropped here, THEN the test dialog could not say how much of the prompt was
    // cached.
    @Test
    void inference_usageWithCachedPrompt_carriesItToTheExchange() {
        final ProviderConfig config = new ProviderConfig(
                "mlx",
                ProviderKind.OLLAMA,
                URI.create("http://127.0.0.1:11434"),
                ProviderConfig.DEFAULT_CONNECT_TIMEOUT,
                ProviderConfig.DEFAULT_REQUEST_TIMEOUT);
        final TokenUsage usage = new TokenUsage(600, 20, Duration.ofSeconds(1), Duration.ofMillis(900), 200, null);
        final InferenceProbe probe = new InferenceProbe(
                new StageOutcome(VerificationStage.INFERENCE, StageStatus.PASSED, null, null),
                new ChatResponse("OK", FinishReason.STOP, usage, null),
                true,
                new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hi"))),
                null,
                null);

        final var exchange = VerificationExchanges.inference(
                config, probe, VerificationExchanges.Bodies.NONE, URI.create("http://127.0.0.1:11434/v1/x"));

        assertThat(exchange.cachedPromptTokens()).isEqualTo(200);
        assertThat(exchange.firstTokenMs()).isEqualTo(900L);
    }
}
