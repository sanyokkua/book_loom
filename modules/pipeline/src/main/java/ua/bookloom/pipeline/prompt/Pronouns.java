package ua.bookloom.pipeline.prompt;

import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The third-person subject pronouns a language file lists ({@code femalePronouns}, {@code malePronouns}), which show
 * the gender of the character a name stands for. Object forms are left out of the files because they usually name
 * somebody else.
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
}
