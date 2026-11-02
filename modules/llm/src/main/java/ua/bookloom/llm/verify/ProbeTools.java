package ua.bookloom.llm.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.Objects;
import java.util.function.LongSupplier;
import ua.bookloom.api.persistence.ModelSettingsRepository;

/**
 * What the inference and advanced probes need beyond the retried calls: the JSON mapper, the time source, the stored
 * model settings and the shaping that lays the probe out the way the model's template reads it.
 */
final class ProbeTools {

    private final ObjectMapper mapper;
    private final LongSupplier nanoTime;
    private final ModelSettingsRepository settings;
    private final ProbeShaping shaping;

    @Inject
    ProbeTools(
            final ObjectMapper mapper,
            final LongSupplier nanoTime,
            final ModelSettingsRepository settings,
            final ProbeShaping shaping) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.shaping = Objects.requireNonNull(shaping, "shaping");
    }

    ObjectMapper mapper() {
        return mapper;
    }

    LongSupplier nanoTime() {
        return nanoTime;
    }

    ModelSettingsRepository settings() {
        return settings;
    }

    ProbeShaping shaping() {
        return shaping;
    }
}
