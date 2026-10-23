package ua.bookloom.app;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * The command of the next copy, left by a restart and run by the launcher only after this process has closed the
 * database and let go of the single-instance lock, so the new copy never meets files this one still holds. The
 * command's arguments may hold paths, so they are never logged.
 */
@Slf4j
public final class PendingRestart {

    private final AtomicReference<@Nullable List<String>> command = new AtomicReference<>();

    /** Created empty by the launcher. */
    public PendingRestart() {
        // nothing is pending yet
    }

    /**
     * Asks for the next copy to be started after shutdown.
     *
     * @param next the command and its arguments; never empty
     * @return {@code true} when this is the first ask, {@code false} when a restart was already asked
     */
    public boolean request(final List<String> next) {
        Objects.requireNonNull(next, "next");
        return command.compareAndSet(null, List.copyOf(next));
    }

    /**
     * The command asked for.
     *
     * @return the command, or empty when no restart was asked
     */
    public Optional<List<String>> requested() {
        return Optional.ofNullable(command.get());
    }

    /**
     * Starts the next copy if one was asked for. A copy that cannot start is logged: the window is gone either way.
     *
     * @param processes starts the process
     */
    public void spawnIfAsked(final Processes processes) {
        Objects.requireNonNull(processes, "processes");
        final List<String> next = command.get();
        if (next == null) {
            log.debug("no restart asked; nothing to start");
            return;
        }
        log.info("starting the next copy after shutdown");
        try {
            if (processes.start(next).isEmpty()) {
                log.warn("the next copy could not be started; the window is closed");
            }
        } catch (RuntimeException e) {
            log.warn("the next copy could not be started; the window is closed", e);
        }
    }
}
