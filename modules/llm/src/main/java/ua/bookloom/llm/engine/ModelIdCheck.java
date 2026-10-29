package ua.bookloom.llm.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.FailureReason;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ModelVariant;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.dto.OpenAiModelsResponse;
import ua.bookloom.llm.dto.OpenAiModelsResponse.Model;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.provider.EndpointPaths;

/**
 * Checks a model id against the server's own list where the server would otherwise answer any id: LM Studio and Unsloth
 * answer an id they do not have with whatever model is loaded, and a llama-server router loads whichever listed model
 * an id names. One GET of the list ({@code /api/v0/models} on LM Studio, else {@code <baseUrl>/models}) on the first
 * call of an id, kept for the session; an id that is not listed is not kept, so a model added meanwhile is found on the
 * next call. A list that cannot be read lets the call go on: the server then decides, as it did before.
 */
@Slf4j
public final class ModelIdCheck {

    private static final int HTTP_OK = 200;
    private static final String LM_STUDIO_MODELS = "/api/v0/models";
    private static final String MODELS = "/models";
    private static final String LOADED = "loaded";
    private static final String NOT_LOADED = "not-loaded";

    private final ProviderConfig config;
    private final HttpExchange exchange;
    private final ObjectMapper mapper;
    private final EngineFacts facts;
    private final Map<String, CheckedModelId> checked = new ConcurrentHashMap<>();
    private final Set<String> variantsReported = ConcurrentHashMap.newKeySet();

    /** Binds the check to one provider, sharing its exchange so the GET signs in and is logged like a chat call. */
    public ModelIdCheck(
            final ProviderConfig config,
            final HttpExchange exchange,
            final ObjectMapper mapper,
            final EngineFacts facts) {
        this.config = Objects.requireNonNull(config, "config");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.facts = Objects.requireNonNull(facts, "facts");
    }

    /**
     * The id a request carries, checked on its first call of the session.
     *
     * @param typedId the model id as stored or typed; non-null
     * @return the check, or {@code modelNotFound} naming the id when the server's list does not hold it
     */
    public Result<CheckedModelId> check(final String typedId) {
        Objects.requireNonNull(typedId, "typedId");
        final CheckedModelId kept = checked.get(typedId);
        if (kept != null) {
            log.debug("Model id check kept provider={} model={} verdict={}", config.id(), typedId, kept.verdict());
            return Result.ok(kept);
        }
        return read(typedId);
    }

    /**
     * Reads the server's list again, as the Test's models stage does, and keeps what it finds.
     *
     * @param typedId the model id as stored or typed; non-null
     * @return the check, or {@code modelNotFound} naming the id when the server's list does not hold it
     */
    public Result<CheckedModelId> recheck(final String typedId) {
        Objects.requireNonNull(typedId, "typedId");
        checked.remove(typedId);
        return read(typedId);
    }

    /**
     * The last check kept for an id, so a caller can say whether LM Studio has the model loaded without a new request.
     *
     * @param typedId the model id as stored or typed; non-null
     * @return the kept check if the id was checked this session, or empty if it was not
     */
    public Optional<CheckedModelId> kept(final String typedId) {
        return Optional.ofNullable(checked.get(Objects.requireNonNull(typedId, "typedId")));
    }

    /**
     * Whether this provider's server answers an id it does not have, so its ids are checked.
     *
     * @return {@code true} if the engine serves unknown ids or is a llama-server router, {@code false} otherwise
     */
    public boolean applies() {
        final ServerEngine engine = config.effectiveEngine();
        final boolean applies = EngineTraits.of(engine, config.engineVersion()).servesUnknownModel()
                || (engine == ServerEngine.LLAMA_CPP && facts.isRouter(config));
        log.debug("Model id check applies provider={} engine={} applies={}", config.id(), engine, applies);
        return applies;
    }

    private Result<CheckedModelId> read(final String typedId) {
        final ModelVariant split = split(typedId);
        if (!applies()) {
            return Result.ok(keep(typedId, unchecked(split, CheckedModelId.Verdict.NOT_CHECKED)));
        }
        final Optional<List<Model>> listed = listing();
        if (listed.isEmpty()) {
            log.debug("Model id not checked: the list could not be read provider={} model={}", config.id(), typedId);
            return Result.ok(keep(typedId, unchecked(split, CheckedModelId.Verdict.UNREADABLE)));
        }
        return judged(typedId, split, listed.orElseThrow());
    }

    private Result<CheckedModelId> judged(final String typedId, final ModelVariant split, final List<Model> listed) {
        final List<Model> chatModels =
                listed.stream().filter(model -> !model.isEmbedding()).toList();
        final List<String> ids = chatModels.stream()
                .map(model -> Objects.requireNonNull(model.id(), "id"))
                .toList();
        final boolean found = ids.contains(split.id());
        log.debug(
                "Model id check provider={} model={} listedCount={} found={}",
                config.id(),
                split.id(),
                ids.size(),
                found);
        if (!found) {
            return Result.err(notListed(split.id()));
        }
        final CheckedModelId check = new CheckedModelId(
                split.id(), split.variant(), CheckedModelId.Verdict.LISTED, ids, loaded(chatModels, split.id()));
        return Result.ok(keep(typedId, check));
    }

    // Only LM Studio reads @ as a variant; elsewhere it may be part of an id.
    private ModelVariant split(final String typedId) {
        if (config.effectiveEngine() != ServerEngine.LM_STUDIO) {
            return new ModelVariant(typedId, null);
        }
        final ModelVariant split = ModelVariant.of(typedId);
        if (split.hasVariant() && variantsReported.add(typedId)) {
            log.info(
                    "Model variant removed provider={} model={} variant={} sent={}",
                    config.id(),
                    typedId,
                    split.variant(),
                    split.id());
        }
        return split;
    }

    private CheckedModelId keep(final String typedId, final CheckedModelId check) {
        checked.put(typedId, check);
        return check;
    }

    private static CheckedModelId unchecked(final ModelVariant split, final CheckedModelId.Verdict verdict) {
        return new CheckedModelId(split.id(), split.variant(), verdict, List.of(), null);
    }

    // LM Studio's own list first, which also says what is loaded; its OpenAI list when an older one has no /api/v0.
    private Optional<List<Model>> listing() {
        if (config.effectiveEngine() == ServerEngine.LM_STUDIO) {
            final Optional<List<Model>> native0 =
                    EndpointPaths.atHostRoot(config).flatMap(root -> rows(exchange.get(root, LM_STUDIO_MODELS)));
            if (native0.isPresent()) {
                return native0;
            }
            log.debug("LM Studio's own model list unread, its OpenAI list is read provider={}", config.id());
        }
        return rows(exchange.get(config, EndpointPaths.modelsPath(config, MODELS)));
    }

    private Optional<List<Model>> rows(final Result<HttpReply> reply) {
        final HttpReply answered = reply.data();
        if (reply.isErr() || answered == null || answered.status() != HTTP_OK) {
            return Optional.empty();
        }
        try {
            final OpenAiModelsResponse list = mapper.readValue(answered.body(), OpenAiModelsResponse.class);
            if (list == null || list.data() == null) {
                return Optional.empty();
            }
            return Optional.of(list.data().stream()
                    .filter(Objects::nonNull)
                    .filter(model -> model.id() != null && !model.id().isBlank())
                    .toList());
        } catch (JsonProcessingException unreadable) {
            log.debug("Model list body is not a list provider={}", config.id());
            return Optional.empty();
        }
    }

    private static @Nullable Boolean loaded(final List<Model> models, final String id) {
        final String state = models.stream()
                .filter(model -> id.equals(model.id()))
                .map(Model::state)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (LOADED.equals(state)) {
            return Boolean.TRUE;
        }
        return NOT_LOADED.equals(state) ? Boolean.FALSE : null;
    }

    private AppError notListed(final String id) {
        final String engine = config.effectiveEngine().label();
        log.warn(
                "Model id not listed by the server provider={} engine={} model={} code={}",
                config.id(),
                config.effectiveEngine(),
                id,
                ErrorCode.modelNotFound);
        // LM Studio answers an unknown id with the loaded model, so the sentence says why the call was stopped.
        final String message = config.effectiveEngine() == ServerEngine.LM_STUDIO
                ? "LM Studio has no model \"" + id + "\". It would answer with the model that is loaded. Pick a model"
                        + " from the list."
                : engine + " has no model with the id " + id + ". Check the model id.";
        return AppError.of(
                ErrorCode.modelNotFound,
                "Model not found",
                message,
                SafeDetails.empty()
                        .withReason(FailureReason.MODEL_NOT_LISTED)
                        .withEndpoint(config.baseUrl())
                        .withModelName(id)
                        .withEngine(config.effectiveEngine().name())
                        .render(),
                null);
    }
}
