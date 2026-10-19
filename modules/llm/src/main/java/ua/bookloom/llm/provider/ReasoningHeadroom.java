package ua.bookloom.llm.provider;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/**
 * The room a thinking call's reasoning needs on top of the answer's output cap: both dialects count the reasoning
 * text against the cap, so a call that thinks and keeps the answer's cap alone comes back empty and cut for length.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReasoningHeadroom {

    /** Tokens added to a cap when the call thinks. */
    public static final int TOKENS = 1024;

    /**
     * Raises a cap by the room for reasoning.
     *
     * @param cap the answer's output cap; {@code null} for no cap, which stays uncapped
     * @return {@code cap} plus {@link #TOKENS}, or {@code null} when there was no cap
     */
    public static @Nullable Integer raise(final @Nullable Integer cap) {
        return cap == null ? null : cap + TOKENS;
    }
}
