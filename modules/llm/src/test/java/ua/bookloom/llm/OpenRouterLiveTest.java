package ua.bookloom.llm;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.StageOutcome;
import ua.bookloom.api.llm.StageStatus;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.api.llm.VerificationReport;
import ua.bookloom.api.llm.VerificationStage;
import ua.bookloom.llm.testing.LiveServers;

/**
 * Proves the OpenRouter rules against the real service, with at most two chat requests (both on a free model or a
 * wrong id) so the free quota is not burned. The key is read from OPENROUTER_API_KEY by the provider's credential
 * reference; its value is never put in a message, an assertion or a log line.
 */
@Tag("liveLocal")
class OpenRouterLiveTest {

    private static final String KEY_VARIABLE = "OPENROUTER_API_KEY";
    private static final String PROVIDER = "live-openrouter";
    private static final String ADDRESS = "https://openrouter.ai/api/v1";
    private static final String WRONG_MODEL = "nonexistent/model-xyz:free";
    private static final int MAX_TOKENS = 200;
    private static final String PROMPT = "Translate to Ukrainian: quick amber lantern whispers beyond silver hills"
            + " while gentle clocks forget seven crooked rivers and mellow foxes carry paper moons across"
            + " velvet meadows toward a distant humming harbour";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();

    @BeforeEach
    void start() {
        Assumptions.assumeTrue(
                System.getenv(KEY_VARIABLE) != null
                        && !System.getenv(KEY_VARIABLE).isBlank(),
                KEY_VARIABLE + " is not set");
        final Logger bookloom = (Logger) LoggerFactory.getLogger("ua.bookloom");
        bookloom.setLevel(Level.DEBUG);
        logged.start();
        bookloom.addAppender(logged);
    }

    @AfterEach
    void stop() {
        ((Logger) LoggerFactory.getLogger("ua.bookloom")).detachAppender(logged);
    }

    // The engine is detected as OpenRouter, the connection and models stages pass, and every listed id is a text model.
    @Test
    void verify_realOpenRouter_detectsEngineAndListsOnlyTextModels() throws Exception {
        final ProviderConfig draft = provider(ServerEngine.GENERIC);
        final EngineDetection detection = Objects.requireNonNull(
                LiveServers.verifier(draft).detectEngine(draft).data(), "detection");
        assertThat(detection.engine()).isEqualTo(ServerEngine.OPENROUTER);

        final Injector injector = injector(provider(detection.engine()));
        final VerificationReport report = Objects.requireNonNull(
                injector.getInstance(ProviderVerifier.class)
                        .verify(
                                provider(detection.engine()),
                                smallFreeReasoningModel(),
                                VerificationPolicy.CONNECTION_AND_MODELS)
                        .data(),
                "report");
        assertThat(stage(report, VerificationStage.CONNECTION).status()).isEqualTo(StageStatus.PASSED);
        final StageOutcome models = stage(report, VerificationStage.MODELS);
        assertThat(models.status()).isEqualTo(StageStatus.PASSED);

        final List<ModelInfo> infos = Objects.requireNonNull(
                injector.getInstance(ua.bookloom.api.llm.ModelCatalog.class)
                        .listModels(PROVIDER)
                        .data(),
                "models");
        final List<String> listed = infos.stream().map(ModelInfo::id).toList();
        assertThat(listed).isNotEmpty();
        final List<String> nonText = nonTextIds();
        assertThat(listed.stream().filter(nonText::contains)).isEmpty();
    }

    // A tiny draft on a free model returns text or a classified error, and its call line names the host and no reply.
    @Test
    void chat_freeModel_answersOrIsClassifiedAndLogsServedFields() throws Exception {
        final String model = smallFreeReasoningModel();
        final Injector injector = injector(provider(ServerEngine.OPENROUTER));
        final ChatModel chat = LiveServers.chatModel(injector, PROVIDER, model);

        final Result<ChatResponse> result = chat.chat(request());

        final AppError error = result.error();
        System.out.println("LIVE chat model=" + model + " result=" + (result.isOk() ? "ok" : error));
        Assumptions.assumeFalse(error != null && error.code() == ErrorCode.rateLimited, "free model rate limited");
        System.out.println("LIVE model=" + model + " result=" + (result.isOk() ? "ok" : error));
        if (result.isOk()) {
            assertThat(Objects.requireNonNull(result.data()).content()).isNotNull();
        } else {
            assertThat(Objects.requireNonNull(error).code()).isNotEqualTo(ErrorCode.internal);
            assertThat(String.valueOf(error.details())).doesNotContain("unreadable");
        }
        final String line = callLine();
        System.out.println("LIVE call line: " + line);
        assertThat(line).contains("servedProvider=").contains("servedModel=");
        if (result.isOk()) {
            assertThat(line)
                    .doesNotContain(
                            Objects.requireNonNull(result.data()).content().strip());
        }
    }

    // A model id OpenRouter does not know is a classified error, never internal or unreadable.
    @Test
    void chat_wrongModelId_isModelNotFoundOrValidation() {
        final Injector injector = injector(provider(ServerEngine.OPENROUTER));
        final ChatModel chat = LiveServers.chatModel(injector, PROVIDER, WRONG_MODEL);

        final Result<ChatResponse> result = chat.chat(request());

        final AppError error = Objects.requireNonNull(result.error(), "a wrong id must fail");
        System.out.println("LIVE wrong model error=" + error.code() + " title=" + error.title());
        assertThat(error.code()).isIn(ErrorCode.modelNotFound, ErrorCode.validation);
    }

    private String callLine() {
        final List<String> lines = new ArrayList<>();
        for (final ILoggingEvent event : logged.list) {
            if (event.getFormattedMessage().startsWith("Provider chat call ")) {
                lines.add(event.getFormattedMessage());
            }
        }
        assertThat(lines).as("call lines").isNotEmpty();
        return lines.get(lines.size() - 1);
    }

    private static StageOutcome stage(final VerificationReport report, final VerificationStage which) {
        return report.stages().stream()
                .filter(outcome -> outcome.stage() == which)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no stage " + which));
    }

    private static ProviderConfig provider(final ServerEngine engine) {
        final ProviderConfig base =
                LiveServers.provider(PROVIDER, ProviderKind.OPENAI_COMPATIBLE, ADDRESS, KEY_VARIABLE);
        return new ProviderConfig(
                base.id(),
                base.kind(),
                base.baseUrl(),
                base.connectTimeout(),
                base.requestTimeout(),
                null,
                base.auth(),
                null,
                KEY_VARIABLE,
                null,
                null,
                Map.of(),
                List.of(),
                engine,
                null,
                null,
                false);
    }

    private static Injector injector(final ProviderConfig config) {
        return LiveServers.injector(config, Map.of(KEY_VARIABLE, System.getenv(KEY_VARIABLE)));
    }

    private static ChatRequest request() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, PROMPT)),
                null,
                null,
                ReasoningLevel.OFF,
                null,
                null,
                MAX_TOKENS,
                null,
                ua.bookloom.api.pipeline.CallKind.DRAFT);
    }

    private static JsonNode listing() throws Exception {
        final HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(ADDRESS + "/models"))
                                .timeout(Duration.ofSeconds(30))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        return JSON.readTree(response.body()).path("data");
    }

    private static List<String> nonTextIds() throws Exception {
        return StreamSupport.stream(listing().spliterator(), false)
                .filter(row -> !emitsText(row))
                .map(row -> row.path("id").asText())
                .toList();
    }

    // A model that cannot emit text (image or embedding generators) is no translation model.
    private static boolean emitsText(final JsonNode row) {
        return StreamSupport.stream(
                        row.path("architecture").path("output_modalities").spliterator(), false)
                .anyMatch(node -> "text".equals(node.asText()));
    }

    private static boolean isTextOnlyOutput(final JsonNode row) {
        final Set<String> output = new java.util.HashSet<>();
        row.path("architecture").path("output_modalities").forEach(node -> output.add(node.asText()));
        return output.equals(Set.of("text"));
    }

    private static String smallFreeReasoningModel() throws Exception {
        final String id = StreamSupport.stream(listing().spliterator(), false)
                .filter(row -> row.path("id").asText().endsWith(":free"))
                .filter(OpenRouterLiveTest::isTextOnlyOutput)
                .filter(row -> supports(row, "reasoning") && supports(row, "max_tokens"))
                .min(Comparator.comparingLong(row -> row.path("context_length").asLong(Long.MAX_VALUE)))
                .map(row -> row.path("id").asText())
                .orElse(null);
        Assumptions.assumeTrue(id != null, "no free model with reasoning and max_tokens is listed");
        return Objects.requireNonNull(id);
    }

    private static boolean supports(final JsonNode row, final String parameter) {
        return StreamSupport.stream(row.path("supported_parameters").spliterator(), false)
                .anyMatch(node -> parameter.equals(node.asText()));
    }
}
