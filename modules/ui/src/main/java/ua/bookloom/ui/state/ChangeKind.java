package ua.bookloom.ui.state;

/** What an operation did to one row of the glossary or the recurring terms. */
public enum ChangeKind {
    /** The operation created the row. */
    ADDED,
    /** The operation deleted the row. */
    REMOVED,
    /** The operation rewrote the row. */
    CHANGED,
    /** The operation left the row as it was and says why (Accept all's rejected suggestions); nothing to put back. */
    LEFT,
    /** The pass found the name but could not give it a target; the person types one in the results. */
    UNTARGETED;

    /**
     * Whether a row of this kind can be put back.
     *
     * @return {@code true} for a row the operation changed, {@code false} for one it left as it was
     */
    public boolean isRevertible() {
        return this != LEFT && this != UNTARGETED;
    }
}
