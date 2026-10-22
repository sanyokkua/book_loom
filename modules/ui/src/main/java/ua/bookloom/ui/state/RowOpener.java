package ua.bookloom.ui.state;

/**
 * Opens the project of a row, the seam that lets the project dialogs open a project without depending on the
 * {@link ProjectOpener} that itself hands a missing book file to them.
 */
public interface RowOpener {

    /**
     * Opens a row's project at its step. Returns at once; FX thread only.
     *
     * @param row the row to open
     */
    void open(ProjectRow row);
}
