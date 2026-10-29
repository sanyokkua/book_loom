package ua.bookloom.api.llm;

import java.util.Objects;

/**
 * A provider-discovered model identifier.
 *
 * @param id the id the provider lists the model under
 * @param cloud whether the provider runs the model on a remote service rather than on this machine (an Ollama cloud
 *     model); a neutral label, never a reason to hide the model
 */
public record ModelInfo(String id, boolean cloud) {

    /**
     * A model that runs where the provider runs.
     *
     * @param id the id the provider lists the model under
     */
    public ModelInfo(String id) {
        this(id, false);
    }

    /** Rejects a missing model identifier. */
    public ModelInfo {
        Objects.requireNonNull(id, "id");
    }
}
