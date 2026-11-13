package ua.bookloom.api.pipeline;

/**
 * What the pipeline knows about the languages it has translation rules for, so the Book Brief can say when a target
 * language is translated with the general rules only, and offer the choices the target language sets.
 */
@FunctionalInterface
public interface LanguageSupport {

    /**
     * Whether a language's rules have been tested against a corpus.
     *
     * @param languageTag the non-null BCP-47 tag; any tag is accepted, because languages are open
     * @return {@code true} if the language has its own tested rules, {@code false} if it has untested rules or none,
     *     in which case a prompt carries the general rules and translation still works
     */
    boolean hasTestedRules(String languageTag);

    /**
     * The apostrophe, quotation-mark, address and convention choices a target language's file sets.
     *
     * @param targetTag the non-null BCP-47 tag of the target language
     * @return never null; {@link BriefChoices#NONE} when the language sets none, which is also the answer of a support
     *     that reads no language file
     */
    default BriefChoices briefChoices(final String targetTag) {
        return BriefChoices.NONE;
    }
}
