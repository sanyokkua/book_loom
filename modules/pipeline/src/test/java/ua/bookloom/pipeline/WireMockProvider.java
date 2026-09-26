package ua.bookloom.pipeline;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.ScenarioMappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.retry.RetryPolicy;

/** A real provider client pointed at a WireMock server, speaking either the Ollama-native or the OpenAI dialect. */
final class WireMockProvider implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROVIDER_ID = "wiremock";
    private static final long WAIT_SECONDS = 5;
    private static final long POLL_MILLIS = 5;

    private final ProviderKind kind;
    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    WireMockProvider(final ProviderKind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
        server.start();
    }

    String chatPath() {
        return kind == ProviderKind.OLLAMA ? "/api/chat" : "/v1/chat/completions";
    }

    ChatModel model(final Duration requestTimeout, final RetryPolicy.Sleeper sleeper) {
        final URI base = URI.create(kind == ProviderKind.OLLAMA ? server.baseUrl() : server.baseUrl() + "/v1");
        final RetryPolicy policy = new RetryPolicy(Clock.systemUTC(), () -> 0.5, sleeper);
        final Injector injector = Guice.createInjector(Modules.override(new LlmModule())
                .with(binder -> binder.bind(RetryPolicy.class).toInstance(policy)));
        injector.getInstance(ProviderConfigs.class)
                .register(new ProviderConfig(PROVIDER_ID, kind, base, Duration.ofSeconds(2), requestTimeout));
        return Objects.requireNonNull(
                injector.getInstance(ChatModelFactory.class)
                        .create(new ModelSelection(PROVIDER_ID, "test-model"))
                        .data(),
                "chat model");
    }

    /** A reply carrying {@code content} as the assistant message, after a delay. */
    ResponseDefinitionBuilder reply(final String content, final Duration delay) {
        final ResponseDefinitionBuilder response = aResponse().withStatus(200).withBody(envelope(content));
        return delay.isZero() ? response : response.withFixedDelay((int) delay.toMillis());
    }

    /** A reply carrying the strict one-field target object the engine asks for. */
    ResponseDefinitionBuilder target(final String target, final Duration delay) {
        return reply(TranslationJobTestSupport.targetReply(target), delay);
    }

    /** Answers the n-th chat request (counting from one) with the given reply; each earlier request must be stubbed. */
    void stubSequence(final List<ResponseDefinitionBuilder> replies) {
        for (int index = 0; index < replies.size(); index++) {
            final ScenarioMappingBuilder mapping = post(urlEqualTo(chatPath()))
                    .inScenario("chat")
                    .whenScenarioStateIs(index == 0 ? Scenario.STARTED : "request-" + index)
                    .willReturn(replies.get(index));
            server.stubFor(index + 1 < replies.size() ? mapping.willSetStateTo("request-" + (index + 1)) : mapping);
        }
    }

    void stubAlways(final ResponseDefinitionBuilder reply) {
        server.stubFor(post(urlEqualTo(chatPath())).willReturn(reply));
    }

    int chatRequests() {
        return server.findAll(postRequestedFor(urlEqualTo(chatPath()))).size();
    }

    List<String> chatBodies() {
        return server.findAll(postRequestedFor(urlEqualTo(chatPath()))).stream()
                .map(request -> request.getBodyAsString())
                .toList();
    }

    /** Waits until the server has received the given number of chat requests. */
    void awaitChatRequests(final int expected) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (chatRequests() < expected) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("provider received " + chatRequests() + " of " + expected + " requests");
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted waiting for provider requests", cause);
            }
        }
    }

    @Override
    public void close() {
        server.stop();
    }

    private String envelope(final String content) {
        try {
            return MAPPER.writeValueAsString(
                    kind == ProviderKind.OLLAMA
                            ? Map.of(
                                    "model", "test-model",
                                    "message", Map.of("role", "assistant", "content", content),
                                    "done_reason", "stop")
                            : Map.of(
                                    "model",
                                    "test-model",
                                    "choices",
                                    List.of(Map.of(
                                            "message",
                                            Map.of("role", "assistant", "content", content),
                                            "finish_reason",
                                            "stop"))));
        } catch (JsonProcessingException cause) {
            throw new AssertionError("could not encode provider reply", cause);
        }
    }
}
