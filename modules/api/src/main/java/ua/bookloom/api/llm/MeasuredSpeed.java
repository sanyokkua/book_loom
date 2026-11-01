package ua.bookloom.api.llm;

import java.util.Objects;

/**
 * A measured speed of one call kind and where it was measured. The {@code DRAFT} figure is seconds per drafted
 * segment, which the inference test and a run measure alike (a batched draft call holds several segments); every
 * other kind is seconds per call, read from the call ledger at the end of a run or a names pass.
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
