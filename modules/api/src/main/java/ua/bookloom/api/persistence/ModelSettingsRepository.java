package ua.bookloom.api.persistence;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import ua.bookloom.api.Result;
import ua.bookloom.api.llm.ModelSettings;

/**
 * Keeps the settings of each model per provider and model. "Send again" on a refusal is an {@link #update} that
 * drops the refusal ({@code ModelSettings#withoutRefusal}), so it never overwrites a field another writer changed.
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
     * Changes one model's settings as a single step, so two writers (a refusal remembered while a speed is stored)
     * never overwrite each other's field.
     *
     * @param providerId the non-null provider id
     * @param modelId the non-null model id
     * @param change receives what is stored, or the defaults when nothing is; returns the record to keep
     * @return the stored settings
     */
    Result<ModelSettings> update(String providerId, String modelId, UnaryOperator<ModelSettings> change);

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
