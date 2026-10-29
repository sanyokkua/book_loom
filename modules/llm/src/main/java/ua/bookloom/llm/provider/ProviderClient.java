package ua.bookloom.llm.provider;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ChatRequest;
import ua.bookloom.api.llm.ChatResponse;
import ua.bookloom.api.llm.ModelInfo;
import ua.bookloom.api.llm.ProviderKind;
import ua.bookloom.llm.engine.CheckedModelId;

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

    /**
     * Asks the provider for the context a model is loaded with right now; never fails the caller.
     *
     * @param modelId the model to ask about; never null
     * @return the loaded context length in tokens, or empty when unknown, not loaded, or this dialect cannot say
     */
    default Optional<Integer> loadedContextLength(String modelId) {
        return Optional.empty();
    }

    /**
     * Asks the server's health route whether it is still loading its model; never fails the caller. Asked only by the
     * wake of a run paused on a loading model.
     *
     * @return {@code true} if the server answered that it is loading, {@code false} otherwise or when it cannot say
     */
    default boolean isLoadingModel() {
        return false;
    }

    /**
     * This client as the Test's checks use it: they send the fields they ask for, because a check is how a server
     * earns a field that a run's calls send only once it is proved.
     *
     * @return the client for checks; this client when its calls are never narrowed
     */
    default ProviderClient forChecks() {
        return this;
    }

    /**
     * Whether this client checks a model id against the server's own list before a chat call, because its server
     * would answer an id it does not have with another model.
     *
     * @return {@code true} if ids are checked, {@code false} otherwise
     */
    default boolean checksModelIds() {
        return false;
    }

    /**
     * Checks a model id against the server's own list again, as the Test's models stage does, keeping the result for
     * the session's chat calls.
     *
     * @param modelId the model id as stored or typed; never null
     * @return the check, or {@code modelNotFound} naming the id when the list does not hold it; a client that checks
     *     no id answers {@link CheckedModelId.Verdict#NOT_CHECKED}
     */
    default Result<CheckedModelId> recheckModelId(String modelId) {
        return Result.ok(new CheckedModelId(modelId, null, CheckedModelId.Verdict.NOT_CHECKED, List.of(), null));
    }

    /**
     * The full address the next chat call is posted to, for the Test dialog's request line.
     *
     * @return the address with no credentials in it, or empty when this client cannot say
     */
    default Optional<URI> chatUrl() {
        return Optional.empty();
    }

    /** Identifies the wire dialect implemented by this client. */
    ProviderKind kind();
}
