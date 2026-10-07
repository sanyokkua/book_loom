package ua.bookloom.ui.state;

import java.util.Objects;

/**
 * What became of the last request for a style suggestion, for the Book Brief to word.
 *
 * @param kind how it ended
 * @param message the failure's own text when {@code kind} is {@link Kind#FAILED}, else empty
 */
public record StyleSuggestionOutcome(Kind kind, String message) {

    /** How a style suggestion ended. */
    public enum Kind {
        /** Nothing was asked, or the last answer was dismissed. */
        NONE,
        /** The model's suggestion was written into the brief. */
        DONE,
        /** No provider and model are chosen. */
        NO_MODEL,
        /** Other model work is running. */
        BUSY,
        /** The model could not suggest a style. */
        FAILED
    }

    /** Nothing to say. */
    public static final StyleSuggestionOutcome NONE = new StyleSuggestionOutcome(Kind.NONE, "");

    /** Rejects a missing part. */
    public StyleSuggestionOutcome {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(message, "message");
    }

    /**
     * An outcome that needs no message.
     *
     * @param kind how it ended
     * @return the outcome
     */
    public static StyleSuggestionOutcome of(final Kind kind) {
        return new StyleSuggestionOutcome(kind, "");
    }
}
