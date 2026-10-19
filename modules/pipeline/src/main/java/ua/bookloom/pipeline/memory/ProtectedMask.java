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
 * @param folded the document's own tokens taken out of {@code maskedText} — a drop cap's pair, wrapping the first
 *     letter of a word — which the gate puts back by position, so the model translates the whole word
 */
public record ProtectedMask(
        String maskedText, List<ProtectedSpan> spans, List<LockedRendering> presentLocked, List<String> folded) {

    /** Rejects a missing component and copies the lists. */
    public ProtectedMask {
        Objects.requireNonNull(maskedText, "maskedText");
        Objects.requireNonNull(spans, "spans");
        Objects.requireNonNull(presentLocked, "presentLocked");
        Objects.requireNonNull(folded, "folded");
        spans = List.copyOf(spans);
        presentLocked = List.copyOf(presentLocked);
        folded = List.copyOf(folded);
    }

    /** A mask that folds no token out. */
    public ProtectedMask(
            final String maskedText, final List<ProtectedSpan> spans, final List<LockedRendering> presentLocked) {
        this(maskedText, spans, presentLocked, List.of());
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
