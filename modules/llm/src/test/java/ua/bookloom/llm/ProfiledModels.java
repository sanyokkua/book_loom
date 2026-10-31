package ua.bookloom.llm;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import ua.bookloom.api.llm.CallLedger;
import ua.bookloom.api.llm.ModelFacts;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.http.HttpClients;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.profile.BuiltInRows;
import ua.bookloom.llm.profile.ModelProfiles;
import ua.bookloom.llm.provider.ModelSettingsCache;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.provider.RefusalMemory;
import ua.bookloom.llm.retry.RetryPolicy;
import ua.bookloom.llm.testing.FakeModelSettingsRepository;

/**
 * Builds a gated model whose profile comes from the built-in rows, against one WireMock server, in either dialect, with
 * its refusals kept in a fake store; and reads back what the server received.
 */
final class ProfiledModels {

    /** The two dialects: how the provider is reached, where it chats, and what a short answer looks like. */
    enum Dialect {
        OLLAMA(
                ProviderKind.OLLAMA,
                ServerEngine.OLLAMA,
                "",
                "/api/chat",
                "{\"model\":\"m\",\"message\":{\"content\":" + "%s},\"done\":true,\"done_reason\":\"stop\"}"),
        LM_STUDIO(
                ProviderKind.OPENAI_COMPATIBLE,
                ServerEngine.LM_STUDIO,
                "/v1",
                "/api/v0/chat/completions",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":%s},\"finish_reason\":\"stop\"}]}");

        final ProviderKind kind;
        final ServerEngine engine;
        final String basePath;
        final String chatPath;
        final String replyTemplate;

        Dialect(
                final ProviderKind kind,
                final ServerEngine engine,
                final String basePath,
                final String chatPath,
                final String replyTemplate) {
            this.kind = kind;
            this.engine = engine;
            this.basePath = basePath;
            this.chatPath = chatPath;
            this.replyTemplate = replyTemplate;
        }

        /** A reply whose text is the given string, written as a JSON string. */
        String reply(final String text) {
            return replyTemplate.formatted(quoted(text));
        }
    }

    /** One stubbed answer: a status and a body. */
    record Reply(int status, String body) {}

    static final String PROVIDER = "office";
    private static final Duration WAIT = Duration.ofSeconds(5);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final WireMockServer server;
    private final FakeModelSettingsRepository repository = new FakeModelSettingsRepository();

    ProfiledModels(final WireMockServer server) {
        this.server = server;
    }

    FakeModelSettingsRepository repository() {
        return repository;
    }

    /** The settings stored for a model, or its defaults when none are. */
    ua.bookloom.api.llm.ModelSettings stored(final String modelId) {
        return repository.find(PROVIDER, modelId).data() == null
                ? ua.bookloom.api.llm.ModelSettings.defaults(PROVIDER, modelId)
                : java.util.Objects.requireNonNull(
                                repository.find(PROVIDER, modelId).data())
                        .orElse(ua.bookloom.api.llm.ModelSettings.defaults(PROVIDER, modelId));
    }

    GatedChatModel model(final Dialect dialect, final String modelId) {
        return model(dialect.kind, dialect.engine, dialect.basePath, modelId);
    }

    /** A model on a server of any engine, reached at the given path below the WireMock server. */
    GatedChatModel model(
            final ProviderKind kind, final ServerEngine engine, final String basePath, final String modelId) {
        final ProviderConfig config = new ProviderConfig(
                        "test", kind, URI.create(server.baseUrl() + basePath), WAIT, WAIT)
                .withEngine(engine);
        final HttpExchange exchange = new HttpExchange(new HttpClients());
        final ModelSettingsCache cache = new ModelSettingsCache(repository);
        return new GatedChatModel(
                new ProviderClientFactory(exchange, new LlmModule().objectMapper(), System::nanoTime).create(config),
                PROVIDER,
                modelId,
                new InferenceGate(),
                new RetryPolicy(CLOCK, () -> 0.5, ignored -> {}),
                cache::current,
                new RefusalMemory(cache),
                CallLedger.none(),
                new BuiltWrongLog(),
                new CapIgnoredLog(),
                stored -> new ModelProfiles(new BuiltInRows())
                        .resolve(config.effectiveEngine(), modelId, ModelFacts.unknown(), stored));
    }

    /** Answers the chat path with these replies in order, the last one for every later request. */
    void stub(final Dialect dialect, final Reply... replies) {
        IntStream.range(0, replies.length).forEach(step -> {
            final boolean last = step == replies.length - 1;
            server.stubFor(post(urlEqualTo(dialect.chatPath))
                    .inScenario("replies")
                    .whenScenarioStateIs(state(step))
                    .willReturn(aResponse().withStatus(replies[step].status()).withBody(replies[step].body()))
                    .willSetStateTo(state(last ? step : step + 1)));
        });
    }

    private static String state(final int step) {
        return step == 0 ? Scenario.STARTED : "step " + step;
    }

    /** The request bodies the chat path received, oldest first. */
    List<JsonNode> bodies(final Dialect dialect) {
        final List<JsonNode> all = new ArrayList<>(server.getAllServeEvents().stream()
                .filter(event -> event.getRequest().getUrl().equals(dialect.chatPath))
                .map(event -> read(event.getRequest().getBodyAsString()))
                .toList());
        Collections.reverse(all);
        return all;
    }

    /** The roles of a body's messages, in order. */
    static List<String> roles(final JsonNode body) {
        final List<String> roles = new ArrayList<>();
        body.path("messages").forEach(message -> roles.add(message.path("role").asText()));
        return roles;
    }

    /** Every message text of a body, joined by line breaks. */
    static String texts(final JsonNode body) {
        final List<String> texts = new ArrayList<>();
        body.path("messages")
                .forEach(message -> texts.add(message.path("content").asText()));
        return String.join("\n", texts);
    }

    private static JsonNode read(final String body) {
        try {
            return JSON.readTree(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static String quoted(final String text) {
        try {
            return JSON.writeValueAsString(text);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
