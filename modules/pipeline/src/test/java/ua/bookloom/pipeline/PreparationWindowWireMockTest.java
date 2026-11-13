package ua.bookloom.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.pipeline.WireMockProvider.Measured;
import ua.bookloom.pipeline.context.ModelWindows;
import ua.bookloom.pipeline.prompt.ChatRequests;
import ua.bookloom.pipeline.prompt.OutputLimit;
import ua.bookloom.pipeline.prompt.PromptName;
import ua.bookloom.pipeline.run.JobModelCalls;

/**
 * One window for every call to a model, at the HTTP seam in both dialects (design D20): a preparation call made after
 * the server's facts were read at Start is planned for the window the server reports, sent as Ollama's {@code num_ctx},
 * and an OpenAI-compatible body carries no size at all.
 */
class PreparationWindowWireMockTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String REPLY = "{\"candidates\":[]}";
    private static final String OLLAMA_SHOW = """
            {"details":{"family":"mistral3"},"model_info":{"general.architecture":"mistral3",\
            "mistral3.context_length":8192}}""";
    private static final String LM_STUDIO_MODEL = """
            {"id":"%s","type":"llm","state":"loaded","loaded_context_length":8192,"max_context_length":131072}""".formatted(WireMockProvider.MODEL_ID);

    @Test
    void contextSize_modelLoadedAt8192_preparationCallSends8192_ollamaNative() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OLLAMA)) {
            provider.stubPost("/api/show", OLLAMA_SHOW);
            provider.stubGetJson("/api/ps", "{\"models\":[]}");
            provider.stubAlways(provider.reply(REPLY, Duration.ZERO));

            final JsonNode body = prescanBody(provider, 8192);

            assertThat(body.path("options").path("num_ctx").asInt()).isEqualTo(8192);
            assertThat(body.path("truncate").isBoolean()).isTrue();
            assertThat(body.path("truncate").asBoolean(true)).isFalse();
            assertThat(body.has("keep_alive")).isFalse();
        }
    }

    // The native client sets the window on every request, so the window Ollama last loaded the model with does not
    // bind the next call: the model's 131,072 is held to 16,384, and Ollama reloads it at that size.
    @Test
    void contextSize_ollamaLoadedAt4096_isNotBoundByIt() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OLLAMA)) {
            provider.stubPost("/api/show", OLLAMA_SHOW.replace("8192", "131072"));
            provider.stubGetJson(
                    "/api/ps",
                    "{\"models\":[{\"name\":\"" + WireMockProvider.MODEL_ID + "\",\"context_length\":4096}]}");
            provider.stubAlways(provider.reply(REPLY, Duration.ZERO));

            final JsonNode body = prescanBody(provider, 16384);

            assertThat(body.path("options").path("num_ctx").asInt()).isEqualTo(16384);
        }
    }

    // LM Studio fixes the window when it loads the model: the call is planned for 8192 and the body names no size.
    @Test
    void contextSize_modelLoadedAt8192_preparationCallSends8192_openAiCompatible() {
        try (WireMockProvider provider = new WireMockProvider(ProviderKind.OPENAI_COMPATIBLE)) {
            provider.stubGetJson("/api/v0/models/" + WireMockProvider.MODEL_ID, LM_STUDIO_MODEL);
            provider.stubAlways(provider.reply(REPLY, Duration.ZERO));

            final JsonNode body = prescanBody(provider, 8192);

            assertThat(body.has("num_ctx")).isFalse();
            assertThat(body.has("n_ctx")).isFalse();
            assertThat(body.has("context_length")).isFalse();
            assertThat(body.path("max_tokens").asInt()).isLessThanOrEqualTo(4096);
        }
    }

    private static JsonNode prescanBody(final WireMockProvider provider, final int expectedWindow) {
        final Measured measured = provider.measured();
        final Integer detected =
                measured.capabilities().detectedTokens(measured.selection()).orElse(null);
        final int window = ModelWindows.of(measured.model(), detected);
        final JobModelCalls calls = new JobModelCalls(
                onSent -> {
                    onSent.run();
                    return measured.model();
                },
                event -> {},
                Clock.systemUTC(),
                "uk",
                window);

        calls.call(
                CallKind.PRESCAN,
                null,
                ChatRequests.build(
                        PromptName.PRESCAN,
                        List.of(new ChatMessage(ChatRole.SYSTEM, "Find names."), new ChatMessage(ChatRole.USER, "x")),
                        new OutputLimit(4000, 8000),
                        false));

        assertThat(window).isEqualTo(expectedWindow);
        return json(provider.chatBodies().getFirst());
    }

    private static JsonNode json(final String body) {
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException cause) {
            throw new AssertionError("the request body is not JSON: " + body, cause);
        }
    }
}
