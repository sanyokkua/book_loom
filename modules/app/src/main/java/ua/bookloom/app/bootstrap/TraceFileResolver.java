package ua.bookloom.app.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
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
 * installed app, where it is switched on by {@code BOOKLOOM_TRACE_FILE=1} or {@code -Dbookloom.trace.file=true}. Settings ›
 * Storage &amp; logs keeps it on from the next launch with a marker file. The order is variable, property, marker,
 * default; a marker can only switch it on, and a value that names neither on nor off is kept for the warning while
 * the decision falls through. No logger here: this runs before logging is configured.
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
     * Resolves the first configured switch, then the marker file, or else the environment's default.
     *
     * @param getEnv reads an environment variable, returning {@code null} when unset
     * @param getProperty reads a system property, returning {@code null} when unset
     * @param environment the resolved run environment; a development run defaults to on
     * @param marker the file whose presence switches the log on
     * @return the decision with where it came from, and the values that named neither on nor off, if any
     */
    public static ResolvedTraceFile resolve(
            Function<String, @Nullable String> getEnv,
            Function<String, @Nullable String> getProperty,
            AppEnvironment environment,
            Path marker) {
        Objects.requireNonNull(marker, "marker");
        return decide(getEnv, getProperty, environment, marker);
    }

    /**
     * Resolves as {@link #resolve(Function, Function, AppEnvironment, Path)} does, for a launch that has no marker
     * (the command line).
     *
     * @param getEnv reads an environment variable, returning {@code null} when unset
     * @param getProperty reads a system property, returning {@code null} when unset
     * @param environment the resolved run environment; a development run defaults to on
     * @return the decision with where it came from, and the values that named neither on nor off, if any
     */
    public static ResolvedTraceFile resolve(
            Function<String, @Nullable String> getEnv,
            Function<String, @Nullable String> getProperty,
            AppEnvironment environment) {
        return decide(getEnv, getProperty, environment, null);
    }

    private static ResolvedTraceFile decide(
            Function<String, @Nullable String> getEnv,
            Function<String, @Nullable String> getProperty,
            AppEnvironment environment,
            @Nullable Path marker) {
        Objects.requireNonNull(getEnv, "getEnv");
        Objects.requireNonNull(getProperty, "getProperty");
        Objects.requireNonNull(environment, "environment");

        final String envValue = trimmed(getEnv.apply(ENVIRONMENT_FLAG));
        final Boolean fromEnv = envValue == null ? null : parseSwitch(envValue);
        final String propertyValue = trimmed(getProperty.apply(PROPERTY_FLAG));
        final Boolean fromProperty = propertyValue == null ? null : parseSwitch(propertyValue);
        final String rejected = rejectedOf(envValue, fromEnv, propertyValue, fromProperty);
        if (fromEnv != null) {
            return new ResolvedTraceFile(fromEnv, ResolvedTraceFile.Source.ENVIRONMENT, rejected);
        }
        if (fromProperty != null) {
            return new ResolvedTraceFile(fromProperty, ResolvedTraceFile.Source.PROPERTY, rejected);
        }
        if (marker != null && Files.exists(marker)) {
            return new ResolvedTraceFile(true, ResolvedTraceFile.Source.MARKER, rejected);
        }
        return new ResolvedTraceFile(environment.isDev(), ResolvedTraceFile.Source.DEFAULT, rejected);
    }

    private static @Nullable String trimmed(final @Nullable String value) {
        return value == null ? null : value.trim();
    }

    // Every value that named neither on nor off is kept for the warning, whichever switch decides.
    private static @Nullable String rejectedOf(
            final @Nullable String envValue,
            final @Nullable Boolean fromEnv,
            final @Nullable String propertyValue,
            final @Nullable Boolean fromProperty) {
        final String junkEnv = envValue != null && fromEnv == null ? envValue : null;
        final String junkProperty = propertyValue != null && fromProperty == null ? propertyValue : null;
        if (junkEnv != null && junkProperty != null) {
            return junkEnv + ", " + junkProperty;
        }
        return junkEnv != null ? junkEnv : junkProperty;
    }

    private static @Nullable Boolean parseSwitch(final String value) {
        final String normalized = value.toLowerCase(Locale.ROOT);
        if (ON.contains(normalized)) {
            return Boolean.TRUE;
        }
        return OFF.contains(normalized) ? Boolean.FALSE : null;
    }
}
