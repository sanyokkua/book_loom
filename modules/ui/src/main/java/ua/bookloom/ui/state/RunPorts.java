package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;
import ua.bookloom.api.llm.ChatModelFactory;
import ua.bookloom.api.llm.ModelCapabilities;
import ua.bookloom.api.llm.ProviderVerifier;
import ua.bookloom.api.pipeline.TranslationEngine;

/**
 * The ports a run is prepared through.
 *
 * @param models the port a model is created through
 * @param engine the port a job is created through
 * @param verifier the port a run paused on a provider error probes the provider with before resuming by itself
 * @param capabilities the port the model's context length is read through when a run is prepared
 */
public record RunPorts(
        ChatModelFactory models, TranslationEngine engine, ProviderVerifier verifier, ModelCapabilities capabilities) {

    /** Rejects a missing part. */
    @Inject
    public RunPorts {
        Objects.requireNonNull(models, "models");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(verifier, "verifier");
        Objects.requireNonNull(capabilities, "capabilities");
    }
}
