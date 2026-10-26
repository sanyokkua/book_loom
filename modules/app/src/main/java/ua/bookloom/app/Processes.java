package ua.bookloom.app;

import java.io.IOException;
import java.util.List;
import java.util.Map;
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
     * Starts {@code command} with variables added to the environment it inherits. A starter that cannot set them
     * starts the command without them.
     *
     * @param command the command and its arguments
     * @param environment the variables to add
     * @return the running process, or empty when it could not be started
     */
    default Optional<Process> start(final List<String> command, final Map<String, String> environment) {
        return start(command);
    }

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
            return start(command, Map.of());
        }

        @Override
        public Optional<Process> start(final List<String> command, final Map<String, String> environment) {
            try {
                final ProcessBuilder builder = new ProcessBuilder(command)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .redirectError(ProcessBuilder.Redirect.DISCARD);
                builder.environment().putAll(environment);
                return Optional.of(builder.start());
            } catch (IOException | RuntimeException failure) {
                log.warn("process {} could not be started", command.getFirst(), failure);
                return Optional.empty();
            }
        }
    }
}
