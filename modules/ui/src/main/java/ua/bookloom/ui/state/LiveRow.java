package ua.bookloom.ui.state;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.ChapterSummaryKept;
import ua.bookloom.api.pipeline.ProofreadItem;
import ua.bookloom.api.pipeline.ProofreadWindowRead;

/**
 * One row of the live card: a segment the run has started, with what the run knows of it so far, or a finished
 * chapter's kept summary ({@link Stage#SUMMARISED}), or a proofread window ({@link Stage#PROOFREAD}). Its marker is worked out from it and the run's state
 * ({@link RowMarker#of}), so a pause changes the markers without a new row.
 *
 * @param segmentId the segment's id; for a summary row {@value #SUMMARY_ID_PREFIX} and the chapter's key
 * @param locator where the segment is, such as {@code ch7 · p42}; for a summary row the chapter's number, which the
 *     card words as "Chapter {n} summarised" (the state holds no catalogue)
 * @param displaySource the source text with the inline marks shown as {@code ⟦1⟧}; empty for a summary row
 * @param target the draft or decided text, or {@code null} until a draft arrives; for a summary row its text
 * @param stage how far the segment has got
 * @param fallbackCode the first detail code of the batch reply's fault that made the engine draft this segment again
 *     alone ({@code MISSING}, {@code DUPLICATE}, {@code MERGED_SUSPECT}), or {@code null} when it was not
 * @param findingKinds the findings of a flagged segment, empty otherwise
 * @param callId the newest call that carried the segment, or {@code null} while none has
 * @param proofread a proofread window's focus segments with their verdicts, in reading order; empty for any other row
 */
public record LiveRow(
        String segmentId,
        String locator,
        String displaySource,
        @Nullable String target,
        Stage stage,
        @Nullable String fallbackCode,
        List<String> findingKinds,
        @Nullable Long callId,
        List<ProofreadItem> proofread) {

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
        KEPT,
        /** Not a segment: a finished chapter's summary was kept, which later drafts are told about. */
        SUMMARISED,
        /** Not a segment: a proofread window was read and its verdicts kept. */
        PROOFREAD;

        /**
         * Whether the segment is decided, so no later call or draft changes its stage.
         *
         * @return {@code true} for accepted, flagged, kept, a kept summary and a read window, {@code false} otherwise
         */
        public boolean isDecided() {
            return this == ACCEPTED || this == FLAGGED || this == KEPT || this == SUMMARISED || this == PROOFREAD;
        }
    }

    /** What a summary row's id starts with, so it never meets a segment's id. */
    public static final String SUMMARY_ID_PREFIX = "chapter-summary:";

    /** What a proofread window row's id starts with, so it never meets a segment's id. */
    public static final String WINDOW_ID_PREFIX = "proofread-window:";

    /**
     * The row of a kept chapter summary.
     *
     * @param kept the kept summary
     * @return a summarised row with no source and the summary as its text
     */
    public static LiveRow chapterSummary(final ChapterSummaryKept kept) {
        Objects.requireNonNull(kept, "kept");
        return new LiveRow(
                SUMMARY_ID_PREFIX + kept.chapterKey(),
                String.valueOf(kept.chapter()),
                "",
                kept.text(),
                Stage.SUMMARISED,
                null,
                List.of(),
                null);
    }

    /**
     * The row of a read proofread window: its span of locators, the texts before and after joined in reading order.
     *
     * @param read the read window
     * @return a proofread row that holds the window's items
     */
    public static LiveRow proofreadWindow(final ProofreadWindowRead read) {
        Objects.requireNonNull(read, "read");
        final List<ProofreadItem> items = read.items();
        final String span = WindowSpan.of(items);
        return new LiveRow(
                WINDOW_ID_PREFIX + "r" + read.round() + ":" + items.getFirst().segmentId(),
                span,
                joined(items, ProofreadItem::before),
                joined(items, ProofreadItem::after),
                Stage.PROOFREAD,
                null,
                List.of(),
                null,
                items);
    }

    private static String joined(final List<ProofreadItem> items, final Function<ProofreadItem, String> text) {
        return items.stream().map(text).collect(Collectors.joining("\n\n"));
    }

    /** Rejects a missing part and copies the findings and the window's items. */
    public LiveRow {
        Objects.requireNonNull(segmentId, "segmentId");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(displaySource, "displaySource");
        Objects.requireNonNull(stage, "stage");
        findingKinds = List.copyOf(Objects.requireNonNull(findingKinds, "findingKinds"));
        proofread = List.copyOf(Objects.requireNonNull(proofread, "proofread"));
    }

    /**
     * A row that is no proofread window.
     *
     * @param segmentId the segment's id, or a summary row's
     * @param locator where the segment is
     * @param displaySource the source text with the inline marks shown
     * @param target the draft or decided text, or {@code null} until a draft arrives
     * @param stage how far the segment has got
     * @param fallbackCode the batch fault that made the engine draft it alone, or {@code null}
     * @param findingKinds the findings of a flagged segment
     * @param callId the newest call that carried the segment, or {@code null}
     */
    public LiveRow(
            final String segmentId,
            final String locator,
            final String displaySource,
            final @Nullable String target,
            final Stage stage,
            final @Nullable String fallbackCode,
            final List<String> findingKinds,
            final @Nullable Long callId) {
        this(segmentId, locator, displaySource, target, stage, fallbackCode, findingKinds, callId, List.of());
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
