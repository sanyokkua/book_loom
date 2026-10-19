package ua.bookloom.pipeline;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
    // Bound to loopback, not the wildcard: on macOS a wildcard bind succeeds on a port where another program (an IDE's
    // built-in server) already listens on 127.0.0.1, and a client then reaches that program instead of this stub.
    private final WireMockServer server =
            new WireMockServer(WireMockConfiguration.options().dynamicPort().bindAddress("127.0.0.1"));

    WireMockProvider(final ProviderKind kind) {
        this.kind = Objects.requireNonNull(kind, "kind");
        server.start();
    }

    String chatPath() {
        return kind == ProviderKind.OLLAMA ? "/api/chat" : "/v1/chat/completions";
    }

    ChatModel model(final Duration requestTimeout, final RetryPolicy.Sleeper sleeper) {
        return chatModels(requestTimeout, sleeper, 1).getFirst();
    }

    /**
     * Two chat models built from one injector, so both go through the one {@code InferenceGate} that a real run and
     * a review retry share.
     */
    TwoModels twoModels(final Duration requestTimeout) {
        final List<ChatModel> models = chatModels(requestTimeout, ignored -> {}, 2);
        return new TwoModels(models.get(0), models.get(1));
    }

    /** The two models of {@link #twoModels(Duration)}. */
    record TwoModels(ChatModel first, ChatModel second) {}

    private List<ChatModel> chatModels(
            final Duration requestTimeout, final RetryPolicy.Sleeper sleeper, final int count) {
        final URI base = URI.create(kind == ProviderKind.OLLAMA ? server.baseUrl() : server.baseUrl() + "/v1");
        final RetryPolicy policy = new RetryPolicy(Clock.systemUTC(), () -> 0.5, sleeper);
        final Injector injector = Guice.createInjector(Modules.override(new LlmModule())
                .with(binder -> binder.bind(RetryPolicy.class).toInstance(policy)));
        injector.getInstance(ProviderConfigs.class)
                .register(new ProviderConfig(PROVIDER_ID, kind, base, Duration.ofSeconds(2), requestTimeout));
        final ChatModelFactory factory = injector.getInstance(ChatModelFactory.class);
        return IntStream.range(0, count)
                .mapToObj(index -> Objects.requireNonNull(
                        factory.create(new ModelSelection(PROVIDER_ID, "test-model"))
                                .data(),
                        "chat model"))
                .toList();
    }

    /** A reply carrying {@code content} as the assistant message, after a delay. */
    ResponseDefinitionBuilder reply(final String content, final Duration delay) {
        final ResponseDefinitionBuilder response = aResponse().withStatus(200).withBody(envelope(content));
        return delay.isZero() ? response : response.withFixedDelay((int) delay.toMillis());
    }

    /** A reply that stopped at the output cap: {@code content} is cut and the finish reason is {@code length}. */
    ResponseDefinitionBuilder replyCut(final String content) {
        return aResponse().withStatus(200).withBody(envelope(content, "length"));
    }

    /**
     * A reply carrying {@code content} and the token usage in this dialect's own fields: Ollama's
     * {@code prompt_eval_count}, {@code eval_count} and {@code eval_duration}, or the OpenAI-compatible
     * {@code usage.prompt_tokens} and {@code usage.completion_tokens}, which report no generation time.
     */
    ResponseDefinitionBuilder replyWithUsage(
            final String content, final int promptTokens, final int completionTokens, final long evalNanos) {
        final Map<String, Object> body = new LinkedHashMap<>(parsed(envelope(content)));
        if (kind == ProviderKind.OLLAMA) {
            body.put("prompt_eval_count", promptTokens);
            body.put("eval_count", completionTokens);
            body.put("eval_duration", evalNanos);
        } else {
            body.put("usage", Map.of("prompt_tokens", promptTokens, "completion_tokens", completionTokens));
        }
        try {
            return aResponse().withStatus(200).withBody(MAPPER.writeValueAsString(body));
        } catch (JsonProcessingException cause) {
            throw new AssertionError("could not encode provider reply", cause);
        }
    }

    private static Map<String, Object> parsed(final String json) {
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException cause) {
            throw new AssertionError("could not decode provider reply", cause);
        }
    }

    /** A status-only answer with no body, as an overloaded server or a proxy in front of it gives. */
    ResponseDefinitionBuilder failure(final int status) {
        return aResponse().withStatus(status);
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
        return envelope(content, "stop");
    }

    private String envelope(final String content, final String finish) {
        try {
            return MAPPER.writeValueAsString(
                    kind == ProviderKind.OLLAMA
                            ? Map.of(
                                    "model",
                                    "test-model",
                                    "message",
                                    Map.of("role", "assistant", "content", content),
                                    "done_reason",
                                    finish)
                            : Map.of(
                                    "model",
                                    "test-model",
                                    "choices",
                                    List.of(Map.of(
                                            "message",
                                            Map.of("role", "assistant", "content", content),
                                            "finish_reason",
                                            finish))));
        } catch (JsonProcessingException cause) {
            throw new AssertionError("could not encode provider reply", cause);
        }
    }
}
