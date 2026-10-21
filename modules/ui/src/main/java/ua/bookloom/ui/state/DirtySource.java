package ua.bookloom.ui.state;

import ua.bookloom.ui.ViewNames;

/**
 * A form whose edits are kept only once saved, so leaving its screen while it holds changes asks first (Keep editing,
 * Discard or Save). A screen registers one through the UI module's set binding; none is registered until the Settings
 * forms (tasks 7.1a and 7.2) need one.
 */
public interface DirtySource {

    /**
     * The screen the form is on.
     *
     * @return the screen whose leaving is guarded; never null
     */
    ViewNames home();

    /**
     * Whether the form holds changes that are not saved.
     *
     * @return {@code true} if leaving now would lose changes, {@code false} otherwise
     */
    boolean isDirty();

    /** Drops the unsaved changes, so the form shows what is saved. FX thread only. */
    void discard();

    /**
     * Saves the changes, then runs {@code then}; a save that fails does not run it, so the person stays on the form.
     *
     * @param then what completes the navigation once the changes are saved
     */
    void save(Runnable then);
}
