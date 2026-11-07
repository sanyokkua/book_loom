package ua.bookloom.pipeline.prompt;

import java.util.Objects;

/**
 * The prompt alternatives a paired eval ("arm") compares. The production injector binds {@link #DEFAULTS}, which
 * reproduce the shipped prompts exactly; only a test injector binds another value, so no environment variable or
 * setting can steer a release's prompts. Each field lives only until the task that settles its arm deletes it.
 *
 * @param languageRules which language rules a prompt states
 * @param proofreadPrompt which instruction block 12 of a proofread call states (arm 7c)
 */
public record PromptVariants(LanguageRulesVariant languageRules, ProofreadPrompt proofreadPrompt) {

    /** The shipped prompts. */
    public static final PromptVariants DEFAULTS = new PromptVariants();

    /** Which language rules a prompt states. */
    public enum LanguageRulesVariant {
        /** The target's, the source's and the pair's own files, as shipped. */
        BUNDLED,
        /** The generic rules alone, which is how the eval measures what the language files add. */
        GENERIC_ONLY
    }

    /** Which instruction a proofread call's block 12 states (design D24, arm 7c). */
    public enum ProofreadPrompt {
        /** The target language's own instruction and rules, as shipped; the English default for a target with none. */
        P1,
        /** The English default instruction for every target, the target's rules kept. */
        P2
    }

    /** Rejects a missing choice. */
    public PromptVariants {
        Objects.requireNonNull(languageRules, "languageRules");
        Objects.requireNonNull(proofreadPrompt, "proofreadPrompt");
    }

    /**
     * The shipped prompts, for an injector that binds nothing (a test that builds services just in time), so such an
     * injector sends exactly what the production one does.
     */
    public PromptVariants() {
        this(LanguageRulesVariant.BUNDLED, ProofreadPrompt.P1);
    }

    /**
     * The shipped proofread prompt with the given language rules.
     *
     * @param languageRules the non-null language-rules choice
     */
    public PromptVariants(final LanguageRulesVariant languageRules) {
        this(languageRules, ProofreadPrompt.P1);
    }

    /** {@code true} when every field is the shipped value. */
    public boolean isDefault() {
        return equals(DEFAULTS);
    }

    /**
     * A copy with another proofread prompt.
     *
     * @param prompt the non-null proofread prompt
     * @return the variants; never null
     */
    public PromptVariants withProofreadPrompt(final ProofreadPrompt prompt) {
        return new PromptVariants(languageRules, prompt);
    }

    /** The language-rules map {@link #languageRules} selects; one shared instance per choice. */
    public LanguageRules rules() {
        return switch (languageRules) {
            case BUNDLED -> LanguageRules.bundled();
            case GENERIC_ONLY -> LanguageRules.genericRulesOnly();
        };
    }
}
