package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.project.SegmentPath;
import ua.bookloom.ui.control.StateMarker;
import ua.bookloom.ui.i18n.MessageKey;

/**
 * The marker of a review desk row: what became of its segment, so the person tells what is done from what is left. A
 * pure function of the row; it logs nothing, because the list's cells call it on every redraw.
 */
public enum DeskMarker {
    /** Flagged and not decided yet. */
    TO_REVIEW(StateMarker.Meaning.ATTENTION, MessageKey.REVIEW_MARKER_TO_REVIEW),
    /** Accepted, by the run or by the person. */
    ACCEPTED(StateMarker.Meaning.DONE, MessageKey.REVIEW_MARKER_ACCEPTED),
    /** The person's own edit is its translation. */
    YOUR_EDIT(StateMarker.Meaning.DONE, MessageKey.REVIEW_MARKER_YOUR_EDIT),
    /** Left for later in this application run; still to review. */
    LATER(StateMarker.Meaning.WAITING, MessageKey.REVIEW_MARKER_LATER),
    /** Kept in the original language by choice. */
    KEPT(StateMarker.Meaning.KEPT, MessageKey.REVIEW_KEPT_AS_SOURCE);

    private final StateMarker.Meaning meaning;
    private final MessageKey word;

    DeskMarker(final StateMarker.Meaning meaning, final MessageKey word) {
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
     * The marker of a listed segment. A segment kept as source says so whatever else holds; one the person left for
     * later says so before its status.
     *
     * @param view the segment as the desk listed it
     * @param later whether the person left it for later in this application run
     * @return the marker; never null
     */
    public static DeskMarker of(final SegmentView view, final boolean later) {
        Objects.requireNonNull(view, "view");
        if (view.path() == SegmentPath.SOURCE_KEPT) {
            return KEPT;
        }
        if (later) {
            return LATER;
        }
        return switch (view.status()) {
            case PENDING, FLAGGED -> TO_REVIEW;
            case ACCEPTED -> ACCEPTED;
            case REVISED -> YOUR_EDIT;
        };
    }
}
