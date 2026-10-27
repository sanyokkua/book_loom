package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One row of the live card: a segment the run has started, with what the run knows of it so far. Its marker
 * is worked out from it and the run's state ({@link RowMarker#of}), so a pause changes the markers without a new row.
 *
 * @param segmentId the segment's id
 * @param locator where the segment is, such as {@code ch7 · p42}
 * @param displaySource the source text with the inline marks shown as {@code ⟦1⟧}
 * @param target the draft or decided text, or {@code null} until a draft arrives
 * @param stage how far the segment has got
 * @param fallbackCode the first detail code of the batch reply's fault that made the engine draft this segment again
 *     alone ({@code MISSING}, {@code DUPLICATE}, {@code MERGED_SUSPECT}), or {@code null} when it was not
 * @param findingKinds the findings of a flagged segment, empty otherwise
 * @param callId the newest call that carried the segment, or {@code null} while none has
 */
public record LiveRow(
        String segmentId,
        String locator,
        String displaySource,
        @Nullable String target,
        Stage stage,
        @Nullable String fallbackCode,
        List<String> findingKinds,
        @Nullable Long callId) {

    /** How far a segment has got. */
    public enum Stage {
        /** Announced, not sent to the model yet. */
        QUEUED,
        /** In a call that waits for the model. */
        IN_CALL,
        /** Drafted, not decided yet. */
        DRAFTED,
        /** Accepted as a translation. */
        ACCEPTED,
        /** Flagged for the person's review. */
        FLAGGED,
        /** Accepted as it is, with no translation (numbers, symbols, a text kept as its source). */
        KEPT;

        /**
         * Whether the segment is decided, so no later call or draft changes its stage.
         *
         * @return {@code true} for accepted, flagged and kept, {@code false} otherwise
         */
        public boolean isDecided() {
            return this == ACCEPTED || this == FLAGGED || this == KEPT;
        }
    }

    /** Rejects a missing part and copies the findings. */
    public LiveRow {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(displaySource, "displaySource");
        Objects.requireNonNull(stage, "stage");
        findingKinds = List.copyOf(Objects.requireNonNull(findingKinds, "findingKinds"));
    }

    LiveRow inCall(final long call, final boolean waiting) {
        final Stage next = waiting && !stage.isDecided() ? Stage.IN_CALL : stage;
        return new LiveRow(segmentId, locator, displaySource, target, next, fallbackCode, findingKinds, call);
    }

    LiveRow fellBack(final String code) {
        return fallbackCode != null
                ? this
                : new LiveRow(segmentId, locator, displaySource, target, stage, code, findingKinds, callId);
    }

    LiveRow drafted(final String draft) {
        final Stage next = stage.isDecided() ? stage : Stage.DRAFTED;
        return new LiveRow(segmentId, locator, displaySource, draft, next, fallbackCode, findingKinds, callId);
    }

    LiveRow decided(final Stage outcome, final @Nullable String decided, final List<String> findings) {
        final String text = decided == null ? target : decided;
        return new LiveRow(segmentId, locator, displaySource, text, outcome, fallbackCode, findings, callId);
    }
}
