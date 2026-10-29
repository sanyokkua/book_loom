package ua.bookloom.llm.client.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.provider.EndpointPaths;

/**
 * Where one provider's chat is posted. LM Studio's own route, {@code /api/v0/chat/completions} at the server's root,
 * takes the same body and adds its timing to the reply; an LM Studio without that route answers with a body that is not
 * JSON or with {@code {"error":"Unexpected endpoint…"}}, and from then on the provider's chat goes to
 * {@code <baseUrl>/chat/completions} for the session. Every other engine, and a provider with its own chat path, posts
 * to the OpenAI route at once.
 */
@Slf4j
final class ChatRoute {

    private static final String CHAT_PATH = "/chat/completions";
    private static final int STATUS_CLASS = 100;
    private static final int SUCCESS_CLASS = 2;
    private static final int NOT_FOUND = 404;
    private static final int METHOD_NOT_ALLOWED = 405;
    private static final String LM_STUDIO_CHAT_PATH = "/api/v0/chat/completions";
    private static final String UNEXPECTED_ENDPOINT = "Unexpected endpoint";

    private final ProviderConfig config;
    private final HttpExchange exchange;
    private final ObjectMapper mapper;
    private final AtomicBoolean fellBack = new AtomicBoolean();

    ChatRoute(final ProviderConfig config, final HttpExchange exchange, final ObjectMapper mapper) {
        this.config = Objects.requireNonNull(config, "config");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Posts one chat body to the provider's current route, and to the OpenAI route once more when LM Studio's own
     * route turns out to be missing.
     *
     * @param chatConfig the provider with this call's timeouts
     * @param body the serialized chat body
     * @return the raw reply of the route that answered, or the transport failure
     */
    Result<HttpReply> post(final ProviderConfig chatConfig, final String body) {
        final Optional<ProviderConfig> native0 = nativeRoot(chatConfig);
        if (native0.isEmpty()) {
            log.debug("Chat route taken provider={} route=openai path={}", config.id(), openAiPath());
            return exchange.post(chatConfig, openAiPath(), body);
        }
        log.debug("Chat route taken provider={} route=lm-studio path={}", config.id(), LM_STUDIO_CHAT_PATH);
        final Result<HttpReply> reply = exchange.post(native0.orElseThrow(), LM_STUDIO_CHAT_PATH, body);
        final HttpReply answered = reply.data();
        if (reply.isErr() || answered == null || !isMissingRoute(answered)) {
            return reply;
        }
        if (fellBack.compareAndSet(false, true)) {
            log.info(
                    "LM Studio has no own chat route, the OpenAI route is used for the session provider={} status={}",
                    config.id(),
                    answered.status());
        }
        return exchange.post(chatConfig, openAiPath(), body);
    }

    /**
     * The full address the next chat call is posted to, for the Test dialog's request line.
     *
     * @return the address with no credentials in it
     */
    URI url() {
        final Optional<ProviderConfig> native0 = nativeRoot(config);
        final ProviderConfig target = native0.orElse(config);
        final String path = native0.isPresent() ? LM_STUDIO_CHAT_PATH : openAiPath();
        final URI base = target.baseUrl();
        final String basePath = base.getPath() == null ? "" : base.getPath().replaceFirst("/+$", "");
        return URI.create(base.getScheme() + "://" + base.getHost() + (base.getPort() < 0 ? "" : ":" + base.getPort())
                + basePath + "/" + path.replaceFirst("^/+", ""));
    }

    private String openAiPath() {
        return EndpointPaths.chatPath(config, CHAT_PATH);
    }

    private Optional<ProviderConfig> nativeRoot(final ProviderConfig target) {
        final boolean lmStudio = config.effectiveEngine() == ServerEngine.LM_STUDIO;
        final boolean overridden =
                config.chatPath() != null && !config.chatPath().isBlank();
        if (!lmStudio || overridden || fellBack.get()) {
            return Optional.empty();
        }
        return EndpointPaths.atHostRoot(target);
    }

    // LM Studio's own "Unexpected endpoint" answer says the route is missing; so does a body that is not JSON with a
    // status a missing route gives (a success, 404, 405). A sign-in, rate or server failure is the request's, not the
    // route's, and is mapped as usual.
    private boolean isMissingRoute(final HttpReply reply) {
        final boolean routeStatus = reply.status() / STATUS_CLASS == SUCCESS_CLASS
                || reply.status() == NOT_FOUND
                || reply.status() == METHOD_NOT_ALLOWED;
        final boolean missing = isUnexpectedEndpoint(reply.body()) || (routeStatus && !isJsonObject(reply.body()));
        log.debug("LM Studio chat route reply status={} missingRoute={}", reply.status(), missing);
        return missing;
    }

    private boolean isUnexpectedEndpoint(final String body) {
        return parsed(body)
                .map(node -> node.path("error").asText("").startsWith(UNEXPECTED_ENDPOINT))
                .orElse(false);
    }

    private boolean isJsonObject(final String body) {
        return parsed(body).map(JsonNode::isContainerNode).orElse(false);
    }

    private Optional<JsonNode> parsed(final String body) {
        try {
            return Optional.ofNullable(mapper.readTree(body));
        } catch (JsonProcessingException notJson) {
            return Optional.empty();
        }
    }
}
