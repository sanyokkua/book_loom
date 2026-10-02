package ua.bookloom.pipeline.prompt;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.NamePolicy;

/**
 * The rule the glossary's suggestion call states for the Book Brief's name policy, read from
 * {@code name-rendering.properties}. The spelling convention for foreign names is a target language's own — for
 * Ukrainian the orthography's practical transcription, not the passport romanisation that goes the other way — so
 * each language may name its own, and every other language gets a neutral sentence. A missing key stops loading, as a
 * mistyped template slot does.
 */
@Slf4j
public final class NameRules {

    static final String FILE = "name-rendering.properties";
    private static final String DEFAULT_CONVENTION = "convention.default";
    private static final String TERMS = "terms";

    private static final class Holder {
        static final NameRules INSTANCE = load(() -> NameRules.class.getResourceAsStream(FILE));
    }

    private final Properties properties;

    private NameRules(final Properties properties) {
        this.properties = properties;
    }

    /** The bundled rules. */
    public static NameRules bundled() {
        return Holder.INSTANCE;
    }

    static NameRules load(final Supplier<@Nullable InputStream> source) {
        final Properties properties = new Properties();
        try (InputStream stream = source.get()) {
            if (stream == null) {
                throw new IllegalStateException(FILE + " is missing");
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } catch (IOException cause) {
            throw new IllegalStateException(FILE + " could not be read", cause);
        }
        Stream.concat(
                        Arrays.stream(NamePolicy.values()).map(NameRules::policyKey),
                        Stream.of(DEFAULT_CONVENTION, TERMS))
                .forEach(key -> {
                    if (properties.getProperty(key) == null) {
                        throw new IllegalStateException(FILE + " lacks key '" + key + "'");
                    }
                });
        return new NameRules(properties);
    }

    /**
     * The rule lines for one policy and target language.
     *
     * @param policy the non-null Book Brief name policy
     * @param targetTag the non-null target language tag
     * @return the lines the system prompt's {@code nameRule} slot shows; under {@link NamePolicy#KEEP_ORIGINAL} only
     *     terms are asked about, so no separate line for terms follows
     */
    public String rule(final NamePolicy policy, final String targetTag) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(targetTag, "targetTag");
        final String primary = Locale.forLanguageTag(targetTag.strip()).getLanguage();
        final String ownConvention = properties.getProperty("convention." + primary);
        log.debug("Name rule policy={} target={} ownConvention={}", policy, targetTag, ownConvention != null);
        final String language = languageName(targetTag);
        final String convention = (ownConvention == null ? properties.getProperty(DEFAULT_CONVENTION) : ownConvention)
                .replace("{target}", language);
        final String rule = properties
                .getProperty(policyKey(policy))
                .replace("{convention}", convention)
                .replace("{target}", language);
        return policy == NamePolicy.KEEP_ORIGINAL ? rule : rule + "\n" + properties.getProperty(TERMS);
    }

    private static String policyKey(final NamePolicy policy) {
        return "policy." + policy.name();
    }

    private static String languageName(final String targetTag) {
        final String name = Locale.forLanguageTag(targetTag.strip()).getDisplayLanguage(Locale.ENGLISH);
        return name.isBlank() ? PromptLanguages.describe(targetTag) : name;
    }
}
