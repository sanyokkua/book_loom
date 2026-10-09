package ua.bookloom.pipeline.prompt;

import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The endings that make a plural of a name, from a language file's {@code pluralSuffixes} key, so the name scan folds
 * {@code Chromes} into {@code Chrome} by data and never by a hard-coded tag.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PluralSuffixes {

    /**
     * The plural endings of a language.
     *
     * @param languageTag the non-null language tag
     * @return the endings, such as {@code s} and {@code es}; never null, empty when the language lists none
     */
    public static List<String> of(final String languageTag) {
        return LanguageRules.bundled().wordsOf(languageTag, "pluralSuffixes");
    }
}
