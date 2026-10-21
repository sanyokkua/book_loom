package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.CallAttempt;
import ua.bookloom.api.llm.CallAttemptListener;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.OutputLengthField;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.RefusedSetting;
import ua.bookloom.api.llm.SamplingParams;
import ua.bookloom.api.llm.TemperatureChoice;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.provider.ModelSettingsCache;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.provider.RefusalMemory;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;

/**
 * A server that refuses an optional setting still translates: the request is repeated without that setting alone,
 * the repeat is not an attempt, and the refusal is remembered for the provider and model, in both dialects.
 */
class GatedChatModelRefusedSettingTest {

    private static final String PROVIDER = "office";
    private static final String MODEL = "gpt-oss:20b";
    private static final String OTHER_MODEL = "gemma4:e4b";
    private static final String OPENAI_REPLY = "{\"model\":\"m\",\"choices\":[{\"message\":{\"role\":\"assistant\","
            + "\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";
    private static final String OLLAMA_REPLY =
            "{\"model\":\"m\",\"message\":{\"content\":\"ok\"},\"done\":true,\"done_reason\":\"stop\"}";
    private static final String TEMPERATURE_REFUSAL =
            "{\"error\":\"Unsupported parameter: 'temperature' is not supported with this model\"}";
    private static final String CSV = "OPENAI_COMPATIBLE, /v1, /v1/chat/completions";
    private static final String CSV_OLLAMA = "OLLAMA, '', /api/chat";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-20T12:00:00Z"), ZoneOffset.UTC);

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final FakeModelSettingsRepository repository = new FakeModelSettingsRepository();
    private final List<String> heard = new ArrayList<>();
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final CallAttemptListener listener = new CallAttemptListener() {
        @Override
        public void started(final CallAttempt attempt) {
            heard.add("started " + attempt.number() + "/" + attempt.maxAttempts());
        }

        @Override
        public void failed(final CallAttempt attempt, final ErrorCode code) {
            heard.add("failed " + attempt.number() + " " + code);
        }
    };

    @BeforeEach
    void start() {
        server.start();
        final Logger bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        bookloom.setLevel(Level.DEBUG);
        logged.start();
        bookloom.addAppender(logged);
    }

    @AfterEach
    void stop() {
        ((Logger) LoggerFactory.getLogger("ua.bookloom")).detachAppender(logged);
        server.stop();
    }

    // IF the server refuses the temperature, THEN the repeat lacks it, and a new call lacks it from its first request.
    @ParameterizedTest
    @CsvSource({CSV, CSV_OLLAMA})
    void chat_temperatureRefused_repeatAndLaterCallsLackIt(
            final ProviderKind kind, final String basePath, final String chatPath) {
        stubSequence(kind, chatPath, reply(400, TEMPERATURE_REFUSAL), ok());
        final GatedChatModel model = model(kind, basePath, MODEL);

        final Result<ChatResponse> first = model.chat(request());
        final Result<ChatResponse> second = model.chat(request());

        assertThat(first.isOk()).isTrue();
        assertThat(second.isOk()).isTrue();
        assertThat(bodies(chatPath)).hasSize(3);
        assertThat(bodies(chatPath).get(0)).contains("\"temperature\":0.2");
        assertThat(bodies(chatPath).get(1)).doesNotContain("temperature");
        assertThat(bodies(chatPath).get(2)).doesNotContain("temperature");
    }

    // Only the refused setting disappears: temperature and both messages stay.
    @ParameterizedTest
    @CsvSource({CSV, CSV_OLLAMA})
    void chat_topPRefused_repeatKeepsTemperatureAndMessages(
            final ProviderKind kind, final String basePath, final String chatPath) {
        stubSequence(kind, chatPath, reply(400, "{\"error\":\"unknown field top_p\"}"), ok());

        final Result<ChatResponse> result = model(kind, basePath, MODEL).chat(request());

        assertThat(result.isOk()).isTrue();
        assertThat(bodies(chatPath).get(0)).contains("\"top_p\":0.9");
        assertThat(bodies(chatPath).get(1))
                .doesNotContain("top_p")
                .contains("\"temperature\":0.2")
                .contains("Translate this.")
                .contains("Keep names.");
    }

    // The window and the reasoning control the request carried are kept when only the temperature goes.
    @ParameterizedTest
    @CsvSource({CSV_OLLAMA})
    void chat_ollamaTemperatureRefused_repeatKeepsContextAndThink(
            final ProviderKind kind, final String basePath, final String chatPath) {
        stubSequence(kind, chatPath, reply(400, TEMPERATURE_REFUSAL), ok());

        final Result<ChatResponse> result = model(kind, basePath, MODEL).chat(request());

        assertThat(result.isOk()).isTrue();
        assertThat(bodies(chatPath).get(1))
                .doesNotContain("temperature")
                .contains("\"num_ctx\":8192")
                .contains("\"think\":\"low\"");
    }

    // max_tokens refused: the repeat and every later call carry the cap as max_completion_tokens.
    @ParameterizedTest
    @CsvSource({CSV})
    void chat_maxTokensRefused_repeatAndLaterCallsUseMaxCompletionTokens(
            final ProviderKind kind, final String basePath, final String chatPath) {
        stubSequence(
                kind,
                chatPath,
                reply(400, "{\"error\":\"max_tokens is not supported, use max_completion_tokens\"}"),
                ok());
        final GatedChatModel model = model(kind, basePath, MODEL);

        model.chat(request());
        final Result<ChatResponse> second = model.chat(request());

        assertThat(second.isOk()).isTrue();
        assertThat(bodies(chatPath).get(0)).contains("\"max_tokens\":400");
        assertThat(bodies(chatPath).get(1))
                .contains("\"max_completion_tokens\":400")
                .doesNotContain("\"max_tokens\"");
        assertThat(bodies(chatPath).get(2))
                .contains("\"max_completion_tokens\":400")
                .doesNotContain("\"max_tokens\"");
    }

    // IF the 400 names nothing the request sent, THEN it stays validation, is not repeated and nothing is stored.
    @ParameterizedTest
    @CsvSource({CSV, CSV_OLLAMA})
    void chat_badRequestNamingNothingSent_isValidationWithOneRequestAndNoMemory(
            final ProviderKind kind, final String basePath, final String chatPath) {
        server.stubFor(post(urlEqualTo(chatPath))
                .willReturn(aResponse().withStatus(400).withBody("{\"error\":\"invalid request\"}")));

        final Result<ChatResponse> result = model(kind, basePath, MODEL).chat(request());

        assertThat(Objects.requireNonNull(result.error(), "error").code()).isEqualTo(ErrorCode.validation);
        server.verify(1, postRequestedFor(urlEqualTo(chatPath)));
        assertThat(new ModelSettingsCache(repository).current(PROVIDER, MODEL).refused())
                .isEmpty();
    }

    // The repeat is not an attempt: refusal, 503, 503, 200 is four requests, and the refusal is not announced as
    // failed.
    @ParameterizedTest
    @CsvSource({CSV, CSV_OLLAMA})
    void chat_refusalThenTwoUpstreamFaults_succeedsAfterFourRequestsWithoutCountingTheRepeat(
            final ProviderKind kind, final String basePath, final String chatPath) {
        stubSequence(kind, chatPath, reply(400, TEMPERATURE_REFUSAL), reply(503, "{}"), reply(503, "{}"), ok());

        final Result<ChatResponse> result = model(kind, basePath, MODEL).chat(request(), listener);

        assertThat(result.isOk()).isTrue();
        server.verify(4, postRequestedFor(urlEqualTo(chatPath)));
        assertThat(heard)
                .containsExactly(
                        "started 1/2",
                        "started 1/2",
                        "failed 1 upstream",
                        "started 2/3",
                        "failed 2 upstream",
                        "started 3/3");
    }

    // After a restart (a new cache and memory over the same stored settings) the first request lacks the temperature.
    @ParameterizedTest
    @CsvSource({CSV, CSV_OLLAMA})
    void chat_afterRestart_firstRequestLacksTheRefusedTemperature(
            final ProviderKind kind, final String basePath, final String chatPath) {
        stubSequence(kind, chatPath, reply(400, TEMPERATURE_REFUSAL), ok());
        model(kind, basePath, MODEL).chat(request());

        final Result<ChatResponse> afterRestart = model(kind, basePath, MODEL).chat(request());

        assertThat(afterRestart.isOk()).isTrue();
        assertThat(bodies(chatPath)).hasSize(3);
        assertThat(bodies(chatPath).get(2)).doesNotContain("temperature");
    }

    @ParameterizedTest
    @CsvSource({CSV, CSV_OLLAMA})
    void chat_otherModelOfTheSameProvider_stillSendsItsTemperature(
            final ProviderKind kind, final String basePath, final String chatPath) {
        stubSequence(kind, chatPath, reply(400, TEMPERATURE_REFUSAL), ok());
        model(kind, basePath, MODEL).chat(request());

        model(kind, basePath, OTHER_MODEL).chat(request());

        assertThat(bodies(chatPath)).hasSize(3);
        assertThat(bodies(chatPath).get(2)).contains("\"temperature\":0.2");
    }

    // A remembered refusal beats the person's own value: the first request carries no temperature at all.
    @ParameterizedTest
    @CsvSource({CSV, CSV_OLLAMA})
    void chat_rememberedRefusalUnderMyValue_sendsNoTemperatureAndOneRequest(
            final ProviderKind kind, final String basePath, final String chatPath) {
        repository.save(new ModelSettings(
                PROVIDER,
                MODEL,
                TemperatureChoice.VALUE,
                0.7,
                null,
                null,
                OutputLengthField.MAX_TOKENS,
                List.of(new RefusedSetting("temperature", LocalDate.of(2026, 10, 20), "omitted")),
                null));
        stubSequence(kind, chatPath, ok());

        final Result<ChatResponse> result = model(kind, basePath, MODEL).chat(request());

        assertThat(result.isOk()).isTrue();
        assertThat(bodies(chatPath)).hasSize(1);
        assertThat(bodies(chatPath).get(0)).doesNotContain("temperature");
    }

    // One warning when the refusal happens, none when the remembered refusal is applied to the next call.
    @ParameterizedTest
    @CsvSource({CSV, CSV_OLLAMA})
    void chat_temperatureRefused_logsOneWarningAndNoneForTheRememberedOne(
            final ProviderKind kind, final String basePath, final String chatPath) {
        stubSequence(kind, chatPath, reply(400, TEMPERATURE_REFUSAL), ok());
        final GatedChatModel model = model(kind, basePath, MODEL);

        model.chat(request());
        final long afterFirst = refusalWarnings();
        model.chat(request());

        assertThat(afterFirst).isEqualTo(1);
        assertThat(refusalWarnings()).isEqualTo(1);
    }

    private long refusalWarnings() {
        return logged.list.stream()
                .filter(event -> event.getLevel() == Level.WARN)
                .filter(event -> event.getFormattedMessage().contains("refused a setting"))
                .filter(event -> event.getFormattedMessage().contains("temperature"))
                .count();
    }

    // Replies are given in order, the last one for every request after.
    private void stubSequence(final ProviderKind kind, final String chatPath, final Reply... replies) {
        final String okBody = kind == ProviderKind.OLLAMA ? OLLAMA_REPLY : OPENAI_REPLY;
        IntStream.range(0, replies.length).forEach(step -> {
            final Reply reply = replies[step];
            final boolean last = step == replies.length - 1;
            server.stubFor(post(urlEqualTo(chatPath))
                    .inScenario("replies")
                    .whenScenarioStateIs(step == 0 ? Scenario.STARTED : "step " + step)
                    .willReturn(aResponse()
                            .withStatus(reply.status())
                            .withBody(reply.body() == null ? okBody : reply.body()))
                    .willSetStateTo(last ? "step " + step : "step " + (step + 1)));
        });
    }

    private static Reply reply(final int status, final String body) {
        return new Reply(status, body);
    }

    private static Reply ok() {
        return new Reply(200, null);
    }

    private record Reply(int status, @Nullable String body) {}

    // Request bodies in the order the server received them.
    private List<String> bodies(final String chatPath) {
        final List<String> all = new ArrayList<>(server.getAllServeEvents().stream()
                .filter(event -> event.getRequest().getUrl().equals(chatPath))
                .map(event -> event.getRequest().getBodyAsString())
                .toList());
        Collections.reverse(all);
        return all;
    }

    private static ChatRequest request() {
        return new ChatRequest(
                List.of(
                        new ChatMessage(ChatRole.SYSTEM, "Keep names."),
                        new ChatMessage(ChatRole.USER, "Translate this.")),
                0.2,
                null,
                false,
                8192,
                null,
                400,
                null,
                CallKind.DRAFT,
                new SamplingParams(0.9, null, null, null),
                null);
    }

    private GatedChatModel model(final ProviderKind kind, final String basePath, final String modelId) {
        final ProviderConfig config = new ProviderConfig(
                "test", kind, URI.create(server.baseUrl() + basePath), Duration.ofSeconds(5), Duration.ofSeconds(5));
        final ProviderClientFactory clients = new ProviderClientFactory(
                new HttpExchange(new HttpClients()), new LlmModule().objectMapper(), System::nanoTime);
        final RetryPolicy retryPolicy = new RetryPolicy(CLOCK, () -> 0.5, ignored -> {});
        final ModelSettingsCache cache = new ModelSettingsCache(repository);
        return new GatedChatModel(
                clients.create(config),
                PROVIDER,
                modelId,
                new InferenceGate(),
                retryPolicy,
                cache::current,
                new RefusalMemory(cache));
    }
}
