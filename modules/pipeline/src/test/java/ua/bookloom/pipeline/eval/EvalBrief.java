package ua.bookloom.pipeline.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.QualityDial;
import ua.bookloom.api.project.BookBrief;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.api.project.Register;

/**
 * The Book Brief an eval request is sized for. A real run's brief is the person's choices; an eval used to send the
 * defaults, so what it measured was not what a run sends. The brief now starts from the defaults and takes, in this
 * order and later winning: a named preset ({@code BOOKLOOM_EVAL_PRESET}), the owner's gold for the book
 * ({@code BOOKLOOM_EVAL_GOLD}, a {@link JudgementGold} file: its brief's register, names, genre, voice, audience and
 * narrator), a JSON file ({@code BOOKLOOM_EVAL_BRIEF} with any of {@code register}, {@code names}, {@code genre},
 * {@code voiceEra}, {@code audience}, {@code dial}, {@code balance}) and the single settings
 * {@code BOOKLOOM_EVAL_REGISTER}, {@code _NAMES} ({@code translate}, {@code transliterate} or {@code keep}),
 * {@code _GENRE} and {@code _DIAL}.
 *
 * <p>The {@code burning-chrome} preset is one model's frozen answer (the e4b run's brief, from its trace log), kept so
 * that earlier reports can be measured again; the gold is what a reader of the book chose, and is what a new
 * measurement should be sized for.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class EvalBrief {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String PREFIX = "BOOKLOOM_EVAL_";
    private static final String KEEP = "keep";

    /** The brief of the Burning Chrome run the owner reviewed (the trace log's session line and style sheet). */
    private static final Map<String, String> BURNING_CHROME = Map.of(
            "register", "FORMAL_LITERARY",
            "names", "TRANSLATE",
            "dial", "BALANCED",
            "genre", "cyberpunk novel",
            "voiceEra", "intense, technical jargon mixed with visceral description",
            "audience", "readers interested in cyberpunk themes");

    private static final Map<String, Map<String, String>> PRESETS = Map.of("burning-chrome", BURNING_CHROME);

    /** The brief for the languages, from the settings an environment (or a test's map) holds. */
    static BookBrief of(final String source, final String target, final Map<String, String> env) {
        Objects.requireNonNull(env, "env");
        final JudgementGold gold = gold(env);
        final Map<String, String> settings = settings(env, gold);
        log.debug("Eval brief source={} target={} settings={} gold={}", source, target, settings, gold != null);
        final BookBrief base = BookBrief.defaults(source).withLanguages(source, target);
        final BookBrief brief = new BookBrief(
                source,
                target,
                settings.getOrDefault("genre", base.genre()),
                register(settings.get("register"), base.register()),
                settings.getOrDefault("voiceEra", base.voiceEra()),
                settings.getOrDefault("audience", base.audience()),
                names(settings.get("names"), base.names()),
                base.foreignPassages(),
                base.footnotes(),
                base.units(),
                settings.containsKey("balance") ? Integer.parseInt(settings.get("balance")) : base.balance(),
                base.alsoTranslate(),
                dial(settings.get("dial"), base.dial()));
        return gold == null ? brief : brief.withNarrator(gold.brief().narratorOf());
    }

    private static @Nullable JudgementGold gold(final Map<String, String> env) {
        final String file = env.get(PREFIX + "GOLD");
        return file == null || file.isBlank() ? null : JudgementGold.read(Path.of(file.strip()));
    }

    private static Map<String, String> settings(final Map<String, String> env, @Nullable final JudgementGold gold) {
        final Map<String, String> settings = new LinkedHashMap<>();
        final String preset = env.get(PREFIX + "PRESET");
        if (preset != null && !preset.isBlank()) {
            settings.putAll(preset(preset.strip()));
        }
        if (gold != null) {
            settings.putAll(gold.brief().settings());
        }
        final String file = env.get(PREFIX + "BRIEF");
        if (file != null && !file.isBlank()) {
            settings.putAll(fileSettings(Path.of(file.strip())));
        }
        for (final String key : new String[] {"register", "names", "genre", "dial"}) {
            final String value = env.get(PREFIX + key.toUpperCase(Locale.ROOT));
            if (value != null && !value.isBlank()) {
                settings.put(key, value.strip());
            }
        }
        return settings;
    }

    private static Map<String, String> preset(final String name) {
        final Map<String, String> preset = PRESETS.get(name);
        if (preset == null) {
            throw new IllegalArgumentException(PREFIX + "PRESET must be one of " + PRESETS.keySet() + ", not " + name);
        }
        return preset;
    }

    private static Map<String, String> fileSettings(final Path file) {
        try {
            return MAPPER.readValue(Files.readString(file), new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + PREFIX + "BRIEF " + file, e);
        }
    }

    private static Register register(final String asked, final Register fallback) {
        return asked == null ? fallback : Register.valueOf(constant(asked));
    }

    private static NamePolicy names(final String asked, final NamePolicy fallback) {
        if (asked == null) {
            return fallback;
        }
        return KEEP.equalsIgnoreCase(asked.strip()) ? NamePolicy.KEEP_ORIGINAL : NamePolicy.valueOf(constant(asked));
    }

    private static QualityDial dial(final String asked, final QualityDial fallback) {
        return asked == null ? fallback : QualityDial.valueOf(constant(asked));
    }

    private static String constant(final String asked) {
        return asked.strip().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
