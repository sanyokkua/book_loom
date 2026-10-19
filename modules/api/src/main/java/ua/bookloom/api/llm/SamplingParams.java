package ua.bookloom.api.llm;

import org.jspecify.annotations.Nullable;

/**
 * Token-sampling controls beyond temperature. A server applies its own default for every control left null, and
 * those defaults differ between engines (some penalise repeated tokens), so a control that matters is stated.
 *
 * @param topP nucleus mass in (0, 1], or null for the server's own
 * @param topK the number of most likely tokens kept, or null for the server's own; positive when set
 * @param minP the least probability, relative to the best token, a token needs to be kept, or null for the
 *     server's own
 * @param repeatPenalty the penalty on recently seen tokens, where 1.0 means none, or null for the server's own
 */
public record SamplingParams(
        @Nullable Double topP,
        @Nullable Integer topK,
        @Nullable Double minP,
        @Nullable Double repeatPenalty) {

    /** No control stated, so every one is the server's own. */
    public static final SamplingParams NONE = new SamplingParams(null, null, null, null);

    /** Rejects a non-positive top-k, which no engine reads as a limit. */
    public SamplingParams {
        if (topK != null && topK <= 0) {
            throw new IllegalArgumentException("topK must be positive: " + topK);
        }
    }
}
