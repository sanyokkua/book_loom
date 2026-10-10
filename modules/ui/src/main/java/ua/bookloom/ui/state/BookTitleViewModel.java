package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * The book's one translated title and author as the Book Brief shows and edits them. They are the metadata segments of
 * the stored project, so the model's answer fills the rows once the run has translated them and a person's edit is the
 * one the export writes into the package, the table of contents and every page that carries the title.
 *
 * <p>A row is read from the review desk off the FX thread and published on it; an edit is saved the same way and the
 * row is read again, so what is shown is always what is stored. Touched on the FX thread only.
 */
@Slf4j
@Singleton
public final class BookTitleViewModel {

    /** The stored id of the book's title segment. */
    static final String TITLE_ID = "aux:title";

    /** The stored id of the book's first author segment. */
    static final String AUTHOR_ID = "aux:creator:0";

    /** Which of the two rows an edit is for. */
    public enum Part {
        /** The book's title. */
        TITLE(TITLE_ID),
        /** The book's first author. */
        AUTHOR(AUTHOR_ID);

        private final String segmentId;

        Part(final String segmentId) {
            this.segmentId = segmentId;
        }

        String segmentId() {
            return segmentId;
        }
    }

    /**
     * What a row shows.
     *
     * @param source the text the book carries; empty when the book has none, and then the row is not shown
     * @param target the translated text, or empty until the run has translated it
     * @param editable whether the translated text can be edited now, which it can once it exists
     */
    public record Row(String source, String target, boolean editable) {

        /** Rejects a missing text. */
        public Row {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
        }

        static Row none() {
            return new Row("", "", false);
        }
    }

    private final CurrentProject project;
    private final ReviewDesk desk;
    private final ExecutorService executor;
    private final ReadOnlyObjectWrapper<Row> title = new ReadOnlyObjectWrapper<>(Row.none());
    private final ReadOnlyObjectWrapper<Row> author = new ReadOnlyObjectWrapper<>(Row.none());
    private final ReadOnlyIntegerWrapper edits = new ReadOnlyIntegerWrapper();

    /**
     * Follows the open book.
     *
     * @param project the holder of the open book
     * @param desk the port the metadata segments are read and edited through
     * @param executor the daemon executor a read or save runs on, never the FX thread
     */
    @Inject
    public BookTitleViewModel(
            final CurrentProject project, final ReviewDesk desk, @BackgroundExecutor final ExecutorService executor) {
        this.project = Objects.requireNonNull(project, "project");
        this.desk = Objects.requireNonNull(desk, "desk");
        this.executor = Objects.requireNonNull(executor, "executor");
        project.book().addListener((observed, was, now) -> reset(now));
    }

    /**
     * The title row.
     *
     * @return a read-only property; an empty row while no book is open or the book has no title
     */
    public ReadOnlyObjectProperty<Row> title() {
        return title.getReadOnlyProperty();
    }

    /**
     * The author row.
     *
     * @return a read-only property; an empty row while no book is open or the book has no author
     */
    public ReadOnlyObjectProperty<Row> author() {
        return author.getReadOnlyProperty();
    }

    /**
     * How many of the person's edits have been stored and read back, so a screen can react to a change of the title or
     * author that came from the person and not from a run.
     *
     * @return a read-only counter that only ever grows
     */
    public ReadOnlyIntegerProperty edits() {
        return edits.getReadOnlyProperty();
    }

    /** Reads both rows again from the stored project, which a run may have filled since they were last read. */
    public void refresh() {
        final OpenedBook book = project.book().get();
        if (book == null) {
            log.debug("title rows not read: no book is open");
            return;
        }
        log.debug("reading the title rows of project {}", book.projectId());
        submit(() -> readBoth(book.projectId(), false));
    }

    /**
     * Saves a person's edit of the title or author, which the export then writes everywhere it stands.
     *
     * @param part which row was edited
     * @param text the new translated text; blank text or the text already stored changes nothing
     */
    public void save(final Part part, final String text) {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(text, "text");
        final OpenedBook book = project.book().get();
        final Row row = (part == Part.TITLE ? title : author).get();
        if (book == null || !row.editable() || text.isBlank() || text.strip().equals(row.target())) {
            log.debug("{} edit not saved: nothing to change", part);
            return;
        }
        log.debug("saving the edited {} of project {}", part, book.projectId());
        log.trace("edited {} is '{}'", part, text);
        submit(() -> saveOffThread(book.projectId(), part, text.strip()));
    }

    private void reset(final @Nullable OpenedBook book) {
        title.set(Row.none());
        author.set(Row.none());
        if (book != null) {
            refresh();
        }
    }

    private void saveOffThread(final String projectId, final Part part, final String text) {
        final Result<?> saved = desk.saveEdit(projectId, part.segmentId(), text);
        if (saved.isErr()) {
            log.warn(
                    "the edited {} was not saved: code {}",
                    part,
                    Objects.requireNonNull(saved.error()).code());
        }
        readBoth(projectId, saved.isOk());
    }

    private void readBoth(final String projectId, final boolean edited) {
        final Row titleRow = read(projectId, TITLE_ID);
        final Row authorRow = read(projectId, AUTHOR_ID);
        Platform.runLater(() -> publish(projectId, titleRow, authorRow, edited));
    }

    private Row read(final String projectId, final String segmentId) {
        final Result<SegmentView> found = desk.segment(projectId, segmentId);
        final SegmentView view = found.data();
        if (view == null) {
            log.debug("no {} segment in project {}", segmentId, projectId);
            return Row.none();
        }
        final String user = view.userTarget();
        final String machine = view.maskedMachineTarget();
        final String target = user != null ? user : machine == null ? "" : machine;
        final boolean translated = view.status() != SegmentStatus.PENDING;
        return new Row(view.displaySource(), translated ? target : "", translated && !target.isBlank());
    }

    private void publish(final String projectId, final Row titleRow, final Row authorRow, final boolean edited) {
        final OpenedBook book = project.book().get();
        if (book == null || !book.projectId().equals(projectId)) {
            log.debug("title rows of project {} dropped: another book is open", projectId);
            return;
        }
        title.set(titleRow);
        author.set(authorRow);
        if (edited) {
            edits.set(edits.get() + 1);
        }
    }

    private void submit(final Runnable work) {
        try {
            executor.execute(work);
        } catch (RejectedExecutionException rejected) {
            log.warn("a title read or save could not be started", rejected);
        }
    }
}
