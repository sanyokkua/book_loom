package ua.bookloom.pipeline.chunk;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The effective context every call is sized against. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenBudget {

    /** The context size sent as Ollama {@code num_ctx}: Ollama silently truncates at a much smaller default. */
    public static final int EFFECTIVE_CONTEXT = 8192;

    /** The most tokens of source text one chunk holds: the model answers a small batch better than a large one. */
    public static final int MAX_CHUNK_TOKENS = 1200;
}
