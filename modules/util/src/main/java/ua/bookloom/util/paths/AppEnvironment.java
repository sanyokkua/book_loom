package ua.bookloom.util.paths;

import java.util.Objects;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Whether this process is a development run or a packaged production run.
 *
 * <p>The distinction exists for one reason: a development run must never read or write the production database or
 * logs. Because the whole folder differs rather than a file within it, a dev instance and a production instance also
 * hold <em>different</em> single-instance lock files and can therefore run at the same time.
 *
 * <p><strong>The default is {@link #DEV}, and that is the safety property rather than a fallback.</strong> An
 * un-stamped run — an IDE launch, {@code ./gradlew :app:run}, a test — has no way to prove it is production, so it
 * stays out of the production folder. The failure mode of the opposite default is silent and destructive: a debug
 * session writing to the user's real book database.
 */
public enum AppEnvironment {

    /** An un-stamped run: IDE, Gradle, or tests. Resolves to the {@code -Dev}/{@code -dev} sibling folder. */
    DEV,

    /** A packaged run, stamped by the jpackage launcher. Resolves to the production folder. */
    PROD;

    /** Env var used by QA to force either environment; highest precedence. */
    private static final String ENV_OVERRIDE = "BOOKLOOM_ENV";

    /** System property the packaging scripts inject via {@code --java-options}. */
    private static final String BUILD_STAMP = "bookloom.env";

    /** Undocumented but reliable property that jpackage's launcher sets; a fallback signal only. */
    private static final String JPACKAGE_MARKER = "jpackage.app-path";

    private static final String DEV_TOKEN = "dev";
    private static final String PROD_TOKEN = "prod";

    /**
     * Decides the environment from the injected environment and system properties.
     *
     * <p>First match wins, in the order the specification fixes: the {@code BOOKLOOM_ENV} override, then the
     * {@code -Dbookloom.env=prod} build stamp, then the presence of jpackage's launcher property, then dev. An
     * unrecognised {@code BOOKLOOM_ENV} value is not an error and is not production — it falls through to the
     * remaining signals, and ultimately to dev.
     *
     * <p>Pure: it reads nothing but the two functions handed to it, which is what makes both environments testable
     * without mutating the real process environment.
     *
     * @param getEnv reads an environment variable, returning {@code null} when unset
     * @param getProperty reads a system property, returning {@code null} when unset
     * @return the resolved environment
     */
    public static AppEnvironment resolve(
            Function<String, @Nullable String> getEnv, Function<String, @Nullable String> getProperty) {
        Objects.requireNonNull(getEnv, "getEnv");
        Objects.requireNonNull(getProperty, "getProperty");

        final String override = trimmedOrNull(getEnv.apply(ENV_OVERRIDE));
        if (override != null) {
            if (PROD_TOKEN.equalsIgnoreCase(override)) {
                return PROD;
            }
            if (DEV_TOKEN.equalsIgnoreCase(override)) {
                return DEV;
            }
        }

        if (PROD_TOKEN.equalsIgnoreCase(trimmedOrNull(getProperty.apply(BUILD_STAMP)))) {
            return PROD;
        }

        return trimmedOrNull(getProperty.apply(JPACKAGE_MARKER)) != null ? PROD : DEV;
    }

    /**
     * Whether this is a development run.
     *
     * @return {@code true} for {@link #DEV}
     */
    public boolean isDev() {
        return this == DEV;
    }

    private static @Nullable String trimmedOrNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
