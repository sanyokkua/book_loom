package ua.bookloom.pipeline.context;

import java.util.Objects;

/**
 * One block of a planned prompt (design D8): blocks 1–7 make the system message, fixed for the run; blocks 8–13 the
 * last user message.
 *
 * @param slot the block's name, one of {@link BlockSlots}
 * @param role which message carries it
 * @param stability how long its text stays the same, which is what a server's prefix cache can reuse
 * @param text the block as sent; never blank, since a block with nothing to say is not planned
 */
public record Block(String slot, Role role, Stability stability, String text) {

    /** Which message a block is sent in. */
    public enum Role {
        /** The system message. */
        SYSTEM,
        /** The last user message. */
        USER
    }

    /** How long a block's text stays the same. */
    public enum Stability {
        /** The whole run. */
        RUN,
        /** One chapter. */
        CHAPTER,
        /** One window of segments. */
        WINDOW,
        /** One call. */
        CALL
    }

    /** Rejects a missing part or an empty block. */
    public Block {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(stability, "stability");
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new IllegalArgumentException("block " + slot + " is empty");
        }
    }
}
