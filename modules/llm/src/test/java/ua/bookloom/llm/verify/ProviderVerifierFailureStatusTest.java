package ua.bookloom.llm.verify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.VerificationExchange;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.testing.FakeProviderConfigs;

/** A failed stage that was answered by the server keeps the status it answered with, for the test dialog. */
class ProviderVerifierFailureStatusTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-22T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    @BeforeEach
    void start() {
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private ProviderConfig draft(final ProviderKind kind) {
        final String base = kind == ProviderKind.OLLAMA ? server.baseUrl() : server.baseUrl() + "/v1";
        return new ProviderConfig("draft", kind, URI.create(base), Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    private StageOutcome stageOf(final ProviderConfig config, final int stageIndex) {
        final var mapper = new LlmModule().objectMapper();
        final var factory = new ProviderClientFactory(
                new HttpExchange(new HttpClients(), System::nanoTime, Map.<String, String>of()::get),
                mapper,
                System::nanoTime);
        final var verifier = new ProviderVerifierImpl(
                new FakeProviderConfigs(),
                factory,
                new InferenceGate(),
                new RetryPolicy(CLOCK, () -> 0.5, delay -> {}),
                mapper,
                System::nanoTime);
        return Objects.requireNonNull(verifier.verify(config, "m", VerificationPolicy.CONNECTION_AND_MODELS)
                        .data())
                .stages()
                .get(stageIndex);
    }

    // IF a refused or missing endpoint were shown as "No answer", THEN a person would hunt for a network fault when
    // the server had answered.
    @ParameterizedTest
    @CsvSource({
        "OLLAMA,/api/version,401,0",
        "OLLAMA,/api/version,500,0",
        "OPENAI_COMPATIBLE,/v1/models,401,0",
        "OPENAI_COMPATIBLE,/v1/models,404,1"
    })
    void connection_serverAnswersWithAnError_exchangeKeepsThatStatus(
            final ProviderKind kind, final String path, final int status, final int stageIndex) {
        server.stubFor(get(urlEqualTo(path)).willReturn(aResponse().withStatus(status)));

        final VerificationExchange exchange =
                Objects.requireNonNull(stageOf(draft(kind), stageIndex).exchange());

        assertThat(exchange.httpStatus()).isEqualTo(status);
    }

    // IF a refused connection reported a status, THEN "No answer" could never be shown.
    @ParameterizedTest
    @CsvSource({"OLLAMA", "OPENAI_COMPATIBLE"})
    void connection_nothingListening_exchangeHasNoStatus(final ProviderKind kind) {
        final ProviderConfig config = draft(kind);
        server.stop();

        final VerificationExchange exchange =
                Objects.requireNonNull(stageOf(config, 0).exchange());

        assertThat(exchange.httpStatus()).isZero();
    }
}
