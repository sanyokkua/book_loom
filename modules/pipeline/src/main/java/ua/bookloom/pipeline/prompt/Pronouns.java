package ua.bookloom.pipeline.prompt;

import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The third-person pronouns a language file lists, which show the gender of the character a name stands for: the
 * subject forms ({@code femalePronouns}, {@code malePronouns}) and, apart, the object and possessive forms
 * ({@code femaleObjectPronouns}, {@code maleObjectPronouns}), which often name somebody else and so are weaker evidence.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Pronouns {

    /**
     * The words that show a woman or a girl.
     *
     * @param languageTag the non-null language tag
     * @return the lower-case pronouns; never null, empty when the language lists none
     */
    public static Set<String> female(final String languageTag) {
        return Set.copyOf(LanguageRules.bundled().wordsOf(languageTag, "femalePronouns"));
    }

    /**
     * The words that show a man or a boy.
     *
     * @param languageTag the non-null language tag
     * @return the lower-case pronouns; never null, empty when the language lists none
     */
    public static Set<String> male(final String languageTag) {
        return Set.copyOf(LanguageRules.bundled().wordsOf(languageTag, "malePronouns"));
    }

    /**
     * The object and possessive forms that show a woman or a girl ({@code her}, {@code її}).
     *
     * @param languageTag the non-null language tag
     * @return the lower-case pronouns; never null, empty when the language lists none
     */
    public static Set<String> femaleObject(final String languageTag) {
        return Set.copyOf(LanguageRules.bundled().wordsOf(languageTag, "femaleObjectPronouns"));
    }

    /**
     * The object and possessive forms that show a man or a boy ({@code him}, {@code його}).
     *
     * @param languageTag the non-null language tag
     * @return the lower-case pronouns; never null, empty when the language lists none
     */
    public static Set<String> maleObject(final String languageTag) {
        return Set.copyOf(LanguageRules.bundled().wordsOf(languageTag, "maleObjectPronouns"));
    }
}
