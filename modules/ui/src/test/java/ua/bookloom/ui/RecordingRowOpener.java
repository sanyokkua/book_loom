package ua.bookloom.ui;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import ua.bookloom.ui.state.ProjectRow;
import ua.bookloom.ui.state.RowOpener;

/** A hand-written {@link RowOpener} that records the rows it was asked to open instead of opening a project. */
public final class RecordingRowOpener implements RowOpener {

    private final List<ProjectRow> opened = new CopyOnWriteArrayList<>();

    @Override
    public void open(final ProjectRow row) {
        opened.add(row);
    }

    /** The rows asked to open, in call order. */
    public List<ProjectRow> opened() {
        return List.copyOf(opened);
    }
}
