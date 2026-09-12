package ua.bookloom.document.mask;

import java.io.Serial;

/**
 * Thrown by {@link MaskWriter#build()} when the mask-time invariant fails: a token emitted more than once in the
 * masked form, or the placeholder map's keys not matching exactly the tokens present. The frozen rule calls this
 * "a parser-side invariant failure, not a model error" — it names a defect in this module's own masking, never a
 * translation problem, and its message carries only token names, never book text.
 */
public final class MaskInvariantException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param message what disagreed, naming tokens only; never null
     */
    public MaskInvariantException(String message) {
        super(message);
    }
}
