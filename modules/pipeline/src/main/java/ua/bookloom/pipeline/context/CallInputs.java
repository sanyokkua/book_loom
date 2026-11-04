package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Objects;
import ua.bookloom.pipeline.memory.ProtectedMask;

/**
 * What one translate-family call reads besides its window, the book map and the run frame.
 *
 * @param masks the masks of the focus items, in the items' order, whose tokens a locked entry is listed by; copied,
 *     empty when nothing is hidden
 * @param contextSize the window the call is sized for, the person's own override included; positive
 * @param memory the translation memory's lines for the focus, shown with the window; copied, empty for none
 * @param keyTerms the recurring key terms the model is asked to report renderings for; copied, empty when not asked
 * @param extra what only this kind of call adds
 */
public record CallInputs(
        List<ProtectedMask> masks, int contextSize, List<String> memory, List<String> keyTerms, KindExtra extra) {

    /** Copies the lists and rejects a size that is not positive. */
    public CallInputs {
        masks = List.copyOf(Objects.requireNonNull(masks, "masks"));
        memory = List.copyOf(Objects.requireNonNull(memory, "memory"));
        keyTerms = List.copyOf(Objects.requireNonNull(keyTerms, "keyTerms"));
        Objects.requireNonNull(extra, "extra");
        if (contextSize <= 0) {
            throw new IllegalArgumentException("contextSize must be positive: " + contextSize);
        }
    }

    /**
     * A call with masks and a window size only.
     *
     * @param masks the non-null masks of the focus items
     * @param contextSize the window the call is sized for; positive
     * @return the inputs; never null
     */
    public static CallInputs of(final List<ProtectedMask> masks, final int contextSize) {
        return new CallInputs(masks, contextSize, List.of(), List.of(), KindExtra.NONE);
    }
}
