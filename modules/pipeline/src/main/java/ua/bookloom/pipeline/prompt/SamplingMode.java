package ua.bookloom.pipeline.prompt;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
     * The mode the environment or the property names, which then replaces the stored setting.
     *
     * @param env the environment variables; never null
     * @param property the {@code bookloom.sampling} value, or null when unset
     * @return the named mode, or empty when neither is set to a known name
     */
    static Optional<SamplingMode> explicit(final Map<String, String> env, @Nullable final String property) {
        return explicit(env, property, true);
    }

    private static Optional<SamplingMode> explicit(
            final Map<String, String> env, @Nullable final String property, final boolean warn) {
        Objects.requireNonNull(env, "env");
        final String asked = env.getOrDefault(ENV_NAME, property == null ? "" : property)
                .strip()
                .toLowerCase(Locale.ROOT);
        if (asked.isEmpty()) {
            return Optional.empty();
        }
        for (final SamplingMode mode : values()) {
            if (mode.token.equals(asked)) {
                return Optional.of(mode);
            }
        }
        if (warn) {
            log.warn("Unknown sampling mode '{}'; it is ignored", asked);
        }
        return Optional.empty();
    }

    /**
     * The variable or property that names a known mode, for Settings to say it decides.
     *
     * @param env the environment variables; never null
     * @param property the {@code bookloom.sampling} value, or null when unset
     * @return {@link #ENV_NAME} when the environment variable is present, else {@link #PROPERTY_NAME}; empty when
     *     neither names a known mode
     */
    static Optional<String> explicitSource(final Map<String, String> env, @Nullable final String property) {
        if (explicit(env, property, false).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(env.containsKey(ENV_NAME) ? ENV_NAME : PROPERTY_NAME);
    }

    /**
     * Resolves the mode from an environment and a property value.
     *
     * @param env the environment variables; never null
     * @param property the {@code bookloom.sampling} value, or null when unset
     * @return the named mode; {@link #DEFAULT} when neither names one or the name is unknown
     */
    static SamplingMode resolve(final Map<String, String> env, @Nullable final String property) {
        return explicit(env, property).orElse(DEFAULT);
    }
}
