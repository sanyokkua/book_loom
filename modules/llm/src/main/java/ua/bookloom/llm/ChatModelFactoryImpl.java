package ua.bookloom.llm;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatModel;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelSelection;
import ua.bookloom.llm.pseudo.PseudoChatModel;

/**
 * Resolves the built-in pseudo provider until real provider clients arrive.
 */
@Slf4j
public final class ChatModelFactoryImpl implements ChatModelFactory {

    private static final String PSEUDO_PROVIDER_ID = "pseudo";

    /**
     * Creates a factory with no external collaborators.
     */
    public ChatModelFactoryImpl() {
        // The first provider is entirely local and deterministic.
    }

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
        if (!PSEUDO_PROVIDER_ID.equals(selection.providerId())) {
            return rejected(selection, "The requested provider is not available.");
        }
        if (selection.modelId().isBlank()) {
            return rejected(selection, "The model id must not be blank.");
        }
        return Result.ok(new PseudoChatModel());
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
