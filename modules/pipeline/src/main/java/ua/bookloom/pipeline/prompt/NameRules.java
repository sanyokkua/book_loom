package ua.bookloom.pipeline.prompt;

import java.util.Locale;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.NamePolicy;

/**
 * The rule the glossary's suggestion call states for the Book Brief's name policy, read from the language-rules map:
 * the policy lines and the neutral convention live in {@code generic.properties}, and a target language may state its
 * own spelling convention for foreign names under {@code names.convention} — for Ukrainian the orthography's
 * practical transcription, not the passport romanisation that goes the other way.
 */
@Slf4j
public final class NameRules {

    private static final String CONVENTION = "names.convention";
    private static final String TERMS = "names.terms";

    private static final class Holder {
        static final NameRules INSTANCE = new NameRules(LanguageRules.bundled());
    }

    private final LanguageRules rules;

    NameRules(final LanguageRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /** The bundled rules. */
    public static NameRules bundled() {
        return Holder.INSTANCE;
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
        final @Nullable String own = rules.targetValue(targetTag, CONVENTION);
        log.debug("Name rule policy={} target={} ownConvention={}", policy, targetTag, own != null);
        final String language = languageName(targetTag);
        final String convention = (own == null ? rules.genericValue(CONVENTION) : own).replace("{target}", language);
        final String rule = rules.genericValue("names.policy." + policy.name())
                .replace("{convention}", convention)
                .replace("{target}", language);
        return policy == NamePolicy.KEEP_ORIGINAL ? rule : rule + "\n" + rules.genericValue(TERMS);
    }

    private static String languageName(final String targetTag) {
        final String name = Locale.forLanguageTag(targetTag.strip()).getDisplayLanguage(Locale.ENGLISH);
        return name.isBlank() ? PromptLanguages.describe(targetTag) : name;
    }
}
