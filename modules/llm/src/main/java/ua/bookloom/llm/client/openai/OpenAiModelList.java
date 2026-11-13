package ua.bookloom.llm.client.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ServerEngine;
import ua.bookloom.llm.dto.OllamaTagsResponse;
import ua.bookloom.llm.dto.OpenAiModelsResponse;
import ua.bookloom.llm.dto.OpenAiModelsResponse.Model;
import ua.bookloom.llm.engine.EngineDetector;
import ua.bookloom.llm.http.HttpExchange;
import ua.bookloom.llm.http.HttpReply;
import ua.bookloom.llm.profile.ListedModels;
import ua.bookloom.llm.provider.DiscoveryErrors;
import ua.bookloom.llm.provider.EndpointPaths;

/**
 * Reads an OpenAI-shaped model list with only its chat models, and words a list that could not be read. LM Studio's
 * OpenAI list says nothing about a model's kind, so its own {@code /api/v0/models} is read first and its
 * {@code embeddings} entries are left out; an Ollama model whose name ends {@code :cloud} or {@code -cloud} is labelled
 * cloud, never hidden. OpenRouter lists every kind of model, so only those that answer in text are kept.
 */
@Slf4j
final class OpenAiModelList {

    private static final int HTTP_OK = 200;
    private static final String LM_STUDIO_MODELS = "/api/v0/models";

    private final ProviderConfig config;
    private final ObjectMapper mapper;
    private final HttpExchange exchange;
    private final ListedModels listed = new ListedModels();

    OpenAiModelList(ProviderConfig config, ObjectMapper mapper, HttpExchange exchange) {
        this.config = Objects.requireNonNull(config, "config");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.exchange = Objects.requireNonNull(exchange, "exchange");
    }

    /**
     * LM Studio's own list, read when the provider is LM Studio with no model-list path of its own.
     *
     * @return the models, or empty when the engine is another one or the list could not be read, so the OpenAI list
     *     is read instead
     */
    Optional<Result<List<ModelInfo>>> lmStudioList() {
        final boolean overridden =
                config.modelsPath() != null && !config.modelsPath().isBlank();
        if (config.effectiveEngine() != ServerEngine.LM_STUDIO || overridden) {
            return Optional.empty();
        }
        final Optional<HttpReply> reply = EndpointPaths.atHostRoot(config)
                .map(root -> exchange.get(root, LM_STUDIO_MODELS))
                .filter(Result::isOk)
                .map(result -> Objects.requireNonNull(result.data(), "reply"))
                .filter(answered -> answered.status() == HTTP_OK);
        final Optional<Result<List<ModelInfo>>> read =
                reply.map(answered -> read(answered.body())).filter(Result::isOk);
        log.debug(
                "LM Studio's own model list provider={} answered={} read={}",
                config.id(),
                reply.isPresent(),
                read.isPresent());
        return read;
    }

    Result<List<ModelInfo>> read(String body) {
        try {
            final OpenAiModelsResponse response = mapper.readValue(body, OpenAiModelsResponse.class);
            if (response == null
                    || response.data() == null
                    || response.data().stream().anyMatch(this::invalidModel)) {
                return Result.err(failure(null));
            }
            final List<ModelInfo> models = response.data().stream()
                    .filter(model -> !Objects.requireNonNull(model, "model").isEmbedding())
                    .filter(model -> config.effectiveEngine() != ServerEngine.OPENROUTER || model.answersInText())
                    .filter(model -> listed.isChat(Objects.requireNonNull(model.id(), "model id")))
                    .map(this::modelInfo)
                    .toList();
            log.debug(
                    "OpenAI-compatible discovery outcome host={} modelCount={} leftOut={} code=none",
                    config.baseUrl().getHost(),
                    models.size(),
                    response.data().size() - models.size());
            return Result.ok(models);
        } catch (JsonProcessingException unreadable) {
            return Result.err(failure(unreadable));
        }
    }

    private ModelInfo modelInfo(Model model) {
        final String id = Objects.requireNonNull(model.id(), "model id");
        return new ModelInfo(
                id,
                config.effectiveEngine() == ServerEngine.OLLAMA && OllamaTagsResponse.isCloudName(id),
                EngineDetector.ownerEngine(model.ownedBy()));
    }

    private boolean invalidModel(@Nullable Model model) {
        return model == null || model.id() == null || model.id().isBlank();
    }

    Result<List<ModelInfo>> failed(AppError error) {
        if (DiscoveryErrors.preserve(error.code())) {
            log.debug(
                    "OpenAI-compatible discovery outcome host={} code={}",
                    config.baseUrl().getHost(),
                    error.code());
            return Result.err(error);
        }
        return Result.err(DiscoveryErrors.degraded(
                "OpenAI-compatible",
                config.baseUrl().getHost(),
                error,
                "The OpenAI-compatible model list could not be read."));
    }

    AppError failure(@Nullable Throwable cause) {
        log.warn(
                "OpenAI-compatible discovery failed host={} code={}",
                config.baseUrl().getHost(),
                ErrorCode.discoveryFailed);
        return AppError.of(
                ErrorCode.discoveryFailed,
                "Model discovery failed",
                "The OpenAI-compatible model list could not be read.",
                SafeDetails.empty().withEndpoint(config.baseUrl()).render(),
                cause);
    }
}
