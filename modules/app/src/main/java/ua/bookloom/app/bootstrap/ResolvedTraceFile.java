package ua.bookloom.app.bootstrap;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Immutable result of deciding whether the detailed diagnostic log ({@code bookloom-trace.log}) is written.
 *
 * @param enabled whether the trace file appender is started
 * @param source where the decision came from; {@link ResolvedLogLevel.Source#DEFAULT} also when a configured value
 *     named neither on nor off
 * @param rejectedValue the configured value that named neither on nor off, or {@code null} when nothing was rejected
 */
public record ResolvedTraceFile(
        boolean enabled,
        ResolvedLogLevel.Source source,
        @Nullable String rejectedValue) {

    /** Validates the source. */
    public ResolvedTraceFile {
        Objects.requireNonNull(source, "source");
    }
}
