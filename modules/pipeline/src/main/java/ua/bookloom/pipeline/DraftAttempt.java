package ua.bookloom.pipeline;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.pipeline.heal.PieceRedraft;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.prompt.DraftContext;
import ua.bookloom.pipeline.qa.LockedRendering;

/**
 * One segment's draft: the segment, the context around it, and the text the model is shown for it. The shown text is
 * the segment's masked text unless protected spans are hidden behind tokens, and every repair and the output
 * allowance follow it rather than the segment's own text.
 *
 * @param segment the segment being drafted
 * @param context the previously accepted targets shown as context
 * @param shownText the text the model translates; carries the tokens the reply must return
 * @param extraInstruction what a repair asks of the draft, or empty for a plain draft
 * @param pieceRedraft how the segment's pieces are drafted again, or null when it is not drafted in pieces
 * @param lockedRenderings the locked glossary terms hidden in the shown text, which the glossary check looks for
 */
record DraftAttempt(
        Segment segment,
        DraftContext context,
        String shownText,
        String extraInstruction,
        @Nullable PieceRedraft pieceRedraft,
        List<LockedRendering> lockedRenderings) {

    DraftAttempt {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(shownText, "shownText");
        Objects.requireNonNull(extraInstruction, "extraInstruction");
        lockedRenderings = List.copyOf(Objects.requireNonNull(lockedRenderings, "lockedRenderings"));
    }

    static DraftAttempt of(final Segment segment, final DraftContext context, final ProtectedMask mask) {
        return new DraftAttempt(segment, context, mask.maskedText(), "", null, mask.presentLocked());
    }

    static DraftAttempt ofPiece(final Segment piece, final DraftContext context, final String extraInstruction) {
        return new DraftAttempt(piece, context, piece.masked(), extraInstruction, null, List.of());
    }

    DraftAttempt redraftedBy(final PieceRedraft redraft) {
        return new DraftAttempt(segment, context, shownText, extraInstruction, redraft, lockedRenderings);
    }
}
