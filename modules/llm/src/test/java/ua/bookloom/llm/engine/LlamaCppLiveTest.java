package ua.bookloom.llm.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.VerificationPolicy;
import ua.bookloom.llm.testing.LiveServers;

/** Proves the llama-server rules against a real llama-server whose address is in the variable. */
@Tag("liveLocal")
@EnabledIfEnvironmentVariable(named = "BOOKLOOM_LIVE_LLAMACPP_URL", matches = ".+")
class LlamaCppLiveTest {

    private static final String PROVIDER = "live-llamacpp";
    private static final String MODEL_ENV = "BOOKLOOM_LIVE_LLAMACPP_MODEL";

    @Test
    void detect_realLlamaServer_isLlamaServer() {
        final ProviderConfig config = config();
        final Result<EngineDetection> result = LiveServers.verifier(config).detectEngine(config);

        assertThat(result.isOk()).as("detection: " + result.error()).isTrue();
        assertThat(Objects.requireNonNull(result.data()).engine()).isEqualTo(ServerEngine.LLAMA_CPP);
    }

    // The chat template in /props is what decides whether enable_thinking is sent at all.
    @Test
    void props_realLlamaServer_reportsABuildAndATemplateField() throws Exception {
        final HttpResponse<String> reply = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create(root() + "/props"))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        final JsonNode props = new ObjectMapper().readTree(reply.body());

        assertThat(reply.statusCode()).isEqualTo(200);
        assertThat(props.path("build_info").asText()).as("build_info").isNotBlank();
        assertThat(props.has("chat_template")).as("chat_template present").isTrue();
    }

    // A draft with thinking off is accepted and answers with no reasoning markup left in the reply.
    @Test
    void draft_thinkingOff_answersWithoutReasoningMarkup() {
        final String reply = draft(ReasoningLevel.OFF);

        assertThat(reply).isNotBlank().doesNotContain("<think>").doesNotContain("</think>");
    }

    // A gpt-oss-style model takes reasoning_effort; any other model skips this case.
    @Test
    void draft_gptOssStyleModel_acceptsReasoningLow() {
        final Injector injector = LiveServers.injector(config(), Map.of());
        Assumptions.assumeTrue(
                modelId(injector).toLowerCase(java.util.Locale.ROOT).contains("gpt-oss"), "the model is not gpt-oss");

        assertThat(draft(ReasoningLevel.LOW)).isNotBlank();
    }

    private String draft(final ReasoningLevel level) {
        final ProviderConfig config = config();
        final Injector injector = LiveServers.injector(config, Map.of());
        final String model = modelId(injector);
        final ProviderVerifier verifier = injector.getInstance(ProviderVerifier.class);
        verifier.verify(new ModelSelection(PROVIDER, model), VerificationPolicy.FULL);
        final Result<ChatResponse> result =
                LiveServers.chatModel(injector, PROVIDER, model).chat(LiveServers.draft(level));
        assertThat(result.isOk()).as("llama-server draft: " + result.error()).isTrue();
        return Objects.requireNonNull(result.data()).content();
    }

    private static String modelId(final Injector injector) {
        final String named = System.getenv(MODEL_ENV);
        return named != null && !named.isBlank() ? named : LiveServers.firstListedModel(injector, PROVIDER);
    }

    private static String root() {
        return LiveServers.requireUrl("BOOKLOOM_LIVE_LLAMACPP_URL");
    }

    private static ProviderConfig config() {
        return LiveServers.provider(PROVIDER, ProviderKind.OPENAI_COMPATIBLE, root(), null);
    }
}
