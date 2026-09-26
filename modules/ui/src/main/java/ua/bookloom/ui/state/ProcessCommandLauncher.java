package ua.bookloom.ui.state;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

/**
 * Starts the command as an operating-system process from its argument list.
 *
 * <p>The program's output is discarded rather than piped, because nothing reads it and a full pipe would block a
 * program that writes a lot; the process is not awaited, because a file manager may outlive the click by minutes.
 */
@Slf4j
final class ProcessCommandLauncher implements CommandLauncher {

    @Override
    public void launch(final List<String> command) {
        Objects.requireNonNull(command, "command");
        log.debug("starting the process {}", command);
        try {
            new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException failure) {
            throw new IllegalStateException("cannot start " + command.getFirst(), failure);
        }
    }
}
