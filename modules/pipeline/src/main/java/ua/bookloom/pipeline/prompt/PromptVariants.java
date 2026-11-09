package ua.bookloom.pipeline.prompt;

import java.util.Objects;

/**
 * The prompt alternatives a paired eval ("arm") compares. The production injector binds {@link #DEFAULTS}, which
 * reproduce the shipped prompts exactly; only a test injector binds another value, so no environment variable or
 * setting can steer a release's prompts. Each field lives only until the task that settles its arm deletes it; the
 * {@code languageRules} field lives only until task 14.7 deletes the record.
 *
 * @param languageRules which language rules a prompt states
 */
public record PromptVariants(LanguageRulesVariant languageRules) {

    /** The shipped prompts. */
    public static final PromptVariants DEFAULTS = new PromptVariants();

    /** Which language rules a prompt states. */
    public enum LanguageRulesVariant {
        /** The target's, the source's and the pair's own files, as shipped. */
        BUNDLED,
        /** The generic rules alone, which is how the eval measures what the language files add. */
        GENERIC_ONLY
    }

    /** Rejects a missing choice. */
    public PromptVariants {
        Objects.requireNonNull(languageRules, "languageRules");
    }

    /**
     * A copy with another language-rules choice.
     *
     * @param value the non-null choice
     * @return the copy
     */
    public PromptVariants withLanguageRules(final LanguageRulesVariant value) {
        return new PromptVariants(value);
    }

    /**
     * The shipped prompts, for an injector that binds nothing (a test that builds services just in time), so such an
     * injector sends exactly what the production one does.
     */
    public PromptVariants() {
        this(LanguageRulesVariant.BUNDLED);
    }

    /** {@code true} when every field is the shipped value. */
    public boolean isDefault() {
        return equals(DEFAULTS);
    }

    /** The language-rules map {@link #languageRules} selects; one shared instance per choice. */
    public LanguageRules rules() {
        return switch (languageRules) {
            case BUNDLED -> LanguageRules.bundled();
            case GENERIC_ONLY -> LanguageRules.genericRulesOnly();
        };
    }
}
