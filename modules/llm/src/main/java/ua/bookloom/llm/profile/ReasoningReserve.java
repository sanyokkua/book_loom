package ua.bookloom.llm.profile;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.SourceSize;

/**
 * The room a call that thinks needs for its reasoning on top of the answer's cap: both dialects count reasoning against
 * the cap, so a thinking call with the answer's cap alone comes back empty and cut for length. The room grows with the
 * source the call reads and the items it answers, and is never below {@value #MIN_TOKENS} tokens (design D20).
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReasoningReserve {

    /** The least room any thinking call is given. */
    public static final int MIN_TOKENS = 2048;

    private static final int TOKENS_PER_SOURCE_TOKEN = 4;
    private static final int TOKENS_PER_ITEM = 35;

    /**
     * The reserve for one call: {@code max(2048, 4·S + 35·n)} for a source of S tokens in n items.
     *
     * @param source the call's source size, or null when it does not say, which gives the floor
     * @return the reserve in tokens; at least {@value #MIN_TOKENS}
     */
    public static int tokens(final @Nullable SourceSize source) {
        if (source == null) {
            return MIN_TOKENS;
        }
        final long proportional =
                (long) TOKENS_PER_SOURCE_TOKEN * source.tokens() + (long) TOKENS_PER_ITEM * source.items();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(MIN_TOKENS, proportional));
    }
}
