package ua.bookloom.pipeline.prompt;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Whether a run sends its own sampling controls or leaves them to the server. Servers disagree on their defaults,
 * and a default that penalises repeats works against copied quote marks and names, so the choice is measured
 * (prompt eval) before it becomes the default. {@code BOOKLOOM_SAMPLING} (else the {@code bookloom.sampling}
 * property) sets it; the eval sets it from {@code BOOKLOOM_EVAL_SAMPLING}.
 */
@Slf4j
public enum SamplingMode {

    /** No sampling control beyond temperature is sent; the server's own defaults apply. */
    SERVER("server"),

    /** Each call kind sends its {@link SamplingProfile}. */
    TUNED("tuned");

    /** What a run uses when nothing is set. */
    public static final SamplingMode DEFAULT = SERVER;

    static final String ENV_NAME = "BOOKLOOM_SAMPLING";
    static final String PROPERTY_NAME = "bookloom.sampling";

    private final String token;

    SamplingMode(final String token) {
        this.token = token;
    }

    /** The spelling the environment variable and the property use. */
    public String token() {
        return token;
    }

    /** The mode of this process, read from the real environment and system properties. */
    public static SamplingMode current() {
        return resolve(System.getenv(), System.getProperty(PROPERTY_NAME));
    }

    /**
     * Resolves the mode from an environment and a property value.
     *
     * @param env the environment variables; never null
     * @param property the {@code bookloom.sampling} value, or null when unset
     * @return the named mode; {@link #DEFAULT} when neither names one or the name is unknown
     */
    static SamplingMode resolve(final Map<String, String> env, @Nullable final String property) {
        Objects.requireNonNull(env, "env");
        final String asked = env.getOrDefault(ENV_NAME, property == null ? "" : property)
                .strip()
                .toLowerCase(Locale.ROOT);
        if (asked.isEmpty()) {
            return DEFAULT;
        }
        for (final SamplingMode mode : values()) {
            if (mode.token.equals(asked)) {
                return mode;
            }
        }
        log.warn("Unknown sampling mode '{}'; using {}", asked, DEFAULT.token);
        return DEFAULT;
    }
}
