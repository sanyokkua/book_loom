package ua.bookloom.pipeline;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.pipeline.context.TranslateContext;
import ua.bookloom.pipeline.context.TranslatePlanner;
import ua.bookloom.pipeline.context.Window;
import ua.bookloom.pipeline.heal.PieceRedraft;
import ua.bookloom.pipeline.memory.ProtectedMask;
import ua.bookloom.pipeline.qa.LockedRendering;

/**
 * One segment's draft: the segment, the context around it, and the text the model is shown for it. The shown text is
 * the segment's masked text unless protected spans are hidden behind tokens, and every repair and the output
 * allowance follow it rather than the segment's own text.
 *
 * @param segment the segment being drafted
 * @param translate the window, map and memory a run's or a review retry's draft is planned with, which its re-ask and
 *     repair share; null for a draft step given no window, which is planned with none
 * @param shownText the text the model translates; carries the tokens the reply must return
 * @param extraInstruction what a repair asks of the draft, or empty for a plain draft
 * @param pieceRedraft how the segment's pieces are drafted again, or null when it is not drafted in pieces
 * @param lockedRenderings the locked glossary terms hidden in the shown text, which the glossary check looks for
 * @param lowerTemperature whether the draft call itself asks for its lower temperature, as a review retry may; a
 *     repair call has none and ignores it
 */
record DraftAttempt(
        Segment segment,
        @Nullable TranslateContext translate,
        String shownText,
        String extraInstruction,
        @Nullable PieceRedraft pieceRedraft,
        List<LockedRendering> lockedRenderings,
        boolean lowerTemperature) {

    DraftAttempt {
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(shownText, "shownText");
        Objects.requireNonNull(extraInstruction, "extraInstruction");
        lockedRenderings = List.copyOf(Objects.requireNonNull(lockedRenderings, "lockedRenderings"));
    }

    static DraftAttempt of(
            final Segment segment,
            @Nullable final TranslateContext translate,
            final ProtectedMask mask,
            final String extraInstruction,
            final boolean lowerTemperature) {
        return new DraftAttempt(
                segment, translate, mask.maskedText(), extraInstruction, null, mask.presentLocked(), lowerTemperature);
    }

    /**
     * One piece of an oversized segment: drafted with the segment's own neighbours and map, its focus the piece alone.
     */
    DraftAttempt ofPiece(final Segment piece, final String note, final boolean lower) {
        final @Nullable TranslateContext pieceContext = translate == null
                ? null
                : new TranslateContext(
                        translate.windowFor(TranslatePlanner.focusOf(piece.id(), piece.masked())),
                        translate.map(),
                        translate.memory(),
                        Map.of(piece.id(), ProtectedMask.none(piece.masked())),
                        List.of());
        return new DraftAttempt(piece, pieceContext, piece.masked(), note, null, List.of(), lower);
    }

    /**
     * The same draft with no previous pair, next source or memory line around it: an unsplittable segment is drafted
     * with as much room as its source can have.
     */
    DraftAttempt alone() {
        final @Nullable TranslateContext bare = translate == null
                ? null
                : new TranslateContext(
                        Window.of(TranslatePlanner.focusOf(segment.id(), shownText)),
                        translate.map(),
                        List.of(),
                        translate.masks(),
                        List.of());
        return new DraftAttempt(
                segment, bare, shownText, extraInstruction, pieceRedraft, lockedRenderings, lowerTemperature);
    }

    DraftAttempt redraftedBy(final PieceRedraft redraft) {
        return new DraftAttempt(
                segment, translate, shownText, extraInstruction, redraft, lockedRenderings, lowerTemperature);
    }
}
