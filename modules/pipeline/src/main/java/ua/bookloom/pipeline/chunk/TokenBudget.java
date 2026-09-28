package ua.bookloom.pipeline.chunk;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The effective context every call is sized against. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenBudget {

    /** The context size sent as Ollama {@code num_ctx}: Ollama silently truncates at a much smaller default. */
    public static final int EFFECTIVE_CONTEXT = 8192;
}
