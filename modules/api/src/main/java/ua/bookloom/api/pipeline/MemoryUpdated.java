package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * Announces that the glossary, rolling summary or translation memory changed.
 *
 * @param kind which memory changed
 * @param label a short human-readable label for what changed (a term, a chunk boundary)
 */
public record MemoryUpdated(MemoryKind kind, String label) implements JobEvent {

    /** Rejects an event without a kind or label. */
    public MemoryUpdated {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
    }
}
