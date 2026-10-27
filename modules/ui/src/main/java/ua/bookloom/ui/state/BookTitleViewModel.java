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
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.Result;
import ua.bookloom.api.document.SegmentStatus;
import ua.bookloom.api.pipeline.OriginalTitle;
import ua.bookloom.api.pipeline.ReviewDesk;
import ua.bookloom.api.pipeline.SegmentView;
import ua.bookloom.api.pipeline.TitleOrigin;
import ua.bookloom.ui.BackgroundExecutor;
import ua.bookloom.ui.i18n.ErrorWords;
import ua.bookloom.ui.i18n.MessageKey;
import ua.bookloom.ui.i18n.Messages;

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
     * @param origin where the original title came from, or null for the author row and while nothing is read
     * @param sourceShown the original with its formatting marks in the number form ({@code ⟦0⟧}), as the card shows
     *     it and as an edit is checked against; the same as {@code source} when it has none
     */
    public record Row(
            String source,
            String target,
            boolean editable,
            @Nullable TitleOrigin origin,
            String sourceShown) {

        /** Rejects a missing text. */
        public Row {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(sourceShown, "sourceShown");
        }

        /** A row whose original carries no formatting marks. */
        Row(final String source, final String target, final boolean editable, final @Nullable TitleOrigin origin) {
            this(source, target, editable, origin, source);
        }

        static Row none() {
            return new Row("", "", false, null);
        }
    }

    private final CurrentProject project;
    private final ReviewDesk desk;
    private final ExecutorService executor;
    private final ReadOnlyObjectWrapper<Row> title = new ReadOnlyObjectWrapper<>(Row.none());
    private final ReadOnlyObjectWrapper<Row> author = new ReadOnlyObjectWrapper<>(Row.none());
    private final ReadOnlyIntegerWrapper edits = new ReadOnlyIntegerWrapper();
    private final ReadOnlyStringWrapper refusal = new ReadOnlyStringWrapper("");
    private final Messages messages;
    // The segment the shown title row was read from; null while there is none. FX thread only.
    private @Nullable String titleSegmentId;

    /**
     * Follows the open book.
     *
     * @param project the holder of the open book
     * @param desk the port the metadata segments are read and edited through
     * @param executor the daemon executor a read or save runs on, never the FX thread
     * @param messages the catalogue a refused edit is said from
     */
    @Inject
    public BookTitleViewModel(
            final CurrentProject project,
            final ReviewDesk desk,
            @BackgroundExecutor final ExecutorService executor,
            final Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
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

    /**
     * Why the person's last edit was not saved: marks it lacks, or the store's refusal.
     *
     * @return a read-only property; empty while the last edit was saved or none was made
     */
    public ReadOnlyStringProperty refusal() {
        return refusal.getReadOnlyProperty();
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
     * @param text the new translated text with its marks in the number form; blank text or the text already stored
     *     changes nothing, and a text lacking a mark of the original is refused here with the message
     */
    public void save(final Part part, final String text) {
        Objects.requireNonNull(part, "part");
        Objects.requireNonNull(text, "text");
        final OpenedBook book = project.book().get();
        final Row row = (part == Part.TITLE ? title : author).get();
        final String segmentId = part == Part.TITLE ? titleSegmentId : part.segmentId();
        if (book == null
                || segmentId == null
                || !row.editable()
                || text.isBlank()
                || text.strip().equals(row.target())) {
            log.debug("{} edit not saved: nothing to change", part);
            return;
        }
        final MarkStatus marks = MarkStatus.of(row.sourceShown(), text);
        log.debug("{} edit marks complete {}", part, marks.isComplete());
        if (!marks.isComplete()) {
            refusal.set(messages.get(MessageKey.EXPORT_BOOK_TITLE_MARKS_MISSING, String.join(" ", marks.missing())));
            return;
        }
        refusal.set("");
        log.debug("saving the edited {} of project {}", part, book.projectId());
        log.trace("edited {} is '{}'", part, text);
        submit(() -> saveOffThread(book.projectId(), part, segmentId, MarkStatus.store(text.strip())));
    }

    private void reset(final @Nullable OpenedBook book) {
        titleSegmentId = null;
        refusal.set("");
        title.set(Row.none());
        author.set(Row.none());
        if (book != null) {
            refresh();
        }
    }

    private void saveOffThread(final String projectId, final Part part, final String segmentId, final String text) {
        final Result<?> saved = desk.saveEdit(projectId, segmentId, text);
        final AppError error = saved.error();
        if (error != null) {
            log.warn("the edited {} was not saved: code {}", part, error.code());
            final String said =
                    messages.get(MessageKey.EXPORT_BOOK_TITLE_NOT_SAVED, ErrorWords.message(messages, error));
            Platform.runLater(() -> refusal.set(said));
        }
        readBoth(projectId, saved.isOk());
    }

    private void readBoth(final String projectId, final boolean edited) {
        final Read titleRead = readTitle(projectId);
        final Row authorRow = read(projectId, AUTHOR_ID, null);
        Platform.runLater(() -> publish(projectId, titleRead, authorRow, edited));
    }

    // The title's segment depends on the book: the resolver names it, or says that the file name stands in.
    private Read readTitle(final String projectId) {
        final OriginalTitle resolved = desk.originalTitle(projectId).data();
        if (resolved == null) {
            log.debug("no original title for project {}", projectId);
            return new Read(Row.none(), null);
        }
        final String segmentId = resolved.segmentId();
        if (segmentId == null) {
            log.debug("the title of project {} is its file name; nothing can be edited", projectId);
            return new Read(new Row(resolved.text(), "", false, resolved.origin()), null);
        }
        final Row row = read(projectId, segmentId, resolved.origin());
        // The resolver found a title the stored segment cannot show yet, so the row still says where it came from.
        return new Read(
                row.source().isEmpty() ? new Row(resolved.text(), "", false, resolved.origin()) : row, segmentId);
    }

    private Row read(final String projectId, final String segmentId, final @Nullable TitleOrigin origin) {
        final Result<SegmentView> found = desk.segment(projectId, segmentId);
        final SegmentView view = found.data();
        if (view == null) {
            log.debug("no {} segment in project {}", segmentId, projectId);
            return Row.none();
        }
        final String user = view.maskedUserTarget() != null ? view.maskedUserTarget() : view.userTarget();
        final String machine = view.maskedMachineTarget();
        final String target = MarkStatus.show(user != null ? user : machine == null ? "" : machine);
        final boolean translated = view.status() != SegmentStatus.PENDING;
        return new Row(
                view.displaySource(),
                translated ? target : "",
                translated && !target.isBlank(),
                origin,
                MarkStatus.show(view.maskedSource()));
    }

    private record Read(Row row, @Nullable String segmentId) {}

    private void publish(final String projectId, final Read titleRead, final Row authorRow, final boolean edited) {
        final OpenedBook book = project.book().get();
        if (book == null || !book.projectId().equals(projectId)) {
            log.debug("title rows of project {} dropped: another book is open", projectId);
            return;
        }
        titleSegmentId = titleRead.segmentId();
        title.set(titleRead.row());
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
