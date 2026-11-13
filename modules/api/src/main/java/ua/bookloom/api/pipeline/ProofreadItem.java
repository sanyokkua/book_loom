package ua.bookloom.api.pipeline;

import java.util.List;
import java.util.Objects;
import ua.bookloom.api.project.ProofreadOutcome;

/**
 * One focus segment of a proofread window as the window decided it. Its texts are book text: {@link #toString()}
 * leaves them out, so an event logged whole never carries them above TRACE.
 *
 * @param segmentId the segment's id
 * @param locator where the segment is, such as {@code ch7 · p42}
 * @param before the text the window read, with the inline marks shown as {@code ⟦1⟧}
 * @param after the text after the window: the kept rewrite, else the text it read
 * @param outcome what the window decided for the segment
 * @param reason the verdict's stored reason, such as a check's name and the words that broke it; empty when none
 * @param marks the reasons the segment was marked for the window; empty when it rode along unmarked
 */
public record ProofreadItem(
        String segmentId,
        String locator,
        String before,
        String after,
        ProofreadOutcome outcome,
        String reason,
        List<String> marks) {

    /** Rejects a missing part and copies the marks. */
    public ProofreadItem {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(reason, "reason");
        marks = List.copyOf(Objects.requireNonNull(marks, "marks"));
    }

    @Override
    public String toString() {
        return "ProofreadItem[segmentId=" + segmentId + ", locator=" + locator + ", outcome=" + outcome + "]";
    }
}
