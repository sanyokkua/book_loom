package ua.bookloom.app;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/** Starts a process for a command; a seam, so a test never spawns one. */
@FunctionalInterface
public interface Processes {

    /**
     * Starts {@code command}.
     *
     * @param command the command and its arguments
     * @return the running process, or empty when it could not be started
     */
    Optional<Process> start(List<String> command);

    /**
     * The real thing: the process's output and errors are thrown away so it never blocks on a full pipe.
     *
     * @return a starter that logs a failure and answers empty
     */
    static Processes system() {
        return Starter.INSTANCE;
    }

    /** The implementation behind {@link #system()}. */
    @Slf4j
    enum Starter implements Processes {
        /** The one instance. */
        INSTANCE;

        @Override
        public Optional<Process> start(final List<String> command) {
            try {
                return Optional.of(new ProcessBuilder(command)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start());
            } catch (IOException | RuntimeException failure) {
                log.warn("process {} could not be started", command.getFirst(), failure);
                return Optional.empty();
            }
        }
    }
}
