package ua.bookloom.api.llm;

import ua.bookloom.api.Result;

/**
 * Port for asking a registered provider what a model can take, so a run sizes its prompts to the window the model
 * really has instead of a guess.
 *
 * <p>Not inference: it never waits behind a running translation, and it is optional — it answers the model's
 * context length when the provider says it and {@link ContextLength#unknown()} otherwise.
 */
public interface ModelCapabilities {

    /**
     * Detects the context length of a model.
     *
     * @param providerId the id of a registered provider; never {@code null}
     * @param modelId the provider's model id; never {@code null}
     * @return the context length, {@link ContextLength#unknown()} when the provider cannot or does not say (a
     *     network or protocol failure is degraded, not reported), or {@link ua.bookloom.api.ErrorCode#validation} for an
     *     unregistered provider id (no request is sent)
     */
    Result<ContextLength> contextLength(String providerId, String modelId);
}
