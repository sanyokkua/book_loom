package ua.bookloom.ui.state;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Asks the person where the translated book goes, behind a seam so a screen test can answer without a real file
 * dialog opening on the machine that runs it.
 */
public interface DestinationChooser {

    /**
     * Shows a save dialog. FX thread only.
     *
     * @param initial the file the dialog starts at; its folder and name are the starting point, and it need not exist
     * @return the file the person picked, or empty if the dialog was cancelled
     */
    Optional<Path> choose(Path initial);
}
