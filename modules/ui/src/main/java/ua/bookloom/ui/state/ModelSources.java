package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.persistence.ModelSettingsRepository;

/**
 * What a model's form reads about it.
 *
 * @param store where the per-model settings are kept
 * @param capabilities what the providers say about a model
 * @param structured whether a model answered in the structured form this session
 */
public record ModelSources(
        ModelSettingsRepository store, ModelCapabilities capabilities, StructuredAnswers structured) {

    /** Rejects a missing part. */
    @Inject
    public ModelSources {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(structured, "structured");
    }
}
