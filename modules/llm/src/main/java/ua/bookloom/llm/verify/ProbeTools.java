package ua.bookloom.llm.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.Objects;
import java.util.function.LongSupplier;
import ua.bookloom.api.persistence.ModelSettingsRepository;

/**
 * What the inference and advanced probes need beyond the retried calls.
 *
 * @param mapper the JSON mapper
 * @param nanoTime the time source
 * @param settings the stored model settings
 * @param shaping lays the probe out the way the model's template reads it
 */
record ProbeTools(ObjectMapper mapper, LongSupplier nanoTime, ModelSettingsRepository settings, ProbeShaping shaping) {

    @Inject
    ProbeTools {
        Objects.requireNonNull(mapper, "mapper");
        Objects.requireNonNull(nanoTime, "nanoTime");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(shaping, "shaping");
    }
}
