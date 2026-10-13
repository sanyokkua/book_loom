package ua.bookloom.pipeline.prompt;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Which named calls ask the model to think. Published studies find thinking does not help translation itself, so
 * every call is off, as it always was, until {@code BOOKLOOM_REASONING} (else the {@code bookloom.reasoning} property)
 * names calls as {@code NAME=on} entries separated by commas (case-insensitive, {@code -} read as {@code _}; the last
 * entry for a name wins), so the eval can measure each call alone. The eval sets the property from
 * {@code BOOKLOOM_EVAL_REASONING}.
 *
 * @param enabled the calls that think
 */
@Slf4j
public record ReasoningPlan(Set<PromptName> enabled) {

    public static final String ENV_NAME = "BOOKLOOM_REASONING";
    public static final String PROPERTY_NAME = "bookloom.reasoning";

    private static final String ON = "on";
    private static final String OFF = "off";
    private static final String OFF_TOKEN = "off";

    // The batch draft is one call kind in three formats (15i.X1): naming DRAFT_BATCH_JSON turns thinking on in all
    // three.
    private static final Set<PromptName> BATCH_ARMS =
            EnumSet.of(PromptName.DRAFT_BATCH_MARKERS, PromptName.DRAFT_BATCH_DUAL);

    public ReasoningPlan {
        Objects.requireNonNull(enabled, "enabled");
        enabled = Collections.unmodifiableSet(
                enabled.isEmpty() ? EnumSet.noneOf(PromptName.class) : EnumSet.copyOf(enabled));
    }

    /**
     * The plan of this process, read from the real environment and system properties.
     *
     * @return the plan; every call off when nothing valid is set
     */
    public static ReasoningPlan current() {
        return resolve(System.getenv(), System.getProperty(PROPERTY_NAME));
    }

    /**
     * Resolves the plan from an environment and a property value.
     *
     * @param env the environment variables, whose value wins over the property as a whole; never null
     * @param property the {@code bookloom.reasoning} value, or null when unset
     * @return the plan; an entry that names no known call or whose value is neither {@code on} nor {@code off} is
     *     ignored with one warning, the others still apply
     */
    public static ReasoningPlan resolve(final Map<String, String> env, @Nullable final String property) {
        Objects.requireNonNull(env, "env");
        final String spec = env.getOrDefault(ENV_NAME, property == null ? "" : property);
        final Set<PromptName> on = EnumSet.noneOf(PromptName.class);
        if (!spec.isBlank()) {
            for (final String entry : spec.split(",", -1)) {
                apply(entry.strip(), on);
            }
        }
        log.debug("Reasoning plan resolved spec='{}' on={}", spec, on);
        return new ReasoningPlan(on);
    }

    private static void apply(final String entry, final Set<PromptName> on) {
        if (entry.isEmpty()) {
            return;
        }
        final int equals = entry.indexOf('=');
        final String name = equals < 0 ? "" : entry.substring(0, equals).strip();
        final String value =
                equals < 0 ? "" : entry.substring(equals + 1).strip().toLowerCase(Locale.ROOT);
        final PromptName call = promptNamed(name);
        if (call == null || !(ON.equals(value) || OFF.equals(value))) {
            log.warn("Ignoring reasoning entry '{}': it must be NAME=on or NAME=off for a known call", entry);
            return;
        }
        if (ON.equals(value)) {
            on.add(call);
        } else {
            on.remove(call);
        }
        log.debug("Reasoning entry call={} value={}", call, value);
    }

    private static @Nullable PromptName promptNamed(final String name) {
        try {
            return PromptName.valueOf(name.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /**
     * Whether a call thinks.
     *
     * @param name the call; never null
     * @return {@code true} if the plan turns thinking on for it; a batch format counts as {@code DRAFT_BATCH_JSON}
     */
    public boolean isOn(final PromptName name) {
        Objects.requireNonNull(name, "name");
        return enabled.contains(name) || (BATCH_ARMS.contains(name) && enabled.contains(PromptName.DRAFT_BATCH_JSON));
    }

    /**
     * The plan as a report states it.
     *
     * @return the calls that are on in declaration order as {@code NAME=on} entries joined by commas, or {@code off}
     */
    public String token() {
        if (enabled.isEmpty()) {
            return OFF_TOKEN;
        }
        return enabled.stream().map(call -> call.name() + "=" + ON).collect(Collectors.joining(","));
    }
}
