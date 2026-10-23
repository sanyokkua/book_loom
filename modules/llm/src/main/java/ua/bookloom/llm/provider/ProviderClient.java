package ua.bookloom.llm.provider;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderKind;

/** Internal boundary implemented by each provider-specific HTTP dialect. */
public interface ProviderClient {

    /** Checks provider reachability without listing or loading a model. */
    ProviderCallResult<Boolean> probe();

    /** Returns the provider's available model identifiers, or a typed discovery failure. */
    ProviderCallResult<List<ModelInfo>> listModels();

    /** Sends one non-streaming conversation to the bound model. */
    ProviderCallResult<ChatResponse> chat(String modelId, ChatRequest request);

    /**
     * The JSON body {@link #chat} puts on the wire for {@code request}, for the Settings test dialog. It is the body
     * before any refused setting is dropped, and holds no header or credential.
     *
     * @param modelId the model the call is bound to; never null
     * @param request the call; never null
     * @return the serialized body, or an empty string when this client cannot show one
     */
    default String requestBody(String modelId, ChatRequest request) {
        return "";
    }

    /**
     * How long {@link #chat} waits for the reply to {@code request}, so a caller can show the bound it waits against.
     *
     * @param request the call about to be sent
     * @return the request timeout, or null when this client sets none of its own
     */
    default @Nullable Duration chatTimeout(ChatRequest request) {
        return null;
    }

    /**
     * Asks the provider for a model's context length; never fails the caller.
     *
     * @param modelId the model to ask about; never null
     * @return the context length in tokens, or empty when the provider cannot or does not say
     */
    default Optional<Integer> contextLength(String modelId) {
        return Optional.empty();
    }

    /** Identifies the wire dialect implemented by this client. */
    ProviderKind kind();
}
