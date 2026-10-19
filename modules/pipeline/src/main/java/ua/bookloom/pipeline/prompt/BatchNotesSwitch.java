package ua.bookloom.pipeline.prompt;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Whether a batch draft is asked for a one-line note before each target (hard phrases and idioms, written before the
 * translation). It costs output tokens in every batch and is only an experiment until the eval shows it moves
 * quality, so it is off. {@code BOOKLOOM_BATCH_NOTES} (else the {@code bookloom.batch.notes} property) names
 * {@code on} or {@code off}; the eval sets the property from {@code BOOKLOOM_EVAL_BATCH_NOTES}.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BatchNotesSwitch {

    /** What a run uses when nothing is set, until the eval gate decides. */
    public static final boolean DEFAULT = false;

    public static final String ENV_NAME = "BOOKLOOM_BATCH_NOTES";
    public static final String PROPERTY_NAME = "bookloom.batch.notes";

    private static final String ON = "on";
    private static final String OFF = "off";

    /**
     * Whether this process asks for notes, read from the real environment and system properties.
     *
     * @return {@code true} if batches ask for them, {@code false} otherwise
     */
    public static boolean isOn() {
        return resolve(System.getenv(), System.getProperty(PROPERTY_NAME));
    }

    /**
     * Resolves the switch from an environment and a property value.
     *
     * @param env the environment variables, whose value wins over the property; never null
     * @param property the {@code bookloom.batch.notes} value, or null when unset
     * @return the named setting; {@link #DEFAULT} when neither names one or the name is neither {@code on} nor
     *     {@code off}
     */
    public static boolean resolve(final Map<String, String> env, @Nullable final String property) {
        Objects.requireNonNull(env, "env");
        final String asked = env.getOrDefault(ENV_NAME, property == null ? "" : property)
                .strip()
                .toLowerCase(Locale.ROOT);
        return switch (asked) {
            case "" -> DEFAULT;
            case ON -> true;
            case OFF -> false;
            default -> {
                log.warn("Unknown batch-notes setting '{}'; using {}", asked, DEFAULT ? ON : OFF);
                yield DEFAULT;
            }
        };
    }
}
