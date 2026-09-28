package ua.bookloom.pipeline.chunk;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** The effective context every call is sized against. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenBudget {

    /** The context size sent as Ollama {@code num_ctx}: Ollama silently truncates at a much smaller default. */
    public static final int EFFECTIVE_CONTEXT = 8192;

    /** The most tokens of source text one chunk holds: the model answers a small batch better than a large one. */
    public static final int MAX_CHUNK_TOKENS = 1200;

    private static final double SAFETY_FACTOR = 1.15;

    /**
     * The source tokens one chunk may hold once the run's fixed context is reserved.
     *
     * @param reservedHeadroom the tokens the output allowance, style sheet, summary and injected terms occupy
     * @return the chunk budget, at most {@link #MAX_CHUNK_TOKENS}
     */
    public static int chunkTokens(final int reservedHeadroom) {
        return Math.min(EFFECTIVE_CONTEXT - reservedHeadroom, MAX_CHUNK_TOKENS);
    }

    /**
     * The output part of the headroom: what the translation of a completely full chunk may take.
     *
     * @param sourceTag the source language tag, or null when unknown
     * @param targetTag the target language tag; never null
     * @return the output allowance in tokens for the source characters a full budget holds
     */
    public static int fullChunkAllowance(@Nullable final String sourceTag, final String targetTag) {
        final int chars = (int) Math.floor(MAX_CHUNK_TOKENS * TokenEstimator.charsPerToken(sourceTag) / SAFETY_FACTOR);
        return TokenEstimator.outputAllowance("a".repeat(chars), sourceTag, targetTag);
    }
}
