package ua.bookloom.ui.state;

import com.google.inject.Singleton;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.pipeline.ProjectOpening;

/** The row actions until their dialogs exist: each is logged and does nothing. */
@Slf4j
@Singleton
public final class PendingProjectActions implements ProjectActions {

    @Override
    public void rename(final ProjectRow row) {
        pending("rename", row.summary().project().id());
    }

    @Override
    public void remove(final ProjectRow row) {
        pending("remove", row.summary().project().id());
    }

    @Override
    public void translateInto(final BookGroup group) {
        pending("translate into another language", group.contentHash());
    }

    @Override
    public void findBook(final ProjectRow row, final ProjectOpening opening) {
        Objects.requireNonNull(opening, "opening");
        pending(
                "find the book of " + opening.getClass().getSimpleName(),
                row.summary().project().id());
    }

    private static void pending(final String action, final String subject) {
        log.info("project action '{}' on {} arrives with the project dialogs", action, subject);
    }
}
