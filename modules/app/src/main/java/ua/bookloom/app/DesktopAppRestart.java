package ua.bookloom.app;

import com.google.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.AppError;
import ua.bookloom.api.AppRestart;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.Result;

/**
 * Restarts the window by closing it and leaving the next copy's command for the launcher. The next copy is started
 * only after this process closed the database and released the lock, never here, so it cannot meet files this process
 * still holds.
 */
@Slf4j
final class DesktopAppRestart implements AppRestart {

    private final PendingRestart pending;
    private final Supplier<ProcessHandle.Info> info;
    private final Runnable exit;

    @Inject
    DesktopAppRestart(final StartupContext startup) {
        this(
                Objects.requireNonNull(startup, "startup").pendingRestart(),
                () -> ProcessHandle.current().info(),
                Platform::exit);
    }

    DesktopAppRestart(final PendingRestart pending, final Supplier<ProcessHandle.Info> info, final Runnable exit) {
        this.pending = Objects.requireNonNull(pending, "pending");
        this.info = Objects.requireNonNull(info, "info");
        this.exit = Objects.requireNonNull(exit, "exit");
    }

    @Override
    public Result<Boolean> restart() {
        try {
            final Optional<List<String>> command = RestartCommand.of(info.get());
            if (command.isEmpty()) {
                log.warn("restart: this process does not report how it was started; nothing done");
                return failure(null);
            }
            if (!pending.request(command.get())) {
                log.debug("restart: already asked, the window is closing");
                return Result.ok(true);
            }
            log.info("restart: the next copy is left for the launcher; closing this one");
            exit.run();
            return Result.ok(true);
        } catch (RuntimeException e) {
            log.error("restart failed", e);
            return failure(e);
        }
    }

    private static Result<Boolean> failure(@Nullable final Throwable cause) {
        return Result.err(AppError.of(
                ErrorCode.internal,
                "BookLoom could not restart",
                "BookLoom could not start again by itself. Close it and open it again.",
                null,
                cause));
    }
}
