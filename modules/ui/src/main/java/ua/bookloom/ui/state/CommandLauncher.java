package ua.bookloom.ui.state;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Starts an external program, behind a seam so a test sees the command without opening a real window. */
@FunctionalInterface
interface CommandLauncher {

    /**
     * Starts the program without waiting for it to finish.
     *
     * @param command the program followed by its arguments, run as given and never through a shell
     * @return the process's exit code, completed when it ends, which may be long after this returns
     * @throws IllegalStateException if the program cannot be started
     */
    CompletableFuture<Integer> launch(List<String> command);
}
