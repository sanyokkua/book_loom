package ua.bookloom.llm.engine;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.provider.EndpointPaths;

/**
 * One detection's GETs: it counts them, keeps the root's {@code /v1/models} reply so the model-list step can reuse it,
 * and stops asking once a failure says the server cannot be asked at all. Confined to the thread that detects.
 */
@Slf4j
final class ProbeRun {

    // Failures that say no further probe can be answered: nothing listens, no key, or the person stopped it.
    private static final Set<ErrorCode> STOPPING =
            EnumSet.of(ErrorCode.unreachable, ErrorCode.missingCredential, ErrorCode.cancelled);
    private static final String ROOT_MODELS = "v1/models";

    private final HttpExchange exchange;
    private final BaseUrlRoot address;
    private int count;
    private @Nullable AppError stopped;
    private boolean rootModelsAsked;
    private @Nullable HttpReply rootModels;

    ProbeRun(final HttpExchange exchange, final BaseUrlRoot address) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.address = Objects.requireNonNull(address, "address");
    }

    /** One GET at the server's root, or empty when nothing answered. */
    Optional<HttpReply> atRoot(final String path) {
        return send(address.root(), path);
    }

    /** The root's {@code /v1/models}, asked at most once. */
    Optional<HttpReply> rootModels() {
        if (!rootModelsAsked) {
            rootModelsAsked = true;
            rootModels = send(address.root(), ROOT_MODELS).orElse(null);
        }
        return Optional.ofNullable(rootModels);
    }

    /** The base's model list: the root's {@code /v1/models} reply when the base is the root or its {@code /v1}. */
    Optional<HttpReply> baseModels() {
        log.debug("Engine model-list probe reuses root reply={}", address.baseIsRootV1());
        return address.baseIsRootV1()
                ? rootModels()
                : send(address.base(), EndpointPaths.modelsPath(address.base(), "models"));
    }

    /** How many GETs were sent. */
    int count() {
        return count;
    }

    /** The failure that stopped the probes, or null while they may go on. */
    @Nullable
    AppError stopped() {
        return stopped;
    }

    private Optional<HttpReply> send(final ProviderConfig config, final String path) {
        if (stopped != null) {
            return Optional.empty();
        }
        count++;
        final Result<HttpReply> reply = exchange.get(config, path);
        final AppError failure = reply.error();
        if (failure != null) {
            log.debug("Engine probe path={} status=none code={} stops={}", path, failure.code(), isStopping(failure));
            stopped = isStopping(failure) ? failure : null;
            return Optional.empty();
        }
        final HttpReply answered = Objects.requireNonNull(reply.data(), "reply");
        log.debug(
                "Engine probe path={} status={} length={}",
                path,
                answered.status(),
                answered.body().length());
        return Optional.of(answered);
    }

    private static boolean isStopping(final AppError failure) {
        return STOPPING.contains(failure.code());
    }
}
