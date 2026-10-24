package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;

/**
 * What became of the last request for a style suggestion, for the Book Brief to word.
 *
 * @param kind how it ended
 * @param failure the failure when {@code kind} is {@link Kind#FAILED}, else {@code null}; the Book Brief words it in
 *     the display language
 */
public record StyleSuggestionOutcome(Kind kind, @Nullable AppError failure) {

    /** How a style suggestion ended. */
    public enum Kind {
        /** Nothing was asked, or the last answer was dismissed. */
        NONE,
        /** The model's suggestion was applied to the brief. */
        DONE,
        /** The person stopped the suggestion; the brief is unchanged. */
        STOPPED,
        /** No provider and model are chosen. */
        NO_MODEL,
        /** Other model work is running. */
        BUSY,
        /** The model could not suggest a style. */
        FAILED
    }

    /** Nothing to say. */
    public static final StyleSuggestionOutcome NONE = new StyleSuggestionOutcome(Kind.NONE, null);

    /** Rejects a missing part. */
    public StyleSuggestionOutcome {
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * An outcome that needs no message.
     *
     * @param kind how it ended
     * @return the outcome
     */
    public static StyleSuggestionOutcome of(final Kind kind) {
        return new StyleSuggestionOutcome(kind, null);
    }
}
