package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Provider;
import java.util.Objects;

/**
 * The screens' state a project action reaches back to; providers, because they hold the actions themselves.
 *
 * @param opener what opens a project after a dialog
 * @param list the Projects list to refresh after a change
 * @param importer the Import screen's state, reset when its project is removed
 */
public record ProjectViews(
        Provider<RowOpener> opener, Provider<ProjectsViewModel> list, Provider<ImportViewModel> importer) {

    /** Rejects a missing part. */
    @Inject
    public ProjectViews {
        Objects.requireNonNull(opener, "opener");
        Objects.requireNonNull(list, "list");
        Objects.requireNonNull(importer, "importer");
    }
}
