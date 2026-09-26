package ua.bookloom.ui.state;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.ui.BackgroundExecutor;

/**
 * The production {@link FileRevealer}: shows the written file in the system's file manager by starting the command
 * {@link OsCommand} builds for the running system.
 *
 * <p>The process is started on the background pool because starting one is I/O that can stall, and the press is on
 * the FX thread. Showing a file is a convenience, so a command that cannot start, or a pool that has stopped, is
 * logged once and otherwise ignored: a missing file manager must not turn a finished run into an error. Nothing here
 * touches the network; the command holds only the local path.
 */
@Slf4j
@Singleton
public final class PlatformFileRevealer implements FileRevealer {

    private final ExecutorService executor;
    private final CommandLauncher launcher;
    private final Supplier<String> osName;

    /**
     * Creates the production revealer over a real process launcher and the running system's {@code os.name}.
     *
     * @param executor the pool the process is started on
     */
    @Inject
    public PlatformFileRevealer(@BackgroundExecutor final ExecutorService executor) {
        this(executor, new ProcessCommandLauncher(), () -> System.getProperty("os.name", ""));
    }

    PlatformFileRevealer(
            final ExecutorService executor, final CommandLauncher launcher, final Supplier<String> osName) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.launcher = Objects.requireNonNull(launcher, "launcher");
        this.osName = Objects.requireNonNull(osName, "osName");
    }

    @Override
    public void reveal(final Path file) {
        Objects.requireNonNull(file, "file");
        final List<String> command = OsCommand.forReveal(osName.get(), file);
        log.debug("showing {} with {}", file, command);
        try {
            executor.execute(() -> launch(command));
        } catch (RejectedExecutionException stopped) {
            log.warn(
                    "cannot show the written file with {}: the background pool has stopped",
                    command.getFirst(),
                    stopped);
        }
    }

    private void launch(final List<String> command) {
        try {
            launcher.launch(command);
            log.info("started {} to show the written file", command.getFirst());
        } catch (RuntimeException failure) {
            log.warn("could not start {} to show the written file", command.getFirst(), failure);
        }
    }
}
