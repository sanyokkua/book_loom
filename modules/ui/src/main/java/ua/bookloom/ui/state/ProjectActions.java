package ua.bookloom.ui.state;

import ua.bookloom.api.pipeline.ProjectOpening;

/**
 * What the Projects screen's row menu, book header and missing-book answers lead to, behind a seam so the screen can
 * be built before the dialogs it opens exist (finalize 6.1c supplies them).
 */
public interface ProjectActions {

    /**
     * Renames the project of a row.
     *
     * @param row the row Rename… was chosen on
     */
    void rename(ProjectRow row);

    /**
     * Removes the project of a row after asking.
     *
     * @param row the row Remove project… was chosen on
     */
    void remove(ProjectRow row);

    /**
     * Starts another translation of a book, from its stored copy.
     *
     * @param group the book whose header's button was pressed
     */
    void translateInto(BookGroup group);

    /**
     * Helps the person point a project at its book file when the stored path no longer holds the same book.
     *
     * @param row the row that was being opened
     * @param opening why it did not open: the file is missing or its content changed
     */
    void findBook(ProjectRow row, ProjectOpening opening);
}
