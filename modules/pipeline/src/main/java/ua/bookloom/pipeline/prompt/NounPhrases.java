package ua.bookloom.pipeline.prompt;

import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The small words of a language's noun phrases that tell a common noun from a name (15i.N5, 15i.N8), from the
 * language file's {@code determiners}, {@code indefiniteArticles} and {@code nameJoiners} keys: a person's name is
 * rarely written after an article, an adjective of nationality is written after "a" before a noun, and "of" joins two
 * names into one ("Tower of Arlen"). A language that lists none gets none of these rules.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NounPhrases {

    /**
     * The words that open a noun phrase: articles, quantifiers, demonstratives and possessives.
     *
     * @param languageTag the non-null language tag
     * @return the lower-case words; never null, empty when the language lists none
     */
    public static Set<String> determiners(final String languageTag) {
        return Set.copyOf(LanguageRules.bundled().wordsOf(languageTag, "determiners"));
    }

    /**
     * The determiners that introduce one thing of a kind ({@code a}, {@code every}), so a capitalised word between one
     * of them and a noun is an adjective.
     *
     * @param languageTag the non-null language tag
     * @return the lower-case words; never null, empty when the language lists none
     */
    public static Set<String> indefinite(final String languageTag) {
        return Set.copyOf(LanguageRules.bundled().wordsOf(languageTag, "indefiniteArticles"));
    }

    /**
     * The words that join two capitalised runs into one name.
     *
     * @param languageTag the non-null language tag
     * @return the lower-case words; never null, empty when the language lists none
     */
    public static Set<String> joiners(final String languageTag) {
        return Set.copyOf(LanguageRules.bundled().wordsOf(languageTag, "nameJoiners"));
    }
}
