package ua.bookloom.api.llm;

import java.util.Objects;

/**
 * A measured speed of one call kind and where it was measured. The {@code DRAFT} figure is seconds per Fast-draft
 * segment, not per call, until a later task changes it.
 *
 * @param seconds the seconds measured; positive
 * @param from where it was measured; non-null
 */
public record MeasuredSpeed(double seconds, MeasuredFrom from) {

    /** Rejects a non-positive figure or a missing source. */
    public MeasuredSpeed {
        Objects.requireNonNull(from, "from");
        if (!(seconds > 0.0)) {
            throw new IllegalArgumentException("measured speed must be positive: " + seconds);
        }
    }
}
