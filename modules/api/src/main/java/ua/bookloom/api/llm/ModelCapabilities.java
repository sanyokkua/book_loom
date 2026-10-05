package ua.bookloom.api.llm;

import java.util.Optional;
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

    /**
     * The context length a run should size against, with any failure degraded to "unknown": a run never stops because
     * its model's window could not be read.
     *
     * @param selection the provider and model a run will use; never {@code null}
     * @return the reported tokens, or empty when the provider did not say or the detection failed
     */
    default Optional<Integer> detectedTokens(final ModelSelection selection) {
        final Result<ContextLength> length = contextLength(selection.providerId(), selection.modelId());
        final ContextLength data = length.data();
        return length.isOk() && data != null ? data.detected() : Optional.empty();
    }
}
