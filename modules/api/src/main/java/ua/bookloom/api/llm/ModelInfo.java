package ua.bookloom.api.llm;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A provider-discovered model identifier.
 *
 * @param id the id the provider lists the model under
 * @param cloud whether the provider runs the model on a remote service rather than on this machine (an Ollama cloud
 *     model); a neutral label, never a reason to hide the model
 * @param ownerEngine the engine the row's own {@code owned_by} value names (LM Studio's {@code organization_owner},
 *     Ollama's {@code library}), or null when the row names none; lets a caller that listed the models tell the
 *     server by the shape of the rows it already holds, without another request
 */
public record ModelInfo(String id, boolean cloud, @Nullable ServerEngine ownerEngine) {

    /**
     * A model whose row names no engine.
     *
     * @param id the id the provider lists the model under
     * @param cloud whether the provider runs the model on a remote service
     */
    public ModelInfo(String id, boolean cloud) {
        this(id, cloud, null);
    }

    /**
     * A model that runs where the provider runs and whose row names no engine.
     *
     * @param id the id the provider lists the model under
     */
    public ModelInfo(String id) {
        this(id, false, null);
    }

    /** Rejects a missing model identifier. */
    public ModelInfo {
        Objects.requireNonNull(id, "id");
    }
}
