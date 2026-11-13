package ua.bookloom.api.pipeline;

import java.util.Objects;

/**
 * Announces that the glossary or the translation memory changed; a kept chapter summary is {@link ChapterSummaryKept}.
 *
 * @param kind which memory changed
 * @param label a short human-readable label for what changed (the glossary's added count, a reused segment's locator)
 */
public record MemoryUpdated(MemoryKind kind, String label) implements JobEvent {

    /** Rejects an event without a kind or label. */
    public MemoryUpdated {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(label, "label");
    }
}
