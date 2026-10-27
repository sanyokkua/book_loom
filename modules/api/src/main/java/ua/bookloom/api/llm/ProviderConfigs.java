package ua.bookloom.api.llm;

import java.util.List;
import java.util.Optional;
import ua.bookloom.api.Result;

/** Registry port for provider descriptions. */
public interface ProviderConfigs {

    /**
     * Validates a provider description with {@link ProviderConfigRules} and inserts it, or replaces the one with the
     * same id.
     *
     * @param config the provider description to save
     * @return the saved description, or the first refusal of the rules for an invalid description
     */
    Result<ProviderConfig> save(ProviderConfig config);

    /**
     * Removes a provider description.
     *
     * @param id the registry key
     * @return {@code true} if a description was removed, {@code false} if the id was unknown
     */
    Result<Boolean> delete(String id);

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
