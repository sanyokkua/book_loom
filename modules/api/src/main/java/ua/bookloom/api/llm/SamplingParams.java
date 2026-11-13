package ua.bookloom.api.llm;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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
 * @param withheld the wire names ({@code top_p}, {@code top_k}, {@code min_p}, {@code repeat_penalty}) of controls the
 *     model refused: a null control stays the server's own, but an engine's neutral stand-in for it is not sent either;
 *     never null
 */
public record SamplingParams(
        @Nullable Double topP,
        @Nullable Integer topK,
        @Nullable Double minP,
        @Nullable Double repeatPenalty,
        Set<String> withheld) {

    /** No control stated, so every one is the server's own. */
    public static final SamplingParams NONE = new SamplingParams(null, null, null, null);

    /** Rejects a non-positive top-k, which no engine reads as a limit. */
    public SamplingParams {
        if (topK != null && topK <= 0) {
            throw new IllegalArgumentException("topK must be positive: " + topK);
        }
        withheld = Set.copyOf(Objects.requireNonNull(withheld, "withheld"));
    }

    /** Controls as stated, none of them refused. */
    public SamplingParams(
            @Nullable Double topP, @Nullable Integer topK, @Nullable Double minP, @Nullable Double repeatPenalty) {
        this(topP, topK, minP, repeatPenalty, Set.of());
    }

    /**
     * Records that the model refused a control, so no stand-in for it is sent.
     *
     * @param wireName the control's wire name; non-null
     * @return these controls with that name added to {@link #withheld()}
     */
    public SamplingParams withWithheld(final String wireName) {
        final Set<String> more = new HashSet<>(withheld);
        more.add(Objects.requireNonNull(wireName, "wireName"));
        return new SamplingParams(topP, topK, minP, repeatPenalty, more);
    }
}
