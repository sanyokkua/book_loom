package ua.bookloom.ui.state;

import com.google.inject.Singleton;
import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.BookBrief;

/**
 * The one book the window has open, as a stored project.
 *
 * <p>A singleton because the screens are rebuilt on every visit while the open book must outlive them, and because the
 * parsed document is kept only by the pipeline: every screen reads the project's id and findings from here. Written
 * by the state classes of this package, read by any screen; the properties are touched on the FX Application Thread only.
 *
 * <p>The brief is a property of its own rather than a part of the book that is swapped whole: the person changes it
 * on every click, and replacing the book would look like opening another one to every listener that resets on that.
 * It starts as the brief the project was created with, so a run started from any screen reads it as it stands.
 */
@Slf4j
@Singleton
public final class CurrentProject {

    private final ReadOnlyObjectWrapper<@Nullable OpenedBook> book = new ReadOnlyObjectWrapper<>();

    private final ReadOnlyObjectWrapper<@Nullable BookBrief> brief = new ReadOnlyObjectWrapper<>();

    /**
     * The open book.
     *
     * @return a read-only property holding {@code null} while no book is open, including after a refusal
     */
    public ReadOnlyObjectProperty<@Nullable OpenedBook> book() {
        return book.getReadOnlyProperty();
    }

    /**
     * The brief of the open book as the person has changed it.
     *
     * @return a read-only property holding {@code null} while no book is open; it changes before the book does when a
     *     book is opened, so a listener on the book already sees the new book's brief
     */
    public ReadOnlyObjectProperty<@Nullable BookBrief> brief() {
        return brief.getReadOnlyProperty();
    }

    void replaceBrief(final BookBrief changed) {
        Objects.requireNonNull(changed, "changed");
        if (book.get() == null) {
            log.debug("the brief is not replaced: no book is open");
            return;
        }
        brief.set(changed);
    }

    void open(final OpenedBook opened) {
        Objects.requireNonNull(opened, "opened");
        log.debug("the current project is now {}", opened.projectId());
        log.trace("the current project's source is {}", opened.source());
        brief.set(opened.brief());
        book.set(opened);
    }

    void clear() {
        log.debug("no current project");
        book.set(null);
        brief.set(null);
    }
}
