package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelSettings;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.SamplingOverride;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.ThinkingOverride;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.ProfiledModels.Dialect;
import ua.bookloom.llm.ProfiledModels.Reply;

/**
 * Proves on the wire that a call's samplers come from the model's profile or the person's Custom values (design D20):
 * the profile's default is BookLoom's temperature per call kind, with a neutral value for each sampler an engine would
 * otherwise default to something else, and never the maker's values a row holds as data; Custom sends what was typed.
 */
class SamplingFromProfileTest {

    // A row that holds the maker's top-p 0.8 and top-k 20 as data, which the profile's default does not send.
    private static final String QWEN = "qwen2.5:14b";
    private static final double DRAFT_TEMPERATURE = 0.2;
    private static final String LLAMA_CHAT = "/v1/chat/completions";
    private static final String LLAMA_REPLY =
            "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"},\"finish_reason\":\"stop\"}]}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final ProfiledModels models = new ProfiledModels(server);
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void start() {
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void chat_samplingProfileDefault_sendsTheProfilesValues() {
        models.stub(Dialect.OLLAMA, new Reply(200, Dialect.OLLAMA.reply("ok")));

        models.model(Dialect.OLLAMA, QWEN).chat(draft());

        final JsonNode options = models.bodies(Dialect.OLLAMA).getFirst().path("options");
        assertThat(options.path("temperature").asDouble()).isEqualTo(DRAFT_TEMPERATURE);
        assertThat(options.has("top_p")).isFalse();
        assertThat(options.has("top_k")).isFalse();
        assertThat(options.has("min_p")).isFalse();
    }

    @Test
    void chat_samplingProfileDefaultOnLmStudio_sendsNeutralValuesNotTheMakers() {
        models.stub(Dialect.LM_STUDIO, new Reply(200, Dialect.LM_STUDIO.reply("ok")));

        models.model(Dialect.LM_STUDIO, QWEN).chat(draft());

        final JsonNode body = models.bodies(Dialect.LM_STUDIO).getFirst();
        assertThat(body.path("temperature").asDouble()).isEqualTo(DRAFT_TEMPERATURE);
        assertThat(body.path("top_p").asDouble()).isEqualTo(1.0);
        assertThat(body.path("top_k").asInt()).isZero();
        assertThat(body.path("min_p").asDouble()).isZero();
        assertThat(body.path("repeat_penalty").asDouble()).isEqualTo(1.0);
    }

    @Test
    void chat_samplingCustom_sendsTheTypedValues() {
        models.stub(Dialect.OLLAMA, new Reply(200, Dialect.OLLAMA.reply("ok")));
        storeCustom(SamplingOverride.custom(0.4, 20, 0.9, 0.05));

        models.model(Dialect.OLLAMA, QWEN).chat(draft());

        final JsonNode options = models.bodies(Dialect.OLLAMA).getFirst().path("options");
        assertThat(options.path("temperature").asDouble()).isEqualTo(0.4);
        assertThat(options.path("top_k").asInt()).isEqualTo(20);
        assertThat(options.path("top_p").asDouble()).isEqualTo(0.9);
        assertThat(options.path("min_p").asDouble()).isEqualTo(0.05);
    }

    @Test
    void chat_samplingCustomOnLmStudio_sendsTheTypedValues() {
        models.stub(Dialect.LM_STUDIO, new Reply(200, Dialect.LM_STUDIO.reply("ok")));
        storeCustom(SamplingOverride.custom(0.4, 20, 0.9, 0.05));

        models.model(Dialect.LM_STUDIO, QWEN).chat(draft());

        final JsonNode body = models.bodies(Dialect.LM_STUDIO).getFirst();
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.4);
        assertThat(body.path("top_k").asInt()).isEqualTo(20);
        assertThat(body.path("top_p").asDouble()).isEqualTo(0.9);
        assertThat(body.path("min_p").asDouble()).isEqualTo(0.05);
    }

    // A value left empty keeps the profile's: no min_p of the person's, and the per-kind temperature.
    @Test
    void chat_samplingCustomWithEmptyFields_keepsTheProfilesForThem() {
        models.stub(Dialect.OLLAMA, new Reply(200, Dialect.OLLAMA.reply("ok")));
        storeCustom(SamplingOverride.custom(null, 40, 0.9, null));

        models.model(Dialect.OLLAMA, QWEN).chat(draft());

        final JsonNode options = models.bodies(Dialect.OLLAMA).getFirst().path("options");
        assertThat(options.path("temperature").asDouble()).isEqualTo(DRAFT_TEMPERATURE);
        assertThat(options.path("top_k").asInt()).isEqualTo(40);
        assertThat(options.path("top_p").asDouble()).isEqualTo(0.9);
        assertThat(options.has("min_p")).isFalse();
    }

    @Test
    void chat_llamaServerRecommended_sendsNeutralSamplers() throws IOException {
        server.stubFor(post(urlEqualTo(LLAMA_CHAT)).willReturn(okJson(LLAMA_REPLY)));

        models.model(ProviderKind.OPENAI_COMPATIBLE, ServerEngine.LLAMA_CPP, "/v1", QWEN)
                .chat(draft());

        final JsonNode body =
                json.readTree(server.getAllServeEvents().getFirst().getRequest().getBodyAsString());
        assertThat(body.path("temperature").asDouble()).isEqualTo(DRAFT_TEMPERATURE);
        assertThat(body.path("top_p").asDouble()).isEqualTo(1.0);
        assertThat(body.path("top_k").asInt()).isZero();
        assertThat(body.path("min_p").asDouble()).isZero();
        assertThat(body.path("repeat_penalty").asDouble()).isEqualTo(1.0);
    }

    private void storeCustom(final SamplingOverride sampling) {
        models.repository()
                .save(ModelSettings.defaults(ProfiledModels.PROVIDER, QWEN)
                        .withOverrides(ThinkingOverride.RECOMMENDED, sampling));
    }

    private static ChatRequest draft() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.SYSTEM, "You translate."), new ChatMessage(ChatRole.USER, "door")),
                DRAFT_TEMPERATURE,
                null,
                ReasoningLevel.OFF,
                null,
                null,
                null,
                null,
                CallKind.DRAFT);
    }
}
