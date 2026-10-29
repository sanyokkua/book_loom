package ua.bookloom.llm.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.client.ollama.OllamaClient;
import ua.bookloom.llm.client.openai.OpenAiCompatibleClient;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpErrorMapper;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.http.RetryRule;
import ua.bookloom.llm.provider.ProviderCallResult;
import ua.bookloom.llm.provider.ProviderClient;
import ua.bookloom.llm.provider.RejectedCapability;

/**
 * Each Ollama error row gives its code, reason token and retry rule on the native route and on {@code /v1}, from the
 * raw server body in {@code errors/ollama}. Provenance: measured on a local Ollama (model-not-pulled,
 * thinking-unsupported, think-level-invalid, option-type, option-type-duration); taken from Ollama's source or
 * documentation as recorded in the research (model-not-pulled-scheduler, server-busy, cloud-disabled, cloud-signin,
 * host-forbidden, runner-crashed, out-of-memory, repeat-limit); the {@code -v1} files wrap the same sentence in the
 * OpenAI error envelope; by decision, no server was reached (cloud-unreachable: the 502 body is invented to carry the
 * words "cloud model").
 */
class OllamaErrorRowsWireMockTest {

    private static final String DIR = "/errors/ollama/";
    private static final String MODEL = "qwen3.5:4b";
    private static final String NATIVE = "native";
    private static final String OPENAI = "openai";
    private static final String OOM_TEXT = "model requires 18.2 GiB but only 9.6 GiB are available";

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

    private static Stream<Arguments> tableInBothDialects() {
        return OllamaErrorTable.rows()
                .flatMap(row -> Stream.of(NATIVE, OPENAI).map(dialect -> {
                    final Object[] values = row.get();
                    return Arguments.of(values[0], values[1], values[2], values[3], values[4], dialect);
                }));
    }

    @ParameterizedTest(name = "{0} on {5}")
    @MethodSource("tableInBothDialects")
    void chat_ollamaFailureBody_givesItsCodeReasonAndRetryRuleWithOneRequest(
            final String file,
            final int status,
            final ErrorCode code,
            final FailureReason reason,
            final RetryRule retry,
            final String dialect) {
        stub(dialect, status, OllamaErrorTable.fixture(file, status));

        final AppError error = chat(dialect, MODEL, request(null)).result().error();

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo(code);
        assertThat(FailureReason.in(error.details())).contains(reason);
        assertThat(HttpErrorMapper.retryRule(reason)).contains(retry);
        assertThat(server.getAllServeEvents()).hasSize(1);
    }

    @Test
    void fixtures_everyFileHasATableEntry() throws IOException {
        final Path folder;
        try {
            folder = Path.of(Objects.requireNonNull(getClass().getResource(DIR)).toURI());
        } catch (java.net.URISyntaxException failure) {
            throw new IllegalStateException(failure);
        }
        final Set<String> expected = OllamaErrorTable.rows()
                .map(row -> row.get()[0] + "." + row.get()[1] + ".json")
                .collect(java.util.stream.Collectors.toSet());
        try (Stream<Path> files = Files.list(folder)) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    @Test
    void chat_modelNotPulled_namesTheModelAndTheCommandToPullIt() {
        stub(NATIVE, 404, OllamaErrorTable.fixture("model-not-pulled", 404));

        final AppError error = chat(NATIVE, MODEL, request(null)).result().error();

        assertThat(error).isNotNull();
        assertThat(error.message())
                .isEqualTo(
                        "This model is not installed in Ollama. Pull it (`ollama pull qwen3.5:4b`) or pick another.");
    }

    @Test
    void chat_wronglyTypedOption_isValidationOptionTypeSentOnce() {
        stub(NATIVE, 500, OllamaErrorTable.fixture("option-type", 500));

        final AppError error = chat(NATIVE, MODEL, request(null)).result().error();

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo(ErrorCode.validation);
        assertThat(FailureReason.in(error.details())).contains(FailureReason.OPTION_TYPE);
        assertThat(server.getAllServeEvents()).hasSize(1);
    }

    @Test
    void map_blankForbiddenFromALanAddress_isAuthHostForbiddenNamingTheHost() {
        final ProviderConfig config = new ProviderConfig(
                "p",
                ProviderKind.OLLAMA,
                URI.create("http://192.168.1.20:11434"),
                Duration.ofSeconds(2),
                Duration.ofSeconds(2));

        final AppError error = HttpErrorMapper.map(
                        new HttpReply(403, Map.of(), ""), config, HttpErrorMapper.CallPurpose.CHAT, MODEL)
                .error();

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo(ErrorCode.auth);
        assertThat(FailureReason.in(error.details())).contains(FailureReason.HOST_FORBIDDEN);
        assertThat(error.message())
                .isEqualTo("Ollama refused this address. Use 127.0.0.1, or let Ollama listen on the network.");
        assertThat(error.details()).contains("192.168.1.20");
    }

    @Test
    void chat_cloudModelNotSignedIn_isAuthCloudSignin() {
        stub(NATIVE, 401, OllamaErrorTable.fixture("cloud-signin", 401));

        final AppError error =
                chat(NATIVE, "gpt-oss:120b-cloud", request(null)).result().error();

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo(ErrorCode.auth);
        assertThat(FailureReason.in(error.details())).contains(FailureReason.CLOUD_SIGNIN);
        assertThat(error.message()).isEqualTo("Sign in to Ollama to use this cloud model, then try again.");
    }

    @Test
    void chat_outOfMemory_namesBothFiguresInTheMessage() {
        stub(NATIVE, 500, OllamaErrorTable.fixture("out-of-memory", 500));

        final AppError error = chat(NATIVE, MODEL, request(null)).result().error();

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo(ErrorCode.modelUnavailable);
        assertThat(error.message())
                .isEqualTo("Not enough memory to load this model (needs 18.2 GiB, 9.6 GiB free)."
                        + " Close other apps or choose a smaller build.");
    }

    @ParameterizedTest
    @MethodSource("streamedErrors")
    void chat_streamedErrorLine_isClassifiedByItsInnerText(
            final String text, final ErrorCode code, final FailureReason reason) {
        final String body = "{\"message\":{\"content\":\"Hel\"},\"done\":false}\n{\"error\":\"" + text + "\"}\n";
        server.stubFor(post(urlPathEqualTo("/api/chat"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-ndjson")
                        .withBody(body)));

        final AppError error = chat(NATIVE, MODEL, request(null)).result().error();

        assertThat(error).isNotNull();
        assertThat(error.code()).isEqualTo(code);
        assertThat(FailureReason.in(error.details())).contains(reason);
        assertThat(error.details()).doesNotContain("httpStatus").doesNotContain("lantern");
    }

    private static Stream<Arguments> streamedErrors() {
        return Stream.of(
                Arguments.of(OOM_TEXT, ErrorCode.modelUnavailable, FailureReason.OUT_OF_MEMORY),
                Arguments.of("the lantern-keeper refused", ErrorCode.upstream, FailureReason.UNCLASSIFIED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"thinking-unsupported", "thinking-unsupported-v1"})
    void chat_thinkingRefusedWhileReasoningWasSent_raisesTheReasoningControlRefusal(final String file) {
        stub(dialectOf(file), 400, OllamaErrorTable.fixture(file, 400));

        final ProviderCallResult<?> call = chat(dialectOf(file), MODEL, request(ReasoningLevel.ON));

        assertThat(call.rejectedCapability()).isEqualTo(RejectedCapability.REASONING_CONTROL);
        assertThat(FailureReason.in(
                        Objects.requireNonNull(call.result().error()).details()))
                .contains(FailureReason.THINKING_UNSUPPORTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"think-level-invalid", "think-level-invalid-v1"})
    void chat_levelRefusedWhileALevelWasSent_raisesTheReasoningControlRefusal(final String file) {
        stub(dialectOf(file), 400, OllamaErrorTable.fixture(file, 400));

        final ProviderCallResult<?> call = chat(dialectOf(file), MODEL, request(ReasoningLevel.LOW));

        assertThat(call.rejectedCapability()).isEqualTo(RejectedCapability.REASONING_CONTROL);
        assertThat(FailureReason.in(
                        Objects.requireNonNull(call.result().error()).details()))
                .contains(FailureReason.THINK_LEVEL_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"thinking-unsupported", "thinking-unsupported-v1", "think-level-invalid"})
    void chat_thinkingRefusedWhileNoReasoningWasSent_raisesNoRefusalButKeepsTheReason(final String file) {
        stub(dialectOf(file), 400, OllamaErrorTable.fixture(file, 400));

        final ProviderCallResult<?> call = chat(dialectOf(file), MODEL, request(null));

        assertThat(call.rejectedCapability()).isNull();
        assertThat(Objects.requireNonNull(call.result().error()).code()).isEqualTo(ErrorCode.validation);
    }

    private static String dialectOf(final String file) {
        return file.endsWith("-v1") ? OPENAI : NATIVE;
    }

    private void stub(final String dialect, final int status, final String body) {
        final String path = NATIVE.equals(dialect) ? "/api/chat" : "/v1/chat/completions";
        server.stubFor(post(urlPathEqualTo(path))
                .willReturn(aResponse().withStatus(status).withBody(body)));
    }

    private ProviderCallResult<?> chat(final String dialect, final String model, final ChatRequest request) {
        return client(config(dialect)).chat(model, request);
    }

    private ProviderConfig config(final String dialect) {
        final boolean isNative = NATIVE.equals(dialect);
        return new ProviderConfig(
                        "p",
                        isNative ? ProviderKind.OLLAMA : ProviderKind.OPENAI_COMPATIBLE,
                        URI.create(server.baseUrl() + (isNative ? "" : "/v1")),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2))
                .withEngine(ServerEngine.OLLAMA);
    }

    private static ProviderClient client(final ProviderConfig config) {
        final HttpExchange exchange =
                new HttpExchange(new HttpClients(), System::nanoTime, Map.<String, String>of()::get);
        return config.kind() == ProviderKind.OLLAMA
                ? new OllamaClient(config, exchange, new LlmModule().objectMapper())
                : new OpenAiCompatibleClient(config, exchange, new LlmModule().objectMapper(), System::nanoTime);
    }

    private static ChatRequest request(final ReasoningLevel level) {
        return new ChatRequest(List.of(new ChatMessage(ChatRole.USER, "hello")), null, null, level);
    }
}
