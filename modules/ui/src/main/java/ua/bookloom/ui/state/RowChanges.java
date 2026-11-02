package ua.bookloom.ui.state;

import java.util.Objects;
import java.util.function.Consumer;
import ua.bookloom.api.project.Gender;
import ua.bookloom.api.project.TargetState;
import ua.bookloom.api.project.TermType;

/**
 * The edits a glossary row's cells and the Add term card make: State, type, gender and a new term. An answer that arrives after a later
 * opening of the screen is dropped. FX thread only.
 */
public final class RowChanges {

    private final GlossaryRowEdits edits;
    private final GlossaryAdditions additions;
    private final Openings openings;

    RowChanges(final GlossaryRowEdits edits, final GlossaryAdditions additions, final Openings openings) {
        this.edits = edits;
        this.additions = additions;
        this.openings = openings;
    }

    /**
     * Adds a term, refusing a blank source, a duplicate or a lock with no target before asking the service.
     *
     * @param term what the Add term card collected
     * @param onAdded run on the FX thread once the term is stored and shown
     * @param onRefused run on the FX thread with the reason, in the display language, the term was not added
     */
    public void add(final NewTerm term, final Runnable onAdded, final Consumer<String> onRefused) {
        additions.add(term, onAdded, onRefused);
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
