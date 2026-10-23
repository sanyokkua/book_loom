package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ProviderKind;

/**
 * One model of one provider as the Models tab offers it.
 *
 * @param providerId the provider's registry id
 * @param providerName the provider's display name
 * @param kind the provider's wire dialect, which decides what the tab can say about the model
 * @param modelId the provider's model id
 */
public record ModelEntry(String providerId, String providerName, ProviderKind kind, String modelId) {

    public ModelEntry {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(providerName, "providerName");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(modelId, "modelId");
    }

    // The names are for display; the model is the same model whatever its provider is called.
    @Override
    public boolean equals(final @Nullable Object other) {
        return other instanceof ModelEntry that && providerId.equals(that.providerId) && modelId.equals(that.modelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(providerId, modelId);
    }
}
