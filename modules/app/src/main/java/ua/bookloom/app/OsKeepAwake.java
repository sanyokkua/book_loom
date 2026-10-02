package ua.bookloom.app;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.ui.KeepAwake;

/**
 * Keeps the computer awake while a translation is at work by running the operating system's own keep-awake command
 * ({@link KeepAwakeCommand}) and ending it when the run stops working. The command is started and ended on one daemon
 * thread of its own, in the order asked, so the FX thread that asks never waits for a process; a command that is
 * missing or fails to start is logged and the run goes on as before. A shutdown hook ends a command still running.
 */
@Slf4j
@Singleton
final class OsKeepAwake implements KeepAwake {

    /** Starts a process for a command; a seam, so a test never spawns one. */
    @FunctionalInterface
    interface Processes {

        /**
         * Starts {@code command}.
         *
         * @param command the command and its arguments
         * @return the running process, or empty when it could not be started
         */
        Optional<Process> start(List<String> command);
    }

    private final @Nullable List<String> command;
    private final Processes processes;
    private final ExecutorService worker;
    // Read and written only on the worker thread, and by the shutdown hook after the worker is gone.
    private volatile @Nullable Process held;

    @Inject
    OsKeepAwake() {
        this(
                KeepAwakeCommand.forOs(
                                System.getProperty("os.name", ""),
                                ProcessHandle.current().pid(),
                                OsKeepAwake::isOnPath)
                        .orElse(null),
                OsKeepAwake::startDiscardingOutput);
        Runtime.getRuntime()
                .addShutdownHook(
                        Thread.ofPlatform().name("bookloom-keep-awake-exit").unstarted(this::end));
    }

    OsKeepAwake(final @Nullable List<String> command, final Processes processes) {
        this.command = command == null ? null : List.copyOf(command);
        this.processes = Objects.requireNonNull(processes, "processes");
        this.worker = Executors.newSingleThreadExecutor(
                Thread.ofPlatform().daemon().name("bookloom-keep-awake").factory());
        if (command == null) {
            log.warn("keep-awake not implemented on this OS; the computer may sleep during a long run");
        }
    }

    @Override
    public void start() {
        worker.execute(this::hold);
    }

    @Override
    public void stop() {
        worker.execute(this::end);
    }

    /** Runs {@code then} after every start and stop asked so far, on the same thread, for a test to wait on. */
    void afterPending(final Runnable then) {
        worker.execute(then);
    }

    private void hold() {
        final Process running = held;
        if (running != null && running.isAlive()) {
            log.debug("keep-awake already held by pid {}", running.pid());
            return;
        }
        if (command == null) {
            log.debug("keep-awake asked for, but this OS has no command for it");
            return;
        }
        held = processes.start(command).orElse(null);
        final Process started = held;
        if (started != null) {
            log.info("keep-awake started: {} pid {}", command.getFirst(), started.pid());
        }
    }

    private void end() {
        final Process running = held;
        held = null;
        if (running != null) {
            running.destroy();
            log.info("keep-awake stopped: pid {}", running.pid());
        }
    }

    private static Optional<Process> startDiscardingOutput(final List<String> command) {
        try {
            return Optional.of(new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start());
        } catch (IOException | RuntimeException failure) {
            log.warn("keep-awake command {} could not be started; the computer may sleep", command.getFirst(), failure);
            return Optional.empty();
        }
    }

    private static boolean isOnPath(final String program) {
        final String path = System.getenv("PATH");
        return path != null
                && Arrays.stream(path.split(System.getProperty("path.separator", ":")))
                        .anyMatch(directory -> Files.isExecutable(Path.of(directory, program)));
    }
}
