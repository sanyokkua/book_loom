package ua.bookloom.pipeline.context;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import ua.bookloom.pipeline.glossary.StopWords;
import ua.bookloom.pipeline.prompt.LanguageRules;

/**
 * What keeps noise out of the recurring-term lines a prompt shows: a term that is a function word of the source
 * language is no key term, and a one-word rendering with a case ending of the target language is a declined form the
 * model happened to write, not the word to keep. Without it the lexicon would teach the model to use {@code програму}
 * for every {@code program}.
 *
 * @param functionWords the lower-cased source-language function words; never null
 * @param obliqueEndings the endings of the target language's oblique case forms; never null, empty for no preference
 */
public record LexiconFilter(Set<String> functionWords, List<String> obliqueEndings) {

    /** The filter that admits everything, for a caller that knows no languages. */
    public static final LexiconFilter NONE = new LexiconFilter(Set.of(), List.of());

    /** Copies both collections. */
    public LexiconFilter {
        functionWords = Set.copyOf(Objects.requireNonNull(functionWords, "functionWords"));
        obliqueEndings = List.copyOf(Objects.requireNonNull(obliqueEndings, "obliqueEndings"));
    }

    /**
     * The filter of a language pair.
     *
     * @param sourceLanguage the source language tag, or null when unknown
     * @param targetLanguage the target language tag, or null when unknown
     * @return the filter reading the bundled lists of the two languages
     */
    public static LexiconFilter of(@Nullable final String sourceLanguage, @Nullable final String targetLanguage) {
        return new LexiconFilter(
                StopWords.of(sourceLanguage),
                targetLanguage == null ? List.of() : LanguageRules.bundled().obliqueEndings(targetLanguage));
    }

    /**
     * Whether a term and its established rendering may be shown.
     *
     * @param term the non-null source term
     * @param rendering the non-null rendering the entry has established
     * @return {@code true} if the line is worth a model's attention, {@code false} for a function word or a declined form
     */
    public boolean admits(final String term, final String rendering) {
        final String key = term.strip().toLowerCase(Locale.ROOT);
        if (functionWords.contains(key)) {
            return false;
        }
        final String word = rendering.strip().toLowerCase(Locale.ROOT);
        final boolean isPlural = key.endsWith("s");
        return isPlural
                || word.chars().anyMatch(Character::isWhitespace)
                || obliqueEndings.stream().noneMatch(word::endsWith);
    }
}
