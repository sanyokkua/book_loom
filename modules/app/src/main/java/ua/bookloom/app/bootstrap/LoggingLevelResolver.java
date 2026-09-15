package ua.bookloom.app.bootstrap;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.slf4j.event.Level;
import ua.bookloom.util.paths.AppEnvironment;

/** Purely resolves the application log level from injected environment and property lookups. */
public final class LoggingLevelResolver {

    private static final String ENVIRONMENT_LEVEL = "BOOKLOOM_LOG_LEVEL";
    private static final String PROPERTY_LEVEL = "bookloom.log.level";

    private LoggingLevelResolver() {
        // Static resolver only.
    }

    /** Resolves the first configured level, or the environment-specific default. */
    public static ResolvedLogLevel resolve(
            Function<String, @Nullable String> getEnv,
            Function<String, @Nullable String> getProperty,
            AppEnvironment environment) {
        Objects.requireNonNull(getEnv, "getEnv");
        Objects.requireNonNull(getProperty, "getProperty");
        Objects.requireNonNull(environment, "environment");

        final String envValue = getEnv.apply(ENVIRONMENT_LEVEL);
        if (envValue != null) {
            return resolveConfigured(envValue.trim(), ResolvedLogLevel.Source.ENVIRONMENT, environment);
        }

        final String propertyValue = getProperty.apply(PROPERTY_LEVEL);
        if (propertyValue != null) {
            return resolveConfigured(propertyValue.trim(), ResolvedLogLevel.Source.PROPERTY, environment);
        }

        return defaultLevel(environment);
    }

    private static ResolvedLogLevel resolveConfigured(
            String value, ResolvedLogLevel.Source source, AppEnvironment environment) {
        final Level level = parse(value);
        if (level != null) {
            return new ResolvedLogLevel(level, source, Optional.empty());
        }
        return new ResolvedLogLevel(defaultFor(environment), ResolvedLogLevel.Source.DEFAULT, Optional.of(value));
    }

    private static ResolvedLogLevel defaultLevel(AppEnvironment environment) {
        return new ResolvedLogLevel(defaultFor(environment), ResolvedLogLevel.Source.DEFAULT, Optional.empty());
    }

    private static Level defaultFor(AppEnvironment environment) {
        return environment.isDev() ? Level.DEBUG : Level.INFO;
    }

    private static @Nullable Level parse(String value) {
        try {
            return Level.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
