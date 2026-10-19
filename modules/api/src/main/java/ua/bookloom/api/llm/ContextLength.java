package ua.bookloom.api.llm;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The context length a provider reports for one model, which bounds how large a prompt may be.
 *
 * <p>Detection is optional: a provider that does not say, or cannot be asked, yields {@link #unknown()} rather than an
 * error, so a run sizes its prompts against its default window.
 *
 * @param tokens the reported context length in tokens, or null when the provider did not say; positive when present
 */
public record ContextLength(@Nullable Integer tokens) {

    /** Rejects a non-positive figure, which no provider reports meaningfully. */
    public ContextLength {
        if (tokens != null && tokens <= 0) {
            throw new IllegalArgumentException("tokens must be positive: " + tokens);
        }
    }

    /**
     * A model whose context length is not known.
     *
     * @return a value that {@link #detected()} reports empty
     */
    public static ContextLength unknown() {
        return new ContextLength(null);
    }

    /**
     * The reported length.
     *
     * @return the tokens if the provider reported them, or empty if not
     */
    public Optional<Integer> detected() {
        return Optional.ofNullable(tokens);
    }
}
