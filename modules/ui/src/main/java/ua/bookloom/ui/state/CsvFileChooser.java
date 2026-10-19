package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Optional;
import javafx.stage.Window;
import org.jspecify.annotations.Nullable;

/**
 * Asks the person which CSV file to read or write, behind a seam so a screen test can answer without a real file
 * dialog opening on the machine that runs it. Both term tables on Names &amp; style use it for their Import and Export.
 */
public interface CsvFileChooser {

    /**
     * Shows an open dialog. FX thread only.
     *
     * @param owner the window the dialog belongs to, so that it opens over the app and not behind it; {@code null}
     *     only where the card is not in a window yet
     * @return the file the person picked, or empty if the dialog was cancelled
     */
    Optional<Path> chooseToOpen(@Nullable Window owner);

    /**
     * Shows a save dialog. FX thread only.
     *
     * @param owner the window the dialog belongs to; {@code null} only where the card is not in a window yet
     * @param suggestedName the file name the dialog starts with, for example {@code glossary.csv}
     * @return the file the person picked, or empty if the dialog was cancelled
     */
    Optional<Path> chooseToSave(@Nullable Window owner, String suggestedName);
}
