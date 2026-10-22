package ua.bookloom.ui;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ua.bookloom.api.pipeline.ProjectOpening;
import ua.bookloom.ui.state.BookGroup;
import ua.bookloom.ui.state.ProjectActions;
import ua.bookloom.ui.state.ProjectRow;

/** A hand-written {@link ProjectActions} that records what each row action was asked of instead of opening a dialog. */
public final class RecordingProjectActions implements ProjectActions {

    private final List<ProjectRow> renamed = new CopyOnWriteArrayList<>();
    private final List<String> renamedOpen = new CopyOnWriteArrayList<>();
    private final List<ProjectRow> removed = new CopyOnWriteArrayList<>();
    private final List<BookGroup> translatedInto = new CopyOnWriteArrayList<>();
    private final List<ProjectRow> findBookRows = new CopyOnWriteArrayList<>();
    private final List<ProjectOpening> findBookOpenings = new CopyOnWriteArrayList<>();

    @Override
    public void rename(final ProjectRow row) {
        renamed.add(row);
    }

    @Override
    public void renameOpen(final String projectId, final Runnable renamed) {
        renamedOpen.add(projectId);
    }

    @Override
    public void remove(final ProjectRow row) {
        removed.add(row);
    }

    @Override
    public void translateInto(final BookGroup group) {
        translatedInto.add(group);
    }

    @Override
    public void findBook(final ProjectRow row, final ProjectOpening opening) {
        findBookRows.add(row);
        findBookOpenings.add(opening);
    }

    /** The rows Rename… was chosen for, in call order. */
    public List<ProjectRow> renamed() {
        return List.copyOf(renamed);
    }

    /** The ids of the open projects Rename… was chosen for on the Book Brief, in call order. */
    public List<String> renamedOpen() {
        return List.copyOf(renamedOpen);
    }

    /** The rows Remove project… was chosen for, in call order. */
    public List<ProjectRow> removed() {
        return List.copyOf(removed);
    }

    /** The book groups ＋ Translate into another language was pressed for, in call order. */
    public List<BookGroup> translatedInto() {
        return List.copyOf(translatedInto);
    }

    /** The rows whose book file could not be opened and was asked for, in call order. */
    public List<ProjectRow> findBookRows() {
        return List.copyOf(findBookRows);
    }

    /** The answer that sent each {@link #findBookRows()} entry, in call order. */
    public List<ProjectOpening> findBookOpenings() {
        return List.copyOf(findBookOpenings);
    }
}
