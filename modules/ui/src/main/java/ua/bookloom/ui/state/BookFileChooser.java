package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Optional;
import javafx.stage.Window;
import org.jspecify.annotations.Nullable;

/**
 * Asks the person which file is a project's moved book, behind a seam so a test can answer without a real file dialog
 * opening on the machine that runs it.
 */
public interface BookFileChooser {

    /**
     * Shows an open dialog for a book file. FX thread only.
     *
     * @param owner the window the dialog belongs to, so that it opens over the app; {@code null} when none is showing
     * @return the file the person picked, or empty if the dialog was cancelled
     */
    Optional<Path> chooseToOpen(@Nullable Window owner);
}
