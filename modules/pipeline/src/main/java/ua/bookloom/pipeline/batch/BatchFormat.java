package ua.bookloom.pipeline.batch;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.prompt.PromptName;

/**
 * How a batch is sent and answered, the three arms of the 15i.X1 experiment: JSON items as today, one marked passage
 * with a plain-text reply split by code, or the whole passage as context beside the numbered items with a JSON reply.
 * {@code BOOKLOOM_BATCH_FORMAT} (else the {@code bookloom.batch.format} property) names {@code json}, {@code markers}
 * or {@code dual}; the eval sets the property from {@code BOOKLOOM_EVAL_BATCH_FORMAT}.
 */
@Slf4j
public enum BatchFormat {
    /** Numbered items in, a JSON object with an entry per id out, under the response schema. */
    JSON(PromptName.DRAFT_BATCH_JSON),
    /** One passage whose items start at {@code ⟦sN⟧} markers in, the same markers in plain text out; no schema. */
    MARKERS(PromptName.DRAFT_BATCH_MARKERS),
    /** The whole passage as context beside the numbered items in, a JSON object out, under the same schema. */
    DUAL(PromptName.DRAFT_BATCH_DUAL);

    /** What a run uses when nothing is set. */
    public static final BatchFormat DEFAULT = JSON;

    /** The environment variable that names the format; it wins over the property. */
    public static final String ENV_NAME = "BOOKLOOM_BATCH_FORMAT";

    /** The system property that names the format. */
    public static final String PROPERTY_NAME = "bookloom.batch.format";

    private final PromptName promptName;

    BatchFormat(final PromptName promptName) {
        this.promptName = promptName;
    }

    /** The templates, response format and call label this format's batches are built from. */
    public PromptName promptName() {
        return promptName;
    }

    /** The format this process uses, read from the real environment and system properties. */
    public static BatchFormat current() {
        final BatchFormat format = resolve(System.getenv(), System.getProperty(PROPERTY_NAME));
        log.debug("Batch format resolved format={}", format);
        return format;
    }

    /**
     * Resolves the format from an environment and a property value.
     *
     * @param env the environment variables, whose value wins over the property; never null
     * @param property the {@code bookloom.batch.format} value, or null when unset
     * @return the named format; {@link #DEFAULT} when neither names one or the name is unknown
     */
    static BatchFormat resolve(final Map<String, String> env, @Nullable final String property) {
        Objects.requireNonNull(env, "env");
        final String fromEnv = env.get(ENV_NAME);
        final String name = fromEnv == null || fromEnv.isBlank() ? property : fromEnv;
        if (name == null || name.isBlank()) {
            return DEFAULT;
        }
        return named(name.strip()).orElseGet(() -> {
            log.warn("Unknown batch format name={}; using {}", name.strip(), DEFAULT);
            return DEFAULT;
        });
    }

    private static Optional<BatchFormat> named(final String name) {
        final String upper = name.toUpperCase(Locale.ROOT);
        for (final BatchFormat format : values()) {
            if (format.name().equals(upper)) {
                return Optional.of(format);
            }
        }
        return Optional.empty();
    }
}
