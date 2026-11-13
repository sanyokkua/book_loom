package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.google.inject.Guice;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.llm.SourceSize;
import ua.bookloom.api.pipeline.CallKind;
import ua.bookloom.llm.testing.FakeProviderConfigs;
import ua.bookloom.llm.testing.LlmTestStorageModule;

/** A model whose reply spent its cap thinking is given room to think on every later call, not only on the repeat. */
class ObservedThinkingTest {

    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final int CAP = 200;
    private static final String SPENT = "{\"choices\":[{\"index\":0,\"finish_reason\":\"length\",\"message\":"
            + "{\"role\":\"assistant\",\"content\":\"\",\"reasoning_content\":\"thinking…\"}}]}";
    private static final String ANSWER = "{\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":"
            + "{\"role\":\"assistant\",\"content\":\"done\"}}]}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void startServer() {
        server.start();
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("thinking")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(okJson(SPENT))
                .willSetStateTo("repeat"));
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("thinking")
                .whenScenarioStateIs("repeat")
                .willReturn(okJson(ANSWER))
                .willSetStateTo("later"));
        server.stubFor(post(urlEqualTo(CHAT_PATH))
                .inScenario("thinking")
                .whenScenarioStateIs("later")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(ANSWER)));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // IF the room were given only to the repeat, THEN every call to a model that always thinks would first spend its
    // cap and cost a second round trip; from the second call on, the first request must already carry the room.
    @Test
    void chat_afterAReplySpentItsCapThinking_theNextCallCarriesTheReserve() throws IOException {
        final ChatModel model = model();

        model.chat(draft());
        model.chat(draft());

        final List<Integer> caps = capsSent();
        assertThat(caps).hasSize(3);
        assertThat(caps.get(0)).as("first request").isEqualTo(CAP);
        assertThat(caps.get(2)).as("first request of the next call").isGreaterThan(caps.get(1));
    }

    private List<Integer> capsSent() throws IOException {
        final var requests = server.findAll(postRequestedFor(urlEqualTo(CHAT_PATH)));
        final List<Integer> caps = new java.util.ArrayList<>();
        for (final var request : requests) {
            final JsonNode body = mapper.readTree(request.getBodyAsString());
            final JsonNode cap =
                    body.has("max_completion_tokens") ? body.get("max_completion_tokens") : body.get("max_tokens");
            caps.add(Objects.requireNonNull(cap, "cap").asInt());
        }
        return caps;
    }

    private ChatModel model() {
        final FakeProviderConfigs configs = new FakeProviderConfigs();
        assertThat(configs.save(new ProviderConfig(
                                "thinker",
                                ProviderKind.OPENAI_COMPATIBLE,
                                URI.create(server.baseUrl() + "/v1"),
                                Duration.ofSeconds(2),
                                Duration.ofSeconds(5),
                                null,
                                ProviderAuth.NONE,
                                null,
                                null,
                                null,
                                null,
                                Map.of(),
                                List.of(),
                                ServerEngine.GENERIC,
                                ServerEngine.GENERIC,
                                null,
                                false))
                        .isOk())
                .isTrue();
        return Objects.requireNonNull(
                Guice.createInjector(new LlmModule(name -> null), new LlmTestStorageModule(configs))
                        .getInstance(ChatModelFactory.class)
                        .create(new ModelSelection("thinker", "vendor/thinking-model"))
                        .data(),
                "chat model");
    }

    private static ChatRequest draft() {
        return new ChatRequest(
                        List.of(new ChatMessage(ChatRole.USER, "Translate: a red door.")),
                        0.2,
                        null,
                        ReasoningLevel.OFF,
                        null,
                        null,
                        CAP,
                        null,
                        CallKind.DRAFT)
                .withSource(new SourceSize(40, 1));
    }
}
