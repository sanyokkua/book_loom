package ua.bookloom.api.persistence;

import java.util.List;
import java.util.Optional;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSettings;

/**
 * Keeps the settings of each model per provider and model. "Send again" on a refusal is a {@link #save} of the
 * record without it.
 */
public interface ModelSettingsRepository {

    /**
     * Reads one model's settings.
     *
     * @param providerId the non-null provider id
     * @param modelId the non-null model id
     * @return the stored settings, or empty when none were saved (the caller then uses the defaults)
     */
    Result<Optional<ModelSettings>> find(String providerId, String modelId);

    /**
     * Stores a model's settings, replacing the previous record of the same provider and model.
     *
     * @param settings the non-null settings
     * @return the stored settings
     */
    Result<ModelSettings> save(ModelSettings settings);

    /**
     * Forgets one model's settings.
     *
     * @param providerId the non-null provider id
     * @param modelId the non-null model id
     * @return an empty success, also when nothing was stored
     */
    Result<Boolean> delete(String providerId, String modelId);

    /**
     * Lists every stored record.
     *
     * @return the records; never null, empty if none
     */
    Result<List<ModelSettings>> all();
}
