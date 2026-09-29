package ua.bookloom.ui.state;

import com.google.inject.Singleton;
import java.util.Objects;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The one book the window has open, as a stored project.
 *
 * <p>A singleton because the screens are rebuilt on every visit while the open book must outlive them, and because the
 * parsed document is kept only by the pipeline: every screen reads the project's id and findings from here. Written
 * by the state classes of this package, read by any screen; the property is touched on the FX Application Thread only.
 */
@Slf4j
@Singleton
public final class CurrentProject {

    private final ReadOnlyObjectWrapper<@Nullable OpenedBook> book = new ReadOnlyObjectWrapper<>();

    /**
     * The open book.
     *
     * @return a read-only property holding {@code null} while no book is open, including after a refusal
     */
    public ReadOnlyObjectProperty<@Nullable OpenedBook> book() {
        return book.getReadOnlyProperty();
    }

    void open(final OpenedBook opened) {
        Objects.requireNonNull(opened, "opened");
        log.debug("the current project is now {}", opened.projectId());
        log.trace("the current project's source is {}", opened.source());
        book.set(opened);
    }

    void clear() {
        log.debug("no current project");
        book.set(null);
    }
}
