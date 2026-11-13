package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.inject.Guice;
import com.google.inject.Injector;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatMessage;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ChatRole;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderAuth;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ReasoningLevel;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.testing.FakeProviderConfigs;
import ua.bookloom.llm.testing.LlmTestStorageModule;

/** Proves on the wire that an OpenRouter error sent inside a 200 reply is an outage, not an unreadable reply (D21). */
class OpenRouterAcceptanceTest {

    private static final String PROVIDER_ID = "openrouter-under-test";
    private static final String CHAT_PATH = "/api/v1/chat/completions";
    private static final String ERROR_IN_200 = "{\"error\":{\"code\":502,\"message\":\"Provider returned error\","
            + "\"metadata\":{\"error_type\":\"provider_unavailable\"}}}";

    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));
    private FakeProviderConfigs configs;

    @BeforeEach
    void startServer() {
        server.start();
        configs = new FakeProviderConfigs();
        server.stubFor(post(urlEqualTo(CHAT_PATH)).willReturn(okJson(ERROR_IN_200)));
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    // IF OpenRouter answers 200 with only an error object, THEN the call fails as an upstream outage with the
    // reason provider-unavailable.
    @Test
    void chat_errorInside200_isUpstreamWithReasonProviderUnavailable() {
        assertThat(configs.save(openRouterProvider()).isOk()).isTrue();
        final Injector injector = Guice.createInjector(new LlmModule(name -> null), new LlmTestStorageModule(configs));
        final ChatModel model = Objects.requireNonNull(
                injector.getInstance(ChatModelFactory.class)
                        .create(new ModelSelection(PROVIDER_ID, "vendor/some-model"))
                        .data(),
                "chat model");

        final Result<ChatResponse> result = model.chat(draft());

        final AppError error = Objects.requireNonNull(result.error(), "call must fail");
        assertThat(error.code()).as("error code").isEqualTo(ErrorCode.upstream);
        assertThat(error.details()).as("details").contains("reason=provider-unavailable");
    }

    private ProviderConfig openRouterProvider() {
        return new ProviderConfig(
                PROVIDER_ID,
                ProviderKind.OPENAI_COMPATIBLE,
                URI.create(server.baseUrl() + "/api/v1"),
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
                ServerEngine.OPENROUTER,
                null,
                false);
    }

    private static ChatRequest draft() {
        return new ChatRequest(
                List.of(new ChatMessage(ChatRole.USER, "Translate: a red door.")),
                null,
                null,
                ReasoningLevel.OFF,
                null,
                null,
                512,
                null,
                null);
    }
}
