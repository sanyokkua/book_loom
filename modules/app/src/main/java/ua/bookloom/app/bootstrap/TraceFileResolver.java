package ua.bookloom.app.bootstrap;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.util.paths.AppEnvironment;

/**
 * Purely decides whether the detailed diagnostic log is written, from injected environment and property lookups.
 *
 * <p>On by default in a development run, so every hand test leaves a complete trace behind; off by default in an
 * installed app, where it is switched on by {@code BOOKLOOM_TRACE_FILE=1} or {@code -Dbookloom.trace.file=true}. Nothing
 * is persisted yet, so the environment variable is the durable way to keep it on.
 */
// Checkstyle parses source text before Lombok's annotation processor creates the private constructor,
// so suppress only its source-level utility-constructor false positive.
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TraceFileResolver {

    private static final String ENVIRONMENT_FLAG = "BOOKLOOM_TRACE_FILE";
    private static final String PROPERTY_FLAG = "bookloom.trace.file";
    private static final Set<String> ON = Set.of("1", "true", "yes", "on");
    private static final Set<String> OFF = Set.of("0", "false", "no", "off");

    /**
     * Resolves the first configured switch, or the environment's default.
     *
     * @param getEnv reads an environment variable, returning {@code null} when unset
     * @param getProperty reads a system property, returning {@code null} when unset
     * @param environment the resolved run environment; a development run defaults to on
     * @return the decision with where it came from, and the value that named neither on nor off, if any
     */
    public static ResolvedTraceFile resolve(
            Function<String, @Nullable String> getEnv,
            Function<String, @Nullable String> getProperty,
            AppEnvironment environment) {
        Objects.requireNonNull(getEnv, "getEnv");
        Objects.requireNonNull(getProperty, "getProperty");
        Objects.requireNonNull(environment, "environment");

        final String envValue = getEnv.apply(ENVIRONMENT_FLAG);
        if (envValue != null) {
            return resolveConfigured(envValue.trim(), ResolvedLogLevel.Source.ENVIRONMENT, environment);
        }
        final String propertyValue = getProperty.apply(PROPERTY_FLAG);
        if (propertyValue != null) {
            return resolveConfigured(propertyValue.trim(), ResolvedLogLevel.Source.PROPERTY, environment);
        }
        return new ResolvedTraceFile(environment.isDev(), ResolvedLogLevel.Source.DEFAULT, null);
    }

    private static ResolvedTraceFile resolveConfigured(
            String value, ResolvedLogLevel.Source source, AppEnvironment environment) {
        final String normalized = value.toLowerCase(Locale.ROOT);
        if (ON.contains(normalized)) {
            return new ResolvedTraceFile(true, source, null);
        }
        if (OFF.contains(normalized)) {
            return new ResolvedTraceFile(false, source, null);
        }
        return new ResolvedTraceFile(environment.isDev(), ResolvedLogLevel.Source.DEFAULT, value);
    }
}
