package ua.bookloom.ui.state;

import java.nio.file.Path;

/**
 * Shows a written file in the operating system's file manager, behind a seam so a screen test can see the request
 * without a real file manager opening a window on the machine that runs it.
 */
public interface FileRevealer {

    /**
     * Asks the operating system to show {@code file}, selected where the platform can and otherwise its folder.
     *
     * @param file the written book
     */
    void reveal(Path file);
}
