package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.llm.ProviderConfig;

/**
 * What a provider test is run against: the description as the form holds it, saved or not.
 *
 * @param config the description to verify
 * @param model the model text as typed; blank when none is chosen
 * @param name the name to show for the provider
 * @param stored whether a provider with this id is saved, which decides whether a measured speed may be kept for it
 */
public record TestSubject(ProviderConfig config, String model, String name, boolean stored) {

    /** The id a provider being added is tested under; it is never stored. */
    public static final String DRAFT_ID = "new-provider";

    /** Rejects a missing part. */
    public TestSubject {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(name, "name");
    }

    /**
     * What makes a finding stale: everything but the name.
     *
     * @param config the description without its display name
     * @param model the model, stripped
     * @param stored whether the provider is saved
     */
    public record Key(ProviderConfig config, String model, boolean stored) {}

    /**
     * The identity of a test's findings.
     *
     * @return a value that is equal for two subjects whose findings mean the same thing
     */
    public Key key() {
        final ProviderConfig unnamed = new ProviderConfig(
                config.id(),
                config.kind(),
                config.baseUrl(),
                config.connectTimeout(),
                config.requestTimeout(),
                null,
                config.auth(),
                config.apiKeyHeader(),
                config.credentialRef(),
                config.modelsPath(),
                config.chatPath(),
                config.headers(),
                config.customModels());
        return new Key(unnamed, model.strip(), stored);
    }
}
