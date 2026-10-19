package ua.bookloom.ui.state;

/** What an operation did to one row of the glossary or the recurring terms. */
public enum ChangeKind {
    /** The operation created the row. */
    ADDED,
    /** The operation deleted the row. */
    REMOVED,
    /** The operation rewrote the row. */
    CHANGED
}
