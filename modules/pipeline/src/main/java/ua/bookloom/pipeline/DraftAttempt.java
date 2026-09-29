package ua.bookloom.pipeline;

import java.util.Objects;
import ua.bookloom.api.document.Segment;
import ua.bookloom.pipeline.prompt.DraftContext;

/**
 * One segment's draft: the segment, the context around it, and the text the model is shown for it. The shown text is
 * the segment's masked text unless protected spans are hidden behind tokens, and every repair and the output
 * allowance follow it rather than the segment's own text.
 *
 * @param segment the segment being drafted
 * @param context the previously accepted targets shown as context
 * @param shownText the text the model translates; carries the tokens the reply must return
 */
record DraftAttempt(Segment segment, DraftContext context, String shownText) {

    DraftAttempt {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(shownText, "shownText");
    }

    static DraftAttempt showingItsOwnMaskedText(final Segment segment, final DraftContext context) {
        return new DraftAttempt(segment, context, segment.masked());
    }
}
