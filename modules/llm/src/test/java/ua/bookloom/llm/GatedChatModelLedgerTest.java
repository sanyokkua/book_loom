package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallRecord;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.SamplingParams;
import ua.bookloom.api.llm.TokenUsage;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.retry.RetryPolicy;

/**
 * Every chat attempt with a call kind leaves one record in the ledger, with the figures the server reported and no
 * others; a call with no kind (a verification), the state check before a resend and the repeat a refused setting
 * costs leave none. Both dialects, at the HTTP seam.
 */
class GatedChatModelLedgerTest {

    private static final String OPENAI = "OPENAI_COMPATIBLE, /v1, /v1/chat/completions";
    private static final String OLLAMA = "OLLAMA, '', /api/chat";
    private static final String OPENAI_REPLY = "{\"model\":\"m\",\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
    private static final String OLLAMA_REPLY =
            "{\"model\":\"m\",\"message\":{\"content\":\"ok\"},\"done\":true,\"done_reason\":\"stop\"}";
    // Only the prompt count is reported: no completion count, no cache figure, no times.
    private static final String OPENAI_PROMPT_ONLY = "{\"model\":\"m\",\"choices\":[{\"message\":{\"role\":"
            + "\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":120}}";
    private static final String OLLAMA_PROMPT_ONLY = "{\"model\":\"m\",\"message\":{\"content\":\"ok\"},\"done\":true,"
            + "\"done_reason\":\"stop\",\"prompt_eval_count\":120}";
    private static final String NO_ROW = "{\"error\":\"the flux capacitor is warming up\"}";
    private static final String TERMINATED = "{\"error\":\"terminated\"}";
    private static final String TEMPERATURE_REFUSAL =
            "{\"error\":\"Unsupported parameter: 'temperature' is not supported with this model\"}";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-29T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final CallLedgerImpl ledger = new CallLedgerImpl();

    @BeforeEach
    void start() {
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @ParameterizedTest
    @CsvSource({OPENAI, OLLAMA})
    void chat_verificationCall_isNotRecorded(final ProviderKind kind, final String basePath, final String chatPath) {
        stubChat(chatPath, List.of(answer(kind)));

        final Result<ChatResponse> result = model(kind, basePath).chat(request(null));

        assertThat(result.isOk()).isTrue();
        assertThat(chatRequests(chatPath)).isEqualTo(1);
        assertThat(ledger.since(0)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({OPENAI, OLLAMA})
    void chat_draftCall_recordsUsageWithNullsKept(
            final ProviderKind kind, final String basePath, final String chatPath) {
        stubChat(chatPath, List.of(promptOnly(kind)));

        model(kind, basePath).chat(request(CallKind.DRAFT));

        final CallRecord record = only(ledger.since(0));
        assertThat(record.callKind()).isEqualTo(CallKind.DRAFT);
        assertThat(record.isFailed()).isFalse();
        assertThat(record.failureReason()).isNull();
        assertThat(record.timeToFirstToken()).isNull();
        assertThat(record.wallTime().isNegative()).isFalse();
        final TokenUsage usage = Objects.requireNonNull(record.usage(), "usage");
        assertThat(usage.prompt()).isEqualTo(120);
        assertThat(usage.completion()).isNull();
        assertThat(usage.cachedPrompt()).isNull();
        assertThat(usage.promptEval()).isNull();
    }

    // A 503 no row explains is retried; every attempt is a failed record whose reason says no row matched.
    @ParameterizedTest
    @CsvSource({OPENAI, OLLAMA})
    void chat_failedCall_recordsUnclassifiedReason(
            final ProviderKind kind, final String basePath, final String chatPath) {
        stubChat(chatPath, List.of(failure(503, NO_ROW)));

        final Result<ChatResponse> result = model(kind, basePath).chat(request(CallKind.REVIEW));

        assertThat(result.isErr()).isTrue();
        assertThat(ledger.since(0)).hasSize(chatRequests(chatPath)).allSatisfy(record -> {
            assertThat(record.callKind()).isEqualTo(CallKind.REVIEW);
            assertThat(record.isFailed()).isTrue();
            assertThat(record.failureReason()).isEqualTo(FailureReason.UNCLASSIFIED);
            assertThat(record.usage()).isNull();
        });
    }

    // The state check before the resend of a stopped reply is no chat call: two attempts, two records, no third.
    @ParameterizedTest
    @CsvSource({OPENAI, OLLAMA})
    void chat_terminatedThenAnswer_recordsTheTwoAttemptsAndNotTheStateCheck(
            final ProviderKind kind, final String basePath, final String chatPath) {
        stubChat(chatPath, List.of(failure(400, TERMINATED), answer(kind)));

        final Result<ChatResponse> result = model(kind, basePath).chat(request(CallKind.DRAFT));

        assertThat(result.isOk()).isTrue();
        assertThat(ledger.since(0))
                .extracting(CallRecord::failureReason)
                .containsExactly(FailureReason.TERMINATED, null);
    }

    // A refused setting is not an attempt, so its repeat costs no record of its own: only the answered call is kept.
    @ParameterizedTest
    @CsvSource({OPENAI, OLLAMA})
    void chat_refusedSettingThenAnswer_recordsOnlyTheAnsweredCall(
            final ProviderKind kind, final String basePath, final String chatPath) {
        stubChat(chatPath, List.of(failure(400, TEMPERATURE_REFUSAL), answer(kind)));

        final Result<ChatResponse> result = model(kind, basePath).chat(request(CallKind.DRAFT));

        assertThat(result.isOk()).isTrue();
        assertThat(chatRequests(chatPath)).isEqualTo(2);
        assertThat(only(ledger.since(0)).isFailed()).isFalse();
    }

    private static CallRecord only(final List<CallRecord> records) {
        assertThat(records).hasSize(1);
        return records.getFirst();
    }

    // Answers the n-th chat request with the n-th reply; the last reply answers every later one.
    private void stubChat(final String chatPath, final List<ResponseDefinitionBuilder> replies) {
        for (int index = 0; index < replies.size(); index++) {
            final ScenarioMappingBuilder mapping = post(urlEqualTo(chatPath))
                    .inScenario("chat")
                    .whenScenarioStateIs(index == 0 ? Scenario.STARTED : "request-" + index)
                    .willReturn(replies.get(index));
            server.stubFor(index + 1 < replies.size() ? mapping.willSetStateTo("request-" + (index + 1)) : mapping);
        }
    }

    private static ResponseDefinitionBuilder failure(final int status, final String body) {
        return aResponse().withStatus(status).withBody(body);
    }

    private static ResponseDefinitionBuilder answer(final ProviderKind kind) {
        return aResponse().withStatus(200).withBody(kind == ProviderKind.OLLAMA ? OLLAMA_REPLY : OPENAI_REPLY);
    }

    private static ResponseDefinitionBuilder promptOnly(final ProviderKind kind) {
        return aResponse()
                .withStatus(200)
                .withBody(kind == ProviderKind.OLLAMA ? OLLAMA_PROMPT_ONLY : OPENAI_PROMPT_ONLY);
    }

    private int chatRequests(final String chatPath) {
        return server.findAll(postRequestedFor(urlEqualTo(chatPath))).size();
    }

    private static ChatRequest request(final @Nullable CallKind kind) {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate this.")),
                0.2,
                null,
                ReasoningLevel.OFF,
                8192,
                null,
                400,
                null,
                kind,
                new SamplingParams(null, null, null, null),
                null);
    }

    private GatedChatModel model(final ProviderKind kind, final String basePath) {
        final ProviderConfig config = new ProviderConfig(
                "test", kind, URI.create(server.baseUrl() + basePath), Duration.ofSeconds(5), Duration.ofSeconds(5));
        final ProviderClientFactory clients = new ProviderClientFactory(
                new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
        return new GatedChatModel(
                clients.create(config),
                "test",
                "test-model",
                new InferenceGate(),
                new RetryPolicy(CLOCK, () -> 0.5, ignored -> {}),
                ModelSettings::defaults,
                null,
                ledger);
    }
}
