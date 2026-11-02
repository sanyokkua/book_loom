package ua.bookloom.ui.state;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * One line of an operation's results.
 *
 * @param id the row's number within its results, by which it is reverted
 * @param kind what the operation did to it
 * @param term the source term
 * @param before the row as it was, worded for the person; a dash for an added row
 * @param after the row as it is, worded for the person; a dash for a removed row
 * @param reason the model's own words for the change, or null when it gave none
 * @param origin where an added row came from; {@link ChangeOrigin#MODEL} for every other row
 * @param section the table the row belongs to, which a sectioned list shows as a heading
 * @param editable the target the person may type over in the dialog, or null when the row has none to give
 */
public record ChangeRow(
        long id,
        ChangeKind kind,
        String term,
        String before,
        String after,
        @Nullable String reason,
        ChangeOrigin origin,
        ChangeSection section,
        @Nullable EditableTarget editable) {

    /** A row with nothing to type over. */
    public ChangeRow(
            final long id,
            final ChangeKind kind,
            final String term,
            final String before,
            final String after,
            final @Nullable String reason,
            final ChangeOrigin origin,
            final ChangeSection section) {
        this(id, kind, term, before, after, reason, origin, section, null);
    }

    /** This row with a target to type over. */
    public ChangeRow withEditable(final EditableTarget target) {
        return new ChangeRow(id, kind, term, before, after, reason, origin, section, target);
    }

    /** Rejects missing parts. */
    public ChangeRow {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(after, "after");
    }
}
