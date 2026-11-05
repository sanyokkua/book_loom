package ua.bookloom.pipeline;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
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
import com.github.tomakehurst.wiremock.extension.ResponseTransformerV2;
import com.github.tomakehurst.wiremock.http.Response;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.stream.IntStream;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.api.pipeline.ProviderProbe;
import ua.bookloom.llm.LlmModule;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.persistence.InMemoryPersistenceModule;

/**
 * A real provider client pointed at a WireMock server, speaking either the Ollama-native or the OpenAI dialect. Public
 * so the review package can test a desk retry at the HTTP seam.
 */
public final class WireMockProvider implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PROVIDER_ID = "wiremock";
    /**
     * A model with a built-in row that declares no thinking control and starts on the schema rung, so the requests show
     * the answer schema and no reasoning field (design D20); a model with no row would start on the prompt-only rung.
     */
    static final String MODEL_ID = "mistral-small3.2:24b";

    private static final long WAIT_SECONDS = 5;
    private static final long POLL_MILLIS = 5;

    private final ProviderKind kind;
    private final String modelId;
    private final ModelSelection selection;
    // Bound to loopback, not the wildcard: on macOS a wildcard bind succeeds on a port where another program (an IDE's
    // built-in server) already listens on 127.0.0.1, and a client then reaches that program instead of this stub.
    private static final String RESPONDER = "bookloom-responder";
    private final AtomicReference<Function<String, String>> responder = new AtomicReference<>();
    private final WireMockServer server = new WireMockServer(WireMockConfiguration.options()
            .dynamicPort()
            .bindAddress("127.0.0.1")
            .extensions(new Responder()));

    public WireMockProvider(final ProviderKind kind) {
        this(kind, MODEL_ID);
    }

    /** A stand-in serving {@code modelId}, whose built-in row decides the profile the run reads. */
    WireMockProvider(final ProviderKind kind, final String modelId) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.selection = new ModelSelection(PROVIDER_ID, modelId);
        server.start();
    }

    String chatPath() {
        return kind == ProviderKind.OLLAMA ? "/api/chat" : "/v1/chat/completions";
    }

    public ChatModel model(final Duration requestTimeout, final RetryPolicy.Sleeper sleeper) {
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

    /**
     * A model and the capability reader of the same injector, so the server's facts the reader reads at Start are the
     * session facts the model's profile is resolved from.
     */
    record Measured(ChatModel model, ModelCapabilities capabilities, ModelSelection selection) {}

    Measured measured() {
        final Injector injector = injector(Duration.ofSeconds(WAIT_SECONDS), ignored -> {});
        final ChatModel model = Objects.requireNonNull(
                injector.getInstance(ChatModelFactory.class).create(selection).data(), "chat model");
        return new Measured(model, injector.getInstance(ModelCapabilities.class), selection);
    }

    /** Answers {@code POST path} with the JSON body. */
    void stubPost(final String path, final String json) {
        server.stubFor(post(urlEqualTo(path)).willReturn(okJson(json)));
    }

    /** Answers {@code GET path} with the JSON body. */
    void stubGetJson(final String path, final String json) {
        server.stubFor(get(urlEqualTo(path)).willReturn(okJson(json)));
    }

    private List<ChatModel> chatModels(
            final Duration requestTimeout, final RetryPolicy.Sleeper sleeper, final int count) {
        final ChatModelFactory factory = injector(requestTimeout, sleeper).getInstance(ChatModelFactory.class);
        return IntStream.range(0, count)
                .mapToObj(index ->
                        Objects.requireNonNull(factory.create(selection).data(), "chat model"))
                .toList();
    }

    private Injector injector(final Duration requestTimeout, final RetryPolicy.Sleeper sleeper) {
        final URI base = URI.create(kind == ProviderKind.OLLAMA ? server.baseUrl() : server.baseUrl() + "/v1");
        final RetryPolicy policy = new RetryPolicy(Clock.systemUTC(), () -> 0.5, sleeper);
        final Injector injector =
                Guice.createInjector(Modules.override(new LlmModule(), new InMemoryPersistenceModule())
                        .with(binder -> binder.bind(RetryPolicy.class).toInstance(policy)));
        // The OpenAI-compatible stand-in is LM Studio: a recognised engine gets the schema and the reasoning switch on
        // every call, where a generic server would get only what a check had proved.
        injector.getInstance(ProviderConfigs.class)
                .save(new ProviderConfig(PROVIDER_ID, kind, base, Duration.ofSeconds(2), requestTimeout)
                        .withEngine(kind == ProviderKind.OLLAMA ? ServerEngine.OLLAMA : ServerEngine.LM_STUDIO));
        return injector;
    }

    /** The probe the window and the command line give a run, checking this server with the real verifier. */
    ProviderProbe probe() {
        return ProviderProbe.of(
                injector(Duration.ofSeconds(WAIT_SECONDS), ignored -> {}).getInstance(ProviderVerifier.class),
                selection);
    }

    /** Answers the n-th GET of {@code path} (counting from one) with the n-th reply; the last answers every later one. */
    void stubGet(final String path, final ResponseDefinitionBuilder... replies) {
        for (int index = 0; index < replies.length; index++) {
            final ScenarioMappingBuilder mapping = get(urlEqualTo(path))
                    .inScenario("get " + path)
                    .whenScenarioStateIs(index == 0 ? Scenario.STARTED : "get-" + index)
                    .willReturn(replies[index]);
            server.stubFor(index + 1 < replies.length ? mapping.willSetStateTo("get-" + (index + 1)) : mapping);
        }
    }

    int getRequests(final String path) {
        return server.findAll(getRequestedFor(urlEqualTo(path))).size();
    }

    /** Every request the server received, oldest first, as {@code METHOD path}. */
    List<String> servedInOrder() {
        return server.findAll(anyRequestedFor(anyUrl())).stream()
                .map(request -> request.getMethod() + " " + request.getUrl())
                .toList();
    }

    /** A reply carrying {@code content} as the assistant message, after a delay. */
    public ResponseDefinitionBuilder reply(final String content, final Duration delay) {
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

    /** A single-target reply, the shape a directed fix and a revision are read in. */
    public ResponseDefinitionBuilder singleTarget(final String target, final Duration delay) {
        return reply(TestReplies.singleTargetReply(target), delay);
    }

    /** A translate-family reply of one item, the shape a draft, its re-ask and its repair are read in. */
    public ResponseDefinitionBuilder target(final String target, final Duration delay) {
        return reply(TranslationJobTestSupport.targetReply(target), delay);
    }

    /** Answers the n-th chat request (counting from one) with the given reply; each earlier request must be stubbed. */
    public void stubSequence(final List<ResponseDefinitionBuilder> replies) {
        for (int index = 0; index < replies.size(); index++) {
            final ScenarioMappingBuilder mapping = post(urlEqualTo(chatPath()))
                    .inScenario("chat")
                    .whenScenarioStateIs(index == 0 ? Scenario.STARTED : "request-" + index)
                    .willReturn(replies.get(index));
            server.stubFor(index + 1 < replies.size() ? mapping.willSetStateTo("request-" + (index + 1)) : mapping);
        }
    }

    /**
     * Answers every chat request with the content {@code bodyToContent} makes from the request body, wrapped in this
     * dialect's envelope; the function sees what the client really sent.
     */
    public void stubResponder(final Function<String, String> bodyToContent) {
        responder.set(Objects.requireNonNull(bodyToContent, "bodyToContent"));
        server.stubFor(post(urlEqualTo(chatPath()))
                .willReturn(aResponse().withStatus(200).withTransformers(RESPONDER)));
    }

    void stubAlways(final ResponseDefinitionBuilder reply) {
        server.stubFor(post(urlEqualTo(chatPath())).willReturn(reply));
    }

    public int chatRequests() {
        return server.findAll(postRequestedFor(urlEqualTo(chatPath()))).size();
    }

    public List<String> chatBodies() {
        return server.findAll(postRequestedFor(urlEqualTo(chatPath()))).stream()
                .map(LoggedRequest::getBodyAsString)
                .toList();
    }

    /** Waits until the server has received the given number of chat requests. */
    void awaitChatRequests(final int expected) {
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(WAIT_SECONDS);
        while (chatRequests() < expected) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("provider received " + chatRequests() + " of " + expected + " requests");
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(POLL_MILLIS));
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt();
                throw new AssertionError("interrupted waiting for provider requests");
            }
        }
    }

    @Override
    public void close() {
        server.stop();
    }

    private final class Responder implements ResponseTransformerV2 {
        @Override
        public Response transform(final Response response, final ServeEvent event) {
            final Function<String, String> make = responder.get();
            return make == null
                    ? response
                    : Response.Builder.like(response)
                            .body(envelope(make.apply(event.getRequest().getBodyAsString())))
                            .build();
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }

        @Override
        public String getName() {
            return RESPONDER;
        }
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
                                    modelId,
                                    "message",
                                    Map.of("role", "assistant", "content", content),
                                    "done_reason",
                                    finish)
                            : Map.of(
                                    "model",
                                    modelId,
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
