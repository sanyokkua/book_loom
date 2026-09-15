package ua.bookloom.app.bootstrap;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;

/**
 * Immutable result of resolving the configured application log level.
 *
 * @param level the level the {@code ua.bookloom} loggers use
 * @param source where the level came from; {@link Source#DEFAULT} also when a configured value named no level
 * @param rejectedValue the configured value that named no level, or {@code null} when nothing was rejected
 */
public record ResolvedLogLevel(
        Level level, Source source, @Nullable String rejectedValue) {

    /** The input that supplied the resolved level. */
    public enum Source {
        ENVIRONMENT,
        PROPERTY,
        DEFAULT
    }

    /** Validates and freezes the resolver result. */
    public ResolvedLogLevel {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(source, "source");
    }
}
