package ua.bookloom.app.env;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/** The operating system's own store of a user's environment variables, asked one name at a time. */
public interface OsEnvironment {

    /**
     * Reads one variable.
     *
     * @param name a variable name already checked to be letters, digits and underscores
     * @return the value if the store holds one, or empty if it does not; a failing read throws an unchecked exception,
     *     which the caller turns into "not set"
     */
    Optional<String> read(String name);

    /**
     * Names this store in the log.
     *
     * @return a short fixed word, never a value
     */
    String source();

    /**
     * Chooses the store of the system BookLoom runs on.
     *
     * @param osName the {@code os.name} property; an unknown one is treated as Linux
     * @param processEnv the process environment, for {@code XDG_CONFIG_HOME}
     * @param getProperty the system properties, for {@code user.home}
     * @param runner runs the macOS and Windows reader commands
     * @return the store for that system
     */
    static OsEnvironment forOs(
            final String osName,
            final Function<String, @Nullable String> processEnv,
            final Function<String, @Nullable String> getProperty,
            final CommandRunner runner) {
        Objects.requireNonNull(osName, "osName");
        Objects.requireNonNull(processEnv, "processEnv");
        Objects.requireNonNull(getProperty, "getProperty");
        Objects.requireNonNull(runner, "runner");
        final String os = osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac")) {
            return new LaunchctlEnvironment(runner);
        }
        if (os.startsWith("windows")) {
            return new WindowsRegistryEnvironment(runner);
        }
        final String xdg = processEnv.apply("XDG_CONFIG_HOME");
        final String home = Objects.requireNonNullElse(getProperty.apply("user.home"), "");
        final Path config = xdg != null && xdg.startsWith("/") ? Path.of(xdg) : Path.of(home, ".config");
        return new EnvironmentFiles(config.resolve("environment.d"), Path.of("/etc/environment"));
    }
}
