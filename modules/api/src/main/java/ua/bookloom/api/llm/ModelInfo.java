package ua.bookloom.api.llm;

import java.util.Objects;

/** A provider-discovered model identifier. */
public record ModelInfo(String id) {

    /** Rejects a missing model identifier. */
    public ModelInfo {
        Objects.requireNonNull(id, "id");
    }
}
