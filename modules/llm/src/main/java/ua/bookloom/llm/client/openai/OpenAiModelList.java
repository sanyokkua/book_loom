package ua.bookloom.llm.client.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.llm.dto.OpenAiModelsResponse;
import ua.bookloom.llm.dto.OpenAiModelsResponse.Model;
import ua.bookloom.llm.provider.DiscoveryErrors;

/** Reads an OpenAI-shaped model list, and words a list that could not be read. */
@Slf4j
final class OpenAiModelList {

    private final ProviderConfig config;
    private final ObjectMapper mapper;

    OpenAiModelList(ProviderConfig config, ObjectMapper mapper) {
        this.config = Objects.requireNonNull(config, "config");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
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
                    .map(model -> new ModelInfo(Objects.requireNonNull(model.id(), "model id")))
                    .toList();
            log.debug(
                    "OpenAI-compatible discovery outcome host={} modelCount={} code=none",
                    config.baseUrl().getHost(),
                    models.size());
            return Result.ok(models);
        } catch (JsonProcessingException unreadable) {
            return Result.err(failure(unreadable));
        }
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
