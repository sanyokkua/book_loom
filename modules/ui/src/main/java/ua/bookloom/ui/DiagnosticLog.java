package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where this launch writes its logs and whether the detailed diagnostic log is on, as the launcher resolved it.
 *
 * <p>The composition root binds the value; {@code :ui} cannot resolve it again, because the decision was made before
 * logging was configured and cannot change for the life of the process.
 *
 * @param directory the per-OS log directory, absolute
 * @param detailed whether {@code bookloom-trace.log} (TRACE for BookLoom's own loggers) is being written
 */
public record DiagnosticLog(Path directory, boolean detailed) {

    /** Rejects a missing directory. */
    public DiagnosticLog {
        Objects.requireNonNull(directory, "directory");
    }
}
