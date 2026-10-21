package ua.bookloom.pipeline.prompt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.llm.ReasoningLevel;

/**
 * Which named calls ask the model to think. Published studies find thinking does not help translation itself, so
 * every call is off, as it always was, until {@code BOOKLOOM_REASONING} (else the {@code bookloom.reasoning} property)
 * names calls as {@code NAME=on} entries separated by commas (case-insensitive, {@code -} read as {@code _}; the last
 * entry for a name wins), so the eval can measure each call alone. The eval sets the property from
 * {@code BOOKLOOM_EVAL_REASONING}.
 *
 * @param levels the level each named call asks for
 * @param base the level every call that is not named asks for
 */
@Slf4j
public record ReasoningPlan(Map<PromptName, ReasoningLevel> levels, ReasoningLevel base) {

    public static final String ENV_NAME = "BOOKLOOM_REASONING";
    public static final String PROPERTY_NAME = "bookloom.reasoning";

    private static final String ON = "on";
    private static final String OFF = "off";
    private static final String LOW = "low";

    // The batch draft is one call kind in three formats (15i.X1): naming DRAFT_BATCH_JSON turns thinking on in all
    // three.
    private static final Set<PromptName> BATCH_ARMS =
            EnumSet.of(PromptName.DRAFT_BATCH_MARKERS, PromptName.DRAFT_BATCH_DUAL);

    public ReasoningPlan {
        Objects.requireNonNull(levels, "levels");
        Objects.requireNonNull(base, "base");
        levels =
                Collections.unmodifiableMap(levels.isEmpty() ? new EnumMap<>(PromptName.class) : new EnumMap<>(levels));
    }

    /**
     * A plan that turns the named calls on and leaves every other call off.
     *
     * @param enabled the calls that think; never null
     */
    public ReasoningPlan(final Set<PromptName> enabled) {
        this(onlyOn(enabled), ReasoningLevel.OFF);
    }

    private static Map<PromptName, ReasoningLevel> onlyOn(final Set<PromptName> enabled) {
        Objects.requireNonNull(enabled, "enabled");
        final Map<PromptName, ReasoningLevel> levels = new EnumMap<>(PromptName.class);
        enabled.forEach(call -> levels.put(call, ReasoningLevel.ON));
        return levels;
    }

    /**
     * The calls whose level is {@code ON}.
     *
     * @return the calls that think fully; never null
     */
    public Set<PromptName> enabled() {
        final Set<PromptName> on = EnumSet.noneOf(PromptName.class);
        levels.forEach((call, level) -> {
            if (level == ReasoningLevel.ON) {
                on.add(call);
            }
        });
        return on;
    }

    /**
     * Whether the environment variable or the property names a plan, which then replaces the stored setting.
     *
     * @param env the environment variables; never null
     * @param property the {@code bookloom.reasoning} value, or null when unset
     * @return {@code true} when either is set and not blank
     */
    public static boolean isSet(final Map<String, String> env, @Nullable final String property) {
        Objects.requireNonNull(env, "env");
        return !env.getOrDefault(ENV_NAME, property == null ? "" : property).isBlank();
    }

    /**
     * The plan of the stored Generation setting: every call at one level.
     *
     * @param base the stored level; never null
     * @return the plan with no named call
     */
    public static ReasoningPlan stored(final ReasoningLevel base) {
        return new ReasoningPlan(new EnumMap<>(PromptName.class), base);
    }

    /**
     * The level a call asks for.
     *
     * @param name the call; never null
     * @return the call's named level, else the base; a batch format counts as {@code DRAFT_BATCH_JSON}
     */
    public ReasoningLevel levelFor(final PromptName name) {
        Objects.requireNonNull(name, "name");
        final ReasoningLevel named = levels.get(name);
        if (named != null) {
            return named;
        }
        if (BATCH_ARMS.contains(name) && levels.containsKey(PromptName.DRAFT_BATCH_JSON)) {
            return levels.get(PromptName.DRAFT_BATCH_JSON);
        }
        return base;
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
     * @return the plan; a bare {@code off}, {@code low} or {@code on} sets the level of every call that is not named,
     *     a {@code NAME=on|off|low} entry sets one call. An entry that fits neither is ignored with one warning, the
     *     others still apply
     */
    public static ReasoningPlan resolve(final Map<String, String> env, @Nullable final String property) {
        Objects.requireNonNull(env, "env");
        final String spec = env.getOrDefault(ENV_NAME, property == null ? "" : property);
        final Map<PromptName, ReasoningLevel> levels = new EnumMap<>(PromptName.class);
        final ReasoningLevel[] base = {ReasoningLevel.OFF};
        if (!spec.isBlank()) {
            for (final String entry : spec.split(",", -1)) {
                apply(entry.strip(), levels, base);
            }
        }
        levels.values().removeIf(level -> level == base[0]);
        log.debug("Reasoning plan resolved spec='{}' base={} named={}", spec, base[0], levels);
        return new ReasoningPlan(levels, base[0]);
    }

    private static void apply(
            final String entry, final Map<PromptName, ReasoningLevel> levels, final ReasoningLevel[] base) {
        if (entry.isEmpty()) {
            return;
        }
        final int equals = entry.indexOf('=');
        if (equals < 0) {
            final ReasoningLevel bare = levelOf(entry);
            if (bare == null) {
                warnIgnored(entry);
            } else {
                base[0] = bare;
            }
            return;
        }
        final PromptName call = promptNamed(entry.substring(0, equals).strip());
        final ReasoningLevel level = levelOf(entry.substring(equals + 1));
        if (call == null || level == null) {
            warnIgnored(entry);
            return;
        }
        levels.put(call, level);
        log.debug("Reasoning entry call={} level={}", call, level);
    }

    private static void warnIgnored(final String entry) {
        log.warn("Ignoring reasoning entry '{}': it must be NAME=on|off|low for a known call, or off|low|on", entry);
    }

    private static @Nullable ReasoningLevel levelOf(final String word) {
        return switch (word.strip().toLowerCase(Locale.ROOT)) {
            case ON -> ReasoningLevel.ON;
            case OFF -> ReasoningLevel.OFF;
            case LOW -> ReasoningLevel.LOW;
            default -> null;
        };
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
        return levelFor(name) != ReasoningLevel.OFF;
    }

    /**
     * The plan as a report states it.
     *
     * @return the base level when it is not off, then the named calls whose level differs from the base in declaration
     *     order as {@code NAME=level} entries, joined by commas; {@code off} when nothing remains
     */
    public String token() {
        final List<String> parts = new ArrayList<>();
        if (base != ReasoningLevel.OFF) {
            parts.add(wordOf(base));
        }
        for (final PromptName call : PromptName.values()) {
            final ReasoningLevel level = levels.get(call);
            if (level != null && level != base) {
                parts.add(call.name() + "=" + wordOf(level));
            }
        }
        return parts.isEmpty() ? OFF : String.join(",", parts);
    }

    private static String wordOf(final ReasoningLevel level) {
        return level.name().toLowerCase(Locale.ROOT);
    }
}
