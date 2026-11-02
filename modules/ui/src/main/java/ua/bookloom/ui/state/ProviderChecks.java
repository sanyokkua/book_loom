package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.api.llm.ProviderVerifier;

/**
 * What the settings check a provider with.
 *
 * @param verifier the port a check is run through
 * @param modelListing the models the selected provider offers
 */
public record ProviderChecks(ProviderVerifier verifier, ModelListing modelListing) {

    /** Rejects a missing part. */
    @Inject
    public ProviderChecks {
        Objects.requireNonNull(verifier, "verifier");
        Objects.requireNonNull(modelListing, "modelListing");
    }
}
