package ua.bookloom.ui.state;

/** Tells the person that a book was written, wherever the window is when the export ends. */
@FunctionalInterface
public interface ExportAnnouncer {

    /**
     * Announces a book that was just written and checked. FX thread only.
     *
     * @param outcome the report and the size of the written file
     */
    void written(ExportOutcome outcome);
}
