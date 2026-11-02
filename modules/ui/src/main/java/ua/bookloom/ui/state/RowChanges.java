package ua.bookloom.ui.state;

import java.util.Objects;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.TargetState;
import ua.bookloom.api.project.TermType;

/**
 * The single-field edits a glossary row's cells make: State, type and gender. An answer that arrives after a later
 * opening of the screen is dropped. FX thread only.
 */
public final class RowChanges {

    private final GlossaryRowEdits edits;
    private final Openings openings;

    RowChanges(final GlossaryRowEdits edits, final Openings openings) {
        this.edits = edits;
        this.openings = openings;
    }

    /** Sets a row's State, which makes its target the person's; a row with no target is refused. */
    public void setState(final String entryId, final TargetState state) {
        Objects.requireNonNull(state, "state");
        edits.state(entryId, state, openings.whileCurrent());
    }

    /** Sets a row's type. */
    public void setType(final String entryId, final TermType type) {
        Objects.requireNonNull(type, "type");
        edits.change(entryId, "type", held -> GlossaryEdits.type(held, type), openings.whileCurrent());
    }

    /** Sets a row's gender. */
    public void setGender(final String entryId, final Gender gender) {
        Objects.requireNonNull(gender, "gender");
        edits.change(entryId, "gender", held -> GlossaryEdits.gender(held, gender), openings.whileCurrent());
    }
}
