package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.ContextSnapshot;
import ua.bookloom.api.project.SegmentPath;

/**
 * One row of the Translating screen's live panel: a segment as the run announced it, in display text only.
 *
 * @param segmentId the segment's stable id
 * @param locator the human-readable locator the person reads, such as {@code ch7 · p42}
 * @param sourceText the segment's source display text
 * @param targetText the draft's display text, or {@code null} until the draft arrives or when none was announced
 * @param judgeScore the judge's score, or {@code null} when the judge did not run
 * @param path how the segment reached its target, or {@code null} while it is undecided
 * @param awaitingDraft {@code true} while the model has not answered yet
 * @param awaitingReview {@code true} when the draft has arrived and the chunk's judge has not decided it yet
 * @param round the repair round the segment is in, or {@code null} while it is in none
 * @param context what the draft was sent with besides its source, or {@code null} when it was not announced
 */
public record LiveRow(
        String segmentId,
        String locator,
        String sourceText,
        @Nullable String targetText,
        @Nullable Double judgeScore,
        @Nullable SegmentPath path,
        boolean awaitingDraft,
        boolean awaitingReview,
        @Nullable RoundTrack round,
        @Nullable ContextSnapshot context) {

    /** Rejects a row without its identity and source. */
    public LiveRow {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(sourceText, "sourceText");
    }

    /**
     * Builds a row in no repair round and with no context announced.
     *
     * @param segmentId the segment's stable id
     * @param locator the human-readable locator
     * @param sourceText the segment's source display text
     * @param targetText the draft's display text, or {@code null}
     * @param judgeScore the judge's score, or {@code null}
     * @param path how the segment reached its target, or {@code null}
     * @param awaitingDraft {@code true} while the model has not answered yet
     * @param awaitingReview {@code true} while the chunk's judge has not decided the draft
     */
    public LiveRow(
            final String segmentId,
            final String locator,
            final String sourceText,
            @Nullable final String targetText,
            @Nullable final Double judgeScore,
            @Nullable final SegmentPath path,
            final boolean awaitingDraft,
            final boolean awaitingReview) {
        this(segmentId, locator, sourceText, targetText, judgeScore, path, awaitingDraft, awaitingReview, null, null);
    }

    /**
     * This row with the context its draft is sent with.
     *
     * @param sent the non-null context
     * @return a copy holding {@code sent}
     */
    public LiveRow withContext(final ContextSnapshot sent) {
        return new LiveRow(
                segmentId,
                locator,
                sourceText,
                targetText,
                judgeScore,
                path,
                awaitingDraft,
                awaitingReview,
                round,
                sent);
    }

    /**
     * This row in a repair round.
     *
     * @param track the non-null round
     * @return a copy in {@code track}, waiting for the round's answer
     */
    public LiveRow inRound(final RoundTrack track) {
        return new LiveRow(
                segmentId, locator, sourceText, targetText, judgeScore, path, awaitingDraft, false, track, context);
    }

    /**
     * This row with its draft.
     *
     * @param draft the draft's display text
     * @param judged {@code true} when the chunk's judge will decide the draft next
     * @return a copy holding the draft, no longer waiting for it
     */
    public LiveRow drafted(final String draft, final boolean judged) {
        return new LiveRow(segmentId, locator, sourceText, draft, null, null, false, judged, round, context);
    }

    /**
     * This row decided.
     *
     * @param score the judge's score, or {@code null}
     * @param reached how the segment reached its target, or {@code null}
     * @return a copy that waits for nothing
     */
    public LiveRow decided(@Nullable final Double score, @Nullable final SegmentPath reached) {
        return new LiveRow(segmentId, locator, sourceText, targetText, score, reached, false, false, round, context);
    }
}
