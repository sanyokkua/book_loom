package ua.bookloom.ui.state;

import com.google.inject.Inject;
import java.util.Objects;

/**
 * What a project row leads to.
 *
 * @param opener what Continue and Open use
 * @param actions what the row menu and the book header lead to
 */
public record RowActions(ProjectOpener opener, ProjectActions actions) {

    /** Rejects a missing part. */
    @Inject
    public RowActions {
        Objects.requireNonNull(opener, "opener");
        Objects.requireNonNull(actions, "actions");
    }
}
