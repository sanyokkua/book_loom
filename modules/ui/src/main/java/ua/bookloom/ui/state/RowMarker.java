package ua.bookloom.ui.state;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import ua.bookloom.ui.control.StateMarker;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The marker of a live row: a pure function of the row and the run-state row, so a pause turns the rows in a call to
 * "waiting" without touching them. It logs nothing, because the list's cells call it on every redraw.
 */
public enum RowMarker {
    /** Not sent yet, or in a call while the run does not work. */
    WAITING(StateMarker.Meaning.WAITING, MessageKey.LIVE_MARKER_WAITING),
    /** In a call the model works on. */
    TRANSLATING(StateMarker.Meaning.WORKING, MessageKey.LIVE_MARKER_TRANSLATING),
    /** Drafted while the run works, not decided yet: a reviewed chunk's checks decide all its drafts together. */
    DRAFTED(StateMarker.Meaning.WORKING, MessageKey.LIVE_MARKER_DRAFTED),
    /** Accepted. */
    TRANSLATED(StateMarker.Meaning.DONE, MessageKey.LIVE_MARKER_TRANSLATED),
    /** Flagged for the person's review. */
    TO_REVIEW(StateMarker.Meaning.ATTENTION, MessageKey.LIVE_MARKER_TO_REVIEW),
    /** Drafted again on its own because the batch reply could not be used for it. */
    ALONE(StateMarker.Meaning.DONE, MessageKey.LIVE_MARKER_ALONE),
    /** Accepted as it is, with no translation. */
    KEPT(StateMarker.Meaning.KEPT, MessageKey.LIVE_MARKER_KEPT);

    // The rows where a call in flight is still being answered: a pending pause or stop waits for it.
    private static final Set<RunRow> WORKING =
            EnumSet.of(RunRow.TRANSLATING, RunRow.PAUSING, RunRow.STOPPING, RunRow.STUCK);

    private final StateMarker.Meaning meaning;
    private final MessageKey word;

    RowMarker(final StateMarker.Meaning meaning, final MessageKey word) {
        this.meaning = meaning;
        this.word = word;
    }

    /** {@return the colour and icon the marker is drawn with, shared with every other state marker of the app} */
    public StateMarker.Meaning meaning() {
        return meaning;
    }

    /** {@return the catalogue entry of the marker's word} */
    public MessageKey word() {
        return word;
    }

    /**
     * The marker of a row while the run is in the given row of the run-state table.
     *
     * @param row the live row
     * @param run what the run is doing
     * @return the marker; a decided row keeps its marker in every run state
     */
    public static RowMarker of(final LiveRow row, final RunRow run) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(run, "run");
        return switch (row.stage()) {
            case FLAGGED -> TO_REVIEW;
            case KEPT -> KEPT;
            case ACCEPTED -> row.fallbackCode() == null ? TRANSLATED : ALONE;
            case DRAFTED -> row.fallbackCode() != null ? ALONE : drafted(run);
            case IN_CALL -> inFlight(run);
            case QUEUED -> WAITING;
        };
    }

    private static RowMarker drafted(final RunRow run) {
        return WORKING.contains(run) ? DRAFTED : WAITING;
    }

    private static RowMarker inFlight(final RunRow run) {
        return WORKING.contains(run) ? TRANSLATING : WAITING;
    }
}
