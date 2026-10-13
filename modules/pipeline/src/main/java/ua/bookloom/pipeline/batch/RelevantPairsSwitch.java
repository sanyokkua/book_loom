package ua.bookloom.pipeline.batch;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Whether a batch shows relevant earlier pairs. The block costs prompt tokens in every batch, so the eval flips it on
 * and off to decide whether it earns them. {@code BOOKLOOM_RELEVANT_PAIRS} (else the {@code bookloom.relevant.pairs}
 * property) names {@code on} or {@code off}; the eval sets the property from {@code BOOKLOOM_EVAL_RELEVANT_PAIRS}.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RelevantPairsSwitch {

    /** What a run uses when nothing is set, until the eval gate decides. */
    public static final boolean DEFAULT = true;

    static final String ENV_NAME = "BOOKLOOM_RELEVANT_PAIRS";
    static final String PROPERTY_NAME = "bookloom.relevant.pairs";

    private static final String ON = "on";
    private static final String OFF = "off";

    /**
     * Whether this process shows relevant pairs, read from the real environment and system properties.
     *
     * @return {@code true} if batches show them, {@code false} otherwise
     */
    public static boolean isOn() {
        return resolve(System.getenv(), System.getProperty(PROPERTY_NAME));
    }

    /**
     * Resolves the switch from an environment and a property value.
     *
     * @param env the environment variables, whose value wins over the property; never null
     * @param property the {@code bookloom.relevant.pairs} value, or null when unset
     * @return the named setting; {@link #DEFAULT} when neither names one or the name is neither {@code on} nor
     *     {@code off}
     */
    static boolean resolve(final Map<String, String> env, @Nullable final String property) {
        Objects.requireNonNull(env, "env");
        final String asked = env.getOrDefault(ENV_NAME, property == null ? "" : property)
                .strip()
                .toLowerCase(Locale.ROOT);
        return switch (asked) {
            case "" -> DEFAULT;
            case ON -> true;
            case OFF -> false;
            default -> {
                log.warn("Unknown relevant-pairs setting '{}'; using {}", asked, DEFAULT ? ON : OFF);
                yield DEFAULT;
            }
        };
    }
}
