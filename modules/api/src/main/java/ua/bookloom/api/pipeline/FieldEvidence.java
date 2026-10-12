package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;

/**
 * What one suggested brief field stands on (15i.B4). A field is decided once, on a basis the code can check: a strict
 * majority of the passages read, accepted knowledge of the book that agrees with the value, or the code's own count of
 * the whole book; a field with none of these is {@link Basis#UNCERTAIN}, stays neutral or empty and is shown with the
 * "uncertain" chip, however many quotes its readings had.
 *
 * @param basis what the field stands on
 * @param quotes the book's own words behind the value, each found in the passage it came from; never null, empty for a
 *     neutral value, which needs no quote, and for an uncertain field
 * @param agreeing how many passages support the value, or for an uncertain field the most that supported any one
 *     value; from zero to {@code samples}
 * @param samples how many passages were read; at least one
 */
public record FieldEvidence(Basis basis, List<String> quotes, int agreeing, int samples) {

    /** What a suggested field stands on. */
    public enum Basis {
        /** Readings of the passages, verified against their quotes: a majority, or the one passage of a short book. */
        PASSAGES,
        /** What the model knows of the book, accepted by the echo check and agreeing with the value. */
        KNOWLEDGE,
        /** The code's own count over the whole book, such as the narrator detector's. */
        MEASURED,
        /** Nothing the code can check; the field is left as it was. */
        UNCERTAIN
    }

    /** Copies the quotes and rejects counts that cannot hold together. */
    public FieldEvidence {
        Objects.requireNonNull(basis, "basis");
        quotes = List.copyOf(Objects.requireNonNull(quotes, "quotes"));
        if (samples < 1 || agreeing < 0 || agreeing > samples) {
            throw new IllegalArgumentException("agreeing " + agreeing + " of " + samples + " samples");
        }
    }

    /**
     * Whether every passage read gave the value.
     *
     * @return {@code true} if every passage agreed, {@code false} otherwise
     */
    public boolean isAgreed() {
        return agreeing == samples;
    }

    /**
     * Whether the field holds the suggested value rather than the neutral one it falls back to.
     *
     * @return {@code true} unless the field is {@link Basis#UNCERTAIN}
     */
    public boolean isKept() {
        return basis != Basis.UNCERTAIN;
    }

    /**
     * The basis in a few English words, for the log and the eval report; the screen words it in its own language.
     *
     * @return never blank
     */
    public String describe() {
        return switch (basis) {
            case PASSAGES -> "from " + agreeing + " of " + samples + " passages";
            case KNOWLEDGE -> "from what the model knows of this book";
            case MEASURED -> "from the code's count of the whole book";
            case UNCERTAIN -> "uncertain: " + agreeing + " of " + samples + " passages agree";
        };
    }
}
