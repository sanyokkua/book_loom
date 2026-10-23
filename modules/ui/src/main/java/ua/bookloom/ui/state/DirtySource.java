package ua.bookloom.ui.state;

import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.ViewNames;

/**
 * A form whose edits are kept only once saved, so leaving its screen while it holds changes asks first (Keep editing,
 * Discard or Save). A screen registers one through the UI module's set binding; the Providers form and the Models form register one together.
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

    /**
     * What the changes belong to, so the question can name it.
     *
     * @return the words and the name of the thing being edited, or {@code null} when the question needs none
     */
    default @Nullable UnsavedSubject subject() {
        return null;
    }

    /** Drops the unsaved changes, so the form shows what is saved. FX thread only. */
    void discard();

    /**
     * Saves the changes, then runs {@code then}; a save that fails does not run it, so the person stays on the form.
     *
     * @param then what completes the navigation once the changes are saved
     */
    void save(Runnable then);
}
