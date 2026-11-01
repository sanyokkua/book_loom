package ua.bookloom.ui.state;

/** Where a target typed in the results dialog is written: the same edit the table's own cell makes. */
@FunctionalInterface
public interface TargetEdits {

    /** Writes nothing, for a dialog whose rows cannot be edited. */
    TargetEdits NONE = (section, key, text) -> {};

    /**
     * Sets a row's target.
     *
     * @param section the table the row belongs to
     * @param key the row's {@link EditableTarget#key()}
     * @param text the typed target; blank clears it
     */
    void set(ChangeSection section, String key, String text);
}
