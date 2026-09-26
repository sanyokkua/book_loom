package ua.bookloom.ui.state;

import java.util.List;

/** Starts an external program, behind a seam so a test sees the command without opening a real window. */
@FunctionalInterface
interface CommandLauncher {

    /**
     * Starts the program without waiting for it to finish.
     *
     * @param command the program followed by its arguments, run as given and never through a shell
     * @throws IllegalStateException if the program cannot be started
     */
    void launch(List<String> command);
}
