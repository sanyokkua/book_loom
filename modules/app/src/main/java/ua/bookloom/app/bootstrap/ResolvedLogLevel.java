package ua.bookloom.app.bootstrap;

import java.util.Objects;
import java.util.Optional;
import org.slf4j.event.Level;

/** Immutable result of resolving the configured application log level. */
public record ResolvedLogLevel(Level level, Source source, Optional<String> rejectedValue) {

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
        Objects.requireNonNull(rejectedValue, "rejectedValue");
    }
}
