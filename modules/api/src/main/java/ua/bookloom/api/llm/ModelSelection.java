package ua.bookloom.api.llm;

import java.util.Objects;

/**
 * The provider and model identifiers selected for one bound chat model.
 *
 * @param providerId the provider kind identifier
 * @param modelId the provider's model identifier
 */
public record ModelSelection(String providerId, String modelId) {

    /**
     * Rejects an incomplete provider/model selection before it reaches a factory.
     */
    public ModelSelection {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(modelId, "modelId");
    }
}
