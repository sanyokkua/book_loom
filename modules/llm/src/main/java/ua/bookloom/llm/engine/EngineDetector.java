package ua.bookloom.llm.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.EngineDetection;
import ua.bookloom.api.llm.EngineDetection.Source;
import ua.bookloom.api.llm.EngineDetection.Suitability;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;

/**
 * Decides which engine answers at a provider's address by the shape of short GET replies, never by a port or a status
 * (LM Studio answers 200 to every path; KoboldCpp and LocalAI copy Ollama's routes). The person's "Treat this server
 * as..." choice wins without a request, and a known host needs none. Only Save, Test and Detect again call it.
 *
 * <p>The steps, in order: Ollama ({@code /api/version}, {@code /api/tags}, and {@code /v1/models} rows that all carry
 * {@code owned_by}), llama-server ({@code /health}, {@code /props}), Unsloth ({@code /api/health}), LM Studio
 * ({@code /api/v0/models}), then the base's model list. That is at most {@value #MAX_PROBES} GETs when the base is the
 * root or its {@code /v1}, whose {@code /v1/models} reply the last step reuses; a base below another prefix takes one
 * GET more for its own model list.
 */
@Slf4j
public final class EngineDetector {

    /** The most GETs a detection sends when the base address is the server's root or its {@code /v1}. */
    public static final int MAX_PROBES = 7;

    private final HttpExchange exchange;
    private final ReplyShapes shapes;
    private final List<Function<ProbeRun, Optional<Found>>> steps;

    /** Shares the provider exchange, so a probe signs in and is logged like every other request. */
    @Inject
    public EngineDetector(final HttpExchange exchange, final ObjectMapper mapper) {
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.shapes = new ReplyShapes(Objects.requireNonNull(mapper, "mapper"));
        this.steps = List.of(this::ollama, this::llamaServer, this::unsloth, this::lmStudio);
    }

    /**
     * Detects the engine at the provider's address.
     *
     * @param config the provider description; its "Treat this server as..." choice, when set, is the answer
     * @return the engine found, or the failure that kept the server from being asked (unreachable, missing key)
     */
    public Result<EngineDetection> detect(final ProviderConfig config) {
        Objects.requireNonNull(config, "config");
        log.debug(
                "Engine detection started provider={} kind={} host={} choice={} declined={}",
                config.id(),
                config.kind(),
                config.baseUrl().getHost(),
                config.engineChoice(),
                config.nativeOfferDeclined());
        final ServerEngine choice = config.engineChoice();
        if (choice != null) {
            return Result.ok(logged(
                    config,
                    new EngineDetection(
                            choice, config.engineVersion(), false, 0, Source.OVERRIDE, Suitability.SUITABLE, false)));
        }
        final Optional<Found> known = HostRules.match(config.baseUrl());
        if (known.isPresent()) {
            return Result.ok(logged(config, detection(config, known.orElseThrow(), 0)));
        }
        if (BaseUrlRoot.isAnythingLlm(config)) {
            log.debug("Engine detection base path is AnythingLLM's provider={}", config.id());
            final Found documents = new Found(ServerEngine.GENERIC, null, false, Suitability.NOT_SUITABLE_DOCUMENTS);
            return Result.ok(logged(config, detection(config, documents, 0)));
        }
        return BaseUrlRoot.of(config)
                .map(address -> probed(config, new ProbeRun(exchange, address)))
                .orElseGet(() -> Result.ok(logged(config, detection(config, Found.of(ServerEngine.GENERIC), 0))));
    }

    private Result<EngineDetection> probed(final ProviderConfig config, final ProbeRun run) {
        for (final Function<ProbeRun, Optional<Found>> step : steps) {
            final Optional<Found> found = step.apply(run);
            final AppError stopped = run.stopped();
            if (stopped != null) {
                log.debug(
                        "Engine detection stopped provider={} code={} probes={}",
                        config.id(),
                        stopped.code(),
                        run.count());
                return Result.err(stopped);
            }
            if (found.isPresent()) {
                return Result.ok(logged(config, detection(config, found.orElseThrow(), run.count())));
            }
        }
        final Found byList = shapes.object(run.baseModels())
                .map(shapes::byModelRows)
                .orElseGet(() -> Found.of(ServerEngine.GENERIC));
        final AppError stopped = run.stopped();
        return stopped != null
                ? Result.err(stopped)
                : Result.ok(logged(config, detection(config, byList, run.count())));
    }

    private Optional<Found> ollama(final ProbeRun run) {
        final @Nullable String version = shapes.object(run.atRoot("api/version"))
                .map(shapes::ollamaVersion)
                .orElse(null);
        matched("api/version", version != null);
        if (version == null) {
            return Optional.empty();
        }
        final boolean tags = shapes.object(run.atRoot("api/tags"))
                .filter(shapes::isOllamaTags)
                .isPresent();
        matched("api/tags", tags);
        if (!tags) {
            return Optional.empty();
        }
        final boolean owned = shapes.isEveryRowOwned(shapes.object(run.rootModels()));
        matched("v1/models", owned);
        return owned
                ? Optional.of(new Found(ServerEngine.OLLAMA, version, false, Suitability.SUITABLE))
                : Optional.empty();
    }

    private Optional<Found> llamaServer(final ProbeRun run) {
        final Optional<HttpReply> health = run.atRoot("health");
        final Optional<JsonNode> props = shapes.object(run.atRoot("props"));
        final Optional<JsonNode> healthBody = shapes.object(health);
        final @Nullable String build = props.map(ReplyShapes::build).orElse(null);
        final boolean header = health.filter(ReplyShapes::hasLlamaServerHeader).isPresent();
        final boolean loading = healthBody.filter(ReplyShapes::isLoadingModel).isPresent();
        final boolean hit = header
                || loading
                || (build != null && healthBody.filter(ReplyShapes::isStatusOk).isPresent());
        log.debug("Engine probe llama-server header={} loading={} build={} matched={}", header, loading, build, hit);
        return hit
                ? Optional.of(new Found(
                        ServerEngine.LLAMA_CPP,
                        build,
                        props.filter(ReplyShapes::isRouter).isPresent(),
                        Suitability.SUITABLE))
                : Optional.empty();
    }

    private Optional<Found> unsloth(final ProbeRun run) {
        final boolean hit = shapes.object(run.atRoot("api/health"))
                .filter(ReplyShapes::isUnslothHealth)
                .isPresent();
        matched("api/health", hit);
        return hit ? Optional.of(Found.of(ServerEngine.UNSLOTH)) : Optional.empty();
    }

    private Optional<Found> lmStudio(final ProbeRun run) {
        final boolean hit = shapes.object(run.atRoot("api/v0/models"))
                .filter(ReplyShapes::isLmStudioModels)
                .isPresent();
        matched("api/v0/models", hit);
        return hit ? Optional.of(Found.of(ServerEngine.LM_STUDIO)) : Optional.empty();
    }

    private static void matched(final String probe, final boolean hit) {
        log.debug("Engine probe path={} shapeMatched={}", probe, hit);
    }

    private static EngineDetection detection(final ProviderConfig config, final Found found, final int probes) {
        // Ollama's OpenAI path drops the window, the cap field and most samplers, so its own connection is offered
        // once; a person who kept it as it is is never asked again.
        final boolean offer = found.engine() == ServerEngine.OLLAMA
                && config.kind() == ProviderKind.OPENAI_COMPATIBLE
                && !config.nativeOfferDeclined();
        return new EngineDetection(
                found.engine(), found.version(), found.router(), probes, Source.DETECTED, found.suitability(), offer);
    }

    private static EngineDetection logged(final ProviderConfig config, final EngineDetection detection) {
        log.info(
                "engine detected provider={} engine={} probes={} source={} version={} host={} suitability={} router={}"
                        + " offer={}",
                config.id(),
                detection.engine(),
                detection.probes(),
                detection.source().token(),
                Objects.requireNonNullElse(detection.version(), "none"),
                config.baseUrl().getHost(),
                detection.suitability(),
                detection.router(),
                detection.offerNativeConnection());
        return detection;
    }
}
