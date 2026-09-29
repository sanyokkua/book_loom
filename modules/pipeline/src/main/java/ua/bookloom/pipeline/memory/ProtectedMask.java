package ua.bookloom.pipeline.memory;

import java.util.List;
import java.util.Objects;
import ua.bookloom.pipeline.qa.LockedRendering;

/**
 * A segment's text as the model is shown it, with what was hidden and how to put it back.
 *
 * @param maskedText the segment's masked text with every protected span replaced by its token; what the prompt shows
 * @param spans the hidden spans in document order
 * @param presentLocked each locked glossary term found in the segment, once, with the rendering it must come back as;
 *     a kept run is checked by its own gate and is not listed
 */
public record ProtectedMask(String maskedText, List<ProtectedSpan> spans, List<LockedRendering> presentLocked) {

    /** Rejects a missing component and copies the lists. */
    public ProtectedMask {
        Objects.requireNonNull(maskedText, "maskedText");
        Objects.requireNonNull(spans, "spans");
        Objects.requireNonNull(presentLocked, "presentLocked");
        spans = List.copyOf(spans);
        presentLocked = List.copyOf(presentLocked);
    }

    /**
     * A text with nothing hidden, for a draft made outside a chunk's glossary.
     *
     * @param maskedText the non-null text the model is shown as it is
     * @return the mask with no span and no locked term
     */
    public static ProtectedMask none(final String maskedText) {
        return new ProtectedMask(maskedText, List.of(), List.of());
    }
}
