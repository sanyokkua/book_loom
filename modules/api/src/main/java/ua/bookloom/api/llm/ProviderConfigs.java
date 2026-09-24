package ua.bookloom.api.llm;

import java.util.List;
import java.util.Optional;
import ua.bookloom.api.Result;

/** Registry port for provider descriptions. */
public interface ProviderConfigs {

    /**
     * Validates and registers a provider description.
     *
     * @param config the provider description to register
     * @return the registered description, or a validation error for a semantically invalid description
     */
    Result<ProviderConfig> register(ProviderConfig config);

    /**
     * Finds a provider description by its registry key.
     *
     * @param id the registry key
     * @return the matching description, or empty when it is unknown
     */
    Optional<ProviderConfig> find(String id);

    /**
     * Lists the known provider descriptions.
     *
     * @return all known descriptions
     */
    List<ProviderConfig> all();
}
