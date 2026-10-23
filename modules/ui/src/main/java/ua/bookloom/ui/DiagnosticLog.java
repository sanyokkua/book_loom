package ua.bookloom.ui;

import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Where this launch writes its logs and whether the detailed diagnostic log is on, as the launcher resolved it.
 *
 * <p>The composition root binds the value; {@code :ui} cannot resolve it again, because the decision was made before
 * logging was configured and cannot change for the life of the process.
 *
 * @param directory the per-OS log directory, absolute
 * @param detailed whether {@code bookloom-trace.log} (TRACE for BookLoom's own loggers) is being written
 * @param marker the file in the data folder whose presence switches the detailed log on from the next launch
 * @param decidedBy the launch variable or property that decided {@code detailed}, or {@code null} when none did
 * @param developmentDefault whether {@code detailed} is on only because this is a development run (no variable,
 *     property or marker decided it), so the Settings switch cannot change it
 */
public record DiagnosticLog(
        Path directory,
        boolean detailed,
        Path marker,
        @Nullable String decidedBy,
        boolean developmentDefault) {

    /** Rejects a missing directory or marker. */
    public DiagnosticLog {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(marker, "marker");
    }
}
