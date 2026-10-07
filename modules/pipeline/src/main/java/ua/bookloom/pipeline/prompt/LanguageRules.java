package ua.bookloom.pipeline.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.pipeline.LanguageSupport;

/**
 * The language-rules map: what a prompt tells a model about one language pair, read from the bundled
 * {@code languages/<tag>.properties}, {@code languages/pairs/<source>-<target>.properties} and
 * {@code languages/generic.properties}. A prompt gets one section built from the target language's rules, a few notes
 * on reading the source language and the pair's own rules; only those files are opened, so no other language's rules
 * can reach a prompt. A tag with no file gets the generic rules, because languages are open: any tag the JDK can name
 * must keep working.
 */
@Slf4j
public final class LanguageRules implements LanguageSupport {

    static final String DIRECTORY = "languages/";
    static final String PAIR_DIRECTORY = DIRECTORY + "pairs/";
    private static final String SUFFIX = ".properties";
    private static final String GENERIC = "generic";
    private static final String STATUS = "status";
    private static final String TESTED = "tested";
    private static final String PITFALLS = "pitfalls";
    private static final String EXAMPLE = "example";
    private static final String BATCH_EXAMPLE = "batchExample";
    private static final String NAME_EXAMPLE = "nameExample";
    private static final String REVIEWER_CHECKS = "reviewerChecks";
    private static final int MAX_SOURCE_NOTES = 3;
    private static final List<String> GENERIC_REQUIRED = List.of(
            STATUS,
            "quotes",
            "agreement",
            PITFALLS + ".1",
            EXAMPLE + ".1",
            NAME_EXAMPLE,
            "names.policy.TRANSLATE",
            "names.policy.TRANSLITERATE",
            "names.policy.KEEP_ORIGINAL",
            "names.terms",
            "names.convention");

    private static final class Holder {
        static final LanguageRules NORMAL = new LanguageRules(LanguageRules::openBundled, false);
        static final LanguageRules GENERIC_ONLY = new LanguageRules(LanguageRules::openBundled, true);
    }

    private final PromptTemplates.ResourceLoader loader;
    private final boolean genericOnly;
    private final LanguageFile generic;
    private final Map<String, LanguageFile> files = new ConcurrentHashMap<>();
    private final Map<String, String> sections = new ConcurrentHashMap<>();
    private final Map<String, String> examples = new ConcurrentHashMap<>();

    /**
     * Opens the map over a loader.
     *
     * @param loader opens a file by its name beside the templates
     * @param genericOnly {@code true} to build every section from the generic rules alone, which is how the eval
     *     measures what the language files add
     */
    LanguageRules(final PromptTemplates.ResourceLoader loader, final boolean genericOnly) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.genericOnly = genericOnly;
        this.generic = load(GENERIC, DIRECTORY + GENERIC + SUFFIX);
        for (final String key : GENERIC_REQUIRED) {
            if (generic.get(key) == null) {
                throw new IllegalStateException(DIRECTORY + GENERIC + SUFFIX + " lacks key '" + key + "'");
            }
        }
    }

    /** The bundled map. */
    public static LanguageRules bundled() {
        return Holder.NORMAL;
    }

    /**
     * The bundled map, or the one that states only the generic rules when the eval asks for it with the system
     * property {@code bookloom.eval.rules} or the environment variable {@code BOOKLOOM_EVAL_RULES} set to
     * {@code generic}.
     */
    static LanguageRules forEnvironment() {
        final String asked = System.getProperty("bookloom.eval.rules", System.getenv("BOOKLOOM_EVAL_RULES"));
        if ("generic".equalsIgnoreCase(asked)) {
            log.info("Language rules forced to the generic rules by the eval switch");
            return Holder.GENERIC_ONLY;
        }
        return Holder.NORMAL;
    }

    /** A language is tested when it has its own file marked {@code status=tested}; any other tag is not. */
    @Override
    public boolean hasTestedRules(final String tag) {
        Objects.requireNonNull(tag, "tag");
        final LanguageFile file = languageFile(tag);
        final boolean tested = file.exists() && TESTED.equals(file.get(STATUS));
        log.debug("Language {} tested={}", tag, tested);
        return tested;
    }

    /**
     * The one section a prompt carries for a language pair.
     *
     * @param sourceTag the source language tag, or null when it is inferred from the text, which leaves out the
     *     source notes and the pair's rules
     * @param targetTag the non-null target language tag
     * @param reviewing {@code true} when the prompt reviews a translation, which adds the languages' reviewer checks
     * @return the section starting {@code [Language rules: <Source> -> <Target>]}; never null, built once per pair
     */
    public String section(@Nullable final String sourceTag, final String targetTag, final boolean reviewing) {
        Objects.requireNonNull(targetTag, "targetTag");
        final String key = primary(sourceTag) + ">" + primary(targetTag) + (reviewing ? "!" : "");
        return sections.computeIfAbsent(key, ignored -> build(sourceTag, targetTag, reviewing));
    }

    private String build(@Nullable final String sourceTag, final String targetTag, final boolean reviewing) {
        final List<String> parts = new ArrayList<>();
        parts.add(targetRules(targetTag, reviewing));
        if (sourceTag != null && !genericOnly) {
            parts.add(sourceRules(sourceTag));
            parts.add(pairRules(sourceTag, targetTag, reviewing));
        }
        final String body =
                String.join("\n", parts.stream().filter(part -> !part.isEmpty()).toList());
        final String heading = "[Language rules: " + (sourceTag == null ? "the source" : name(sourceTag)) + " -> "
                + name(targetTag) + "]";
        log.debug(
                "Language rules source={} target={} reviewing={} genericOnly={} lines={}",
                sourceTag,
                targetTag,
                reviewing,
                genericOnly,
                body.lines().count());
        log.trace("Language rules section {}", body);
        return body.isEmpty() ? "" : heading + "\n" + body;
    }

    /** The target language's own rules, or the generic ones when it has no file. */
    String targetRules(final String targetTag, final boolean reviewing) {
        final LanguageFile file = genericOnly ? generic : languageFile(targetTag);
        final LanguageFile used = file.exists() ? file : generic;
        return block("Target — " + name(targetTag), used, reviewing);
    }

    /** What to read carefully when the language is the source; empty when it has no file. */
    String sourceRules(final String sourceTag) {
        final LanguageFile file = languageFile(sourceTag);
        final List<String> notes = file.indexed("sourceNotes");
        final List<String> lines = notes.subList(0, Math.min(MAX_SOURCE_NOTES, notes.size()));
        return lines.isEmpty() ? "" : "Source — " + name(sourceTag) + "\n" + bullets(lines);
    }

    /** The rules that exist only for the pair; empty when it has no file. */
    String pairRules(final String sourceTag, final String targetTag, final boolean reviewing) {
        final LanguageFile file = pairFile(sourceTag, targetTag);
        return block("Pair — " + name(sourceTag) + " -> " + name(targetTag), file, reviewing);
    }

    private static String block(final String header, final LanguageFile file, final boolean reviewing) {
        final List<String> lines = new ArrayList<>();
        for (final RuleKey rule : RuleKey.values()) {
            final String value = file.get(rule.key());
            if (value != null) {
                lines.add(rule.label() + ": " + value);
            }
        }
        file.indexed(PITFALLS).forEach(pitfall -> lines.add("Watch: " + pitfall));
        if (reviewing) {
            file.indexed(REVIEWER_CHECKS).forEach(check -> lines.add("Check: " + check));
        }
        return lines.isEmpty() ? "" : header + "\n" + bullets(lines);
    }

    private static String bullets(final List<String> lines) {
        return String.join("\n", lines.stream().map(line -> "- " + line).toList());
    }

    /**
     * The few-shot examples for a language pair: the pair's, else the target's, else the generic ones.
     *
     * @param sourceTag the source language tag, or null when it is inferred from the text, which skips the pair
     * @param targetTag the non-null target language tag
     * @return the examples as the prompt shows them; never blank
     */
    String examples(@Nullable final String sourceTag, final String targetTag) {
        return pick(sourceTag, targetTag, EXAMPLE, file -> String.join("\n\n", file.indexed(EXAMPLE)));
    }

    /**
     * The batch draft's examples, chosen like {@link #examples}: protocol-neutral lines {@code source ⇒ target}, one
     * example per blank-line block, which {@link BatchExamples} renders for a protocol.
     */
    String batchExamples(@Nullable final String sourceTag, final String targetTag) {
        return pick(sourceTag, targetTag, BATCH_EXAMPLE, file -> String.join("\n\n", file.indexed(BATCH_EXAMPLE)));
    }

    /** The suggestion call's example, chosen like {@link #examples}. */
    String nameExamples(@Nullable final String sourceTag, final String targetTag) {
        return pick(sourceTag, targetTag, NAME_EXAMPLE, file -> {
            final String text = file.get(NAME_EXAMPLE);
            return text == null ? "" : text;
        });
    }

    private String pick(
            @Nullable final String sourceTag,
            final String targetTag,
            final String kind,
            final Function<LanguageFile, String> read) {
        final String key = kind + "|" + primary(sourceTag) + ">" + primary(targetTag);
        return examples.computeIfAbsent(key, ignored -> {
            final List<LanguageFile> order = new ArrayList<>();
            if (sourceTag != null && !primary(sourceTag).isEmpty()) {
                order.add(pairFile(sourceTag, targetTag));
            }
            order.add(languageFile(targetTag));
            order.add(generic);
            for (final LanguageFile file : order) {
                final String text = read.apply(file);
                if (!text.isBlank()) {
                    log.debug("Chose {} from {} for {} -> {}", kind, file.name(), sourceTag, targetTag);
                    return text;
                }
            }
            throw new IllegalStateException("The generic " + kind + " is missing");
        });
    }

    /**
     * A name-rendering line the generic file holds.
     *
     * @param key the non-null key, such as {@code names.terms}
     * @return the text; never null
     */
    String genericValue(final String key) {
        return Objects.requireNonNull(generic.get(key), key);
    }

    /** A value of the target language's own file, or null when it has no file or no such key. */
    @Nullable
    String targetValue(final String targetTag, final String key) {
        return languageFile(targetTag).get(key);
    }

    /**
     * The gender check a target language's file names, so the check is chosen by data and never by a hard-coded tag.
     *
     * @param targetTag the non-null target language tag
     * @return the value of the file's {@code genderCheck} key, or null when the language has no file or names none
     */
    public @Nullable String genderCheck(final String targetTag) {
        return targetValue(Objects.requireNonNull(targetTag, "targetTag"), "genderCheck");
    }

    /**
     * The groups of letters a name's stem may swap its last letter within before a suffix, from the target language's
     * {@code stemAlternations} key, so the name check never hard-codes a language.
     *
     * @param targetTag the non-null target language tag
     * @return the groups, such as {@code гзж}; never null, empty when the language names none
     */
    public List<String> stemAlternations(final String targetTag) {
        return wordsOf(targetTag, "stemAlternations");
    }

    /**
     * The word endings of a target language's oblique case forms, from its {@code obliqueEndings} key, so the lexicon
     * can prefer the dictionary form of a learned rendering by data and never by a hard-coded tag.
     *
     * @param targetTag the non-null target language tag
     * @return the endings, such as {@code і} and {@code ів}; never null, empty when the language lists none
     */
    public List<String> obliqueEndings(final String targetTag) {
        return wordsOf(targetTag, "obliqueEndings");
    }

    /**
     * The letters a target language's alphabet does not have, from its {@code forbiddenLetters} key, so the alphabet
     * check is chosen by data and never by a hard-coded tag.
     *
     * @param targetTag the non-null target language tag
     * @return the lower-case letters, such as {@code ыъэё}; never null, empty when the language lists none
     */
    public String forbiddenLetters(final String targetTag) {
        final String value = targetValue(Objects.requireNonNull(targetTag, "targetTag"), "forbiddenLetters");
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    /**
     * The voiced and voiceless consonant pairs of a target language, from its {@code voicingPairs} key, so the audit can
     * tell two spellings of one name (Майлз, Майлс) apart from two names without hard-coding a language.
     *
     * @param targetTag the non-null target language tag
     * @return pairs of two letters each, such as {@code зс}; never null, empty when the language lists none
     */
    public List<String> voicingPairs(final String targetTag) {
        return wordsOf(targetTag, "voicingPairs");
    }

    /**
     * The particles and negations of a target language that carry mood or polarity, from its {@code functionWords} key,
     * so a reviewer edit that only fixes agreement can be held to leaving them alone without hard-coding a language.
     *
     * @param targetTag the non-null target language tag
     * @return the lower-case words; never null, empty when the language lists none
     */
    public Set<String> functionWords(final String targetTag) {
        return Set.copyOf(wordsOf(targetTag, "functionWords"));
    }

    /**
     * The words that are a first-person subject in a language's narration, from its {@code firstPersonPronouns} key,
     * so the narrator detector is chosen by data and never by a hard-coded tag.
     *
     * @param languageTag the non-null language tag
     * @return the pronouns exactly as written (capitalised forms listed too); never null, empty when the language lists
     *     none, which is how a language that drops its subject says detection cannot work
     */
    public Set<String> firstPersonPronouns(final String languageTag) {
        return Set.copyOf(wordsOf(languageTag, "firstPersonPronouns"));
    }

    // The whitespace-separated words of a language file's key, as written; empty when the language lacks the key.
    private List<String> wordsOf(final String tag, final String key) {
        final String value = targetValue(Objects.requireNonNull(tag, "tag"), key);
        return value == null || value.isBlank() ? List.of() : List.of(value.split("\\s+"));
    }

    private LanguageFile languageFile(@Nullable final String tag) {
        final String primary = primary(tag);
        return primary.isEmpty() ? new LanguageFile("", Map.of()) : fileOf(primary, DIRECTORY + primary + SUFFIX);
    }

    private LanguageFile pairFile(final String sourceTag, final String targetTag) {
        final String source = primary(sourceTag);
        final String target = primary(targetTag);
        if (source.isEmpty() || target.isEmpty()) {
            return new LanguageFile("", Map.of());
        }
        return fileOf(source + "-" + target, PAIR_DIRECTORY + source + "-" + target + SUFFIX);
    }

    private LanguageFile fileOf(final String name, final String resource) {
        return files.computeIfAbsent(resource, ignored -> load(name, resource));
    }

    private LanguageFile load(final String name, final String resource) {
        final Properties properties = new Properties();
        try (InputStream stream = loader.open(resource)) {
            if (stream == null) {
                log.debug("No language file {}", resource);
                return new LanguageFile(name, Map.of());
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException cause) {
            throw new IllegalStateException(resource + " could not be read", cause);
        }
        final Map<String, String> values = new HashMap<>();
        properties.forEach(
                (key, value) -> values.put(key.toString(), value.toString().strip()));
        log.debug("Loaded language file {} with {} keys", resource, values.size());
        return new LanguageFile(name, values);
    }

    private static @Nullable InputStream openBundled(final String fileName) {
        return LanguageRules.class.getResourceAsStream(fileName);
    }

    private static String name(final String tag) {
        final String name = Locale.forLanguageTag(tag.strip()).getDisplayLanguage(Locale.ENGLISH);
        return name.isBlank() ? PromptLanguages.describe(tag) : name;
    }

    private static String primary(@Nullable final String tag) {
        return tag == null ? "" : Locale.forLanguageTag(tag.strip()).getLanguage();
    }
}
