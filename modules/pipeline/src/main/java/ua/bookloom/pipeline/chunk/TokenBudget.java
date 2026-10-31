package ua.bookloom.pipeline.chunk;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** The size of one chunk; the window every call is sized against is {@code ModelWindow} in {@code :api}. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TokenBudget {

    /** The most tokens of source text one chunk holds: the model answers a small batch better than a large one. */
    public static final int MAX_CHUNK_TOKENS = 2000;
}
