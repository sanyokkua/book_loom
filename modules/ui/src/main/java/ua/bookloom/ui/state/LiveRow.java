package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
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
 * @param awaitingJudge {@code true} when the draft has arrived and the chunk's judge has not decided it yet
 */
public record LiveRow(
        String segmentId,
        String locator,
        String sourceText,
        @Nullable String targetText,
        @Nullable Double judgeScore,
        @Nullable SegmentPath path,
        boolean awaitingDraft,
        boolean awaitingJudge) {

    /** Rejects a row without its identity and source. */
    public LiveRow {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(sourceText, "sourceText");
    }
}
