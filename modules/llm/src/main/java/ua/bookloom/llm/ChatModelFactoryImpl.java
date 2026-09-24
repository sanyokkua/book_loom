package ua.bookloom.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.api.llm.ProviderConfig;
import ua.bookloom.api.llm.ProviderConfigs;
import ua.bookloom.llm.gate.InferenceGate;
import ua.bookloom.llm.provider.ProviderClientFactory;
import ua.bookloom.llm.pseudo.PseudoChatModel;
import ua.bookloom.llm.retry.RetryPolicy;

/**
 * Resolves pseudo or a registered real provider into a model bound to one model id.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = {@Inject})
public final class ChatModelFactoryImpl implements ChatModelFactory {

    private static final String PSEUDO_PROVIDER_ID = "pseudo";

    private final ProviderConfigs configs;
    private final ProviderClientFactory clientFactory;
    private final InferenceGate gate;
    private final RetryPolicy retryPolicy;
    private final ObjectMapper mapper;

    @Override
    public Result<ChatModel> create(ModelSelection selection) {
        Objects.requireNonNull(selection, "selection");
        Result<ChatModel> result;
        try {
            result = createModel(selection);
        } catch (Throwable cause) {
            result = Result.err(internalError(cause));
        }
        log.debug(
                "Created chat model providerId={} modelId={} result={}",
                selection.providerId(),
                selection.modelId(),
                resultDescription(result));
        return result;
    }

    private Result<ChatModel> createModel(ModelSelection selection) {
        if (selection.modelId().isBlank()) {
            log.debug("Chat model resolution stopped branch=blank-model providerId={}", selection.providerId());
            return rejected(selection, "The model id must not be blank.");
        }
        if (PSEUDO_PROVIDER_ID.equals(selection.providerId())) {
            log.debug("Chat model resolution branch=pseudo modelId={}", selection.modelId());
            return Result.ok(new PseudoChatModel(mapper));
        }
        final Optional<ProviderConfig> config = configs.find(selection.providerId());
        if (config.isEmpty()) {
            log.debug("Chat model resolution stopped branch=unknown-provider providerId={}", selection.providerId());
            return rejected(selection, "The requested provider is not available.");
        }
        final ProviderConfig registered = config.orElseThrow();
        log.debug(
                "Chat model resolution branch=registered providerId={} kind={} modelId={}",
                registered.id(),
                registered.kind(),
                selection.modelId());
        return Result.ok(new GatedChatModel(clientFactory.create(registered), selection.modelId(), gate, retryPolicy));
    }

    private Result<ChatModel> rejected(ModelSelection selection, String message) {
        final AppError error = AppError.of(ErrorCode.validation, "Invalid chat model selection", message);
        log.warn(
                "Refused chat model request providerId={} modelId={} code={}",
                selection.providerId(),
                selection.modelId(),
                error.code());
        return Result.err(error);
    }

    private static String resultDescription(Result<ChatModel> result) {
        return result.isOk()
                ? "ok"
                : Objects.requireNonNull(result.error(), "error").code().name();
    }

    private static AppError internalError(Throwable cause) {
        final AppError error = AppError.of(
                ErrorCode.internal, "Chat model factory failure", "The chat model could not be created.", null, cause);
        log.error("Unexpected chat model factory failure code={}", error.code(), cause);
        return error;
    }
}
