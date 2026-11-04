package ua.bookloom.pipeline.context;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * What only one kind of translate-family call adds in block 11 (design D8): a repair's previous attempt, a format
 * re-ask's rejected reply and the reader's diagnosis, and a note the person or a piece redraft gave. The defects a
 * repair names come from the focus's reasons, so one item's findings stay with that item.
 *
 * @param previousAttempt the masked text a repair rewrites, or null when the call is no repair
 * @param rejectedReply the raw reply a format re-ask quotes, or null when the call is no re-ask
 * @param diagnosis what the reply reader said of the rejected reply; empty when the call is no re-ask
 * @param note an instruction shown under {@code [Extra instruction]}; empty for none
 */
public record KindExtra(
        @Nullable String previousAttempt, @Nullable String rejectedReply, String diagnosis, String note) {

    /** Nothing beyond the window and the items. */
    public static final KindExtra NONE = new KindExtra(null, null, "", "");

    /** Rejects a missing diagnosis or note. */
    public KindExtra {
        Objects.requireNonNull(diagnosis, "diagnosis");
        Objects.requireNonNull(note, "note");
    }

    /**
     * A draft that carries a note.
     *
     * @param note the non-null note; empty for none
     * @return the extra; never null
     */
    public static KindExtra note(final String note) {
        return new KindExtra(null, null, "", note);
    }

    /**
     * A repair of a previous attempt.
     *
     * @param previousAttempt the non-null masked text the repair rewrites
     * @param note the non-null note the draft carried; empty for none
     * @return the extra; never null
     */
    public static KindExtra repair(final String previousAttempt, final String note) {
        return new KindExtra(Objects.requireNonNull(previousAttempt, "previousAttempt"), null, "", note);
    }

    /**
     * A format re-ask of a reply the reader refused.
     *
     * @param rejectedReply the non-null raw reply
     * @param diagnosis the non-null diagnosis of the reader
     * @param note the non-null note the draft carried; empty for none
     * @return the extra; never null
     */
    public static KindExtra reask(final String rejectedReply, final String diagnosis, final String note) {
        return new KindExtra(null, Objects.requireNonNull(rejectedReply, "rejectedReply"), diagnosis, note);
    }
}
