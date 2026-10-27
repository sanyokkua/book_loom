package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.llm.MeasuredFrom;

/**
 * A model's stored speed and where it was measured.
 *
 * @param seconds seconds per segment of a Fast draft; positive
 * @param from where it was measured
 */
public record ModelSpeed(double seconds, MeasuredFrom from) {

    /** Rejects a missing source. */
    public ModelSpeed {
        Objects.requireNonNull(from, "from");
    }
}
