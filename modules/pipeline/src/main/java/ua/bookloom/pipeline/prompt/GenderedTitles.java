package ua.bookloom.pipeline.prompt;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.project.Gender;
import ua.bookloom.util.lang.Language;
import ua.bookloom.util.lang.Languages;

/**
 * The forms of address that show the gender of the person they address ({@code Mrs}, {@code Sir}), from a language
 * file's {@code femaleTitles} and {@code maleTitles} keys, so the name clusters never join {@code Mrs Venn} to a man by
 * data and never by a hard-coded tag. A title a language file does not list (Doctor, Captain) shows no gender.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GenderedTitles {

    private static final String FEMALE_KEY = "femaleTitles";
    private static final String MALE_KEY = "maleTitles";

    /**
     * The gender a form of address shows.
     *
     * @param title the non-null title in any case, with or without a closing full stop
     * @param languageTag the book's language tag, or null when it is not known, which reads every bundled language
     * @return {@link Gender#FEMALE} or {@link Gender#MALE} for a listed title, or empty when the title shows none
     */
    public static Optional<Gender> genderOf(final String title, @Nullable final String languageTag) {
        final String word = title.strip().replaceAll("\\.$", "").toLowerCase(Locale.ROOT);
        if (words(languageTag, FEMALE_KEY).contains(word)) {
            return Optional.of(Gender.FEMALE);
        }
        return words(languageTag, MALE_KEY).contains(word) ? Optional.of(Gender.MALE) : Optional.empty();
    }

    private static Set<String> words(@Nullable final String languageTag, final String key) {
        final LanguageRules rules = LanguageRules.bundled();
        if (languageTag != null && !languageTag.isBlank()) {
            return Set.copyOf(rules.wordsOf(languageTag, key));
        }
        final Set<String> every = new HashSet<>();
        for (final Language language : Languages.all()) {
            every.addAll(rules.wordsOf(language.tag(), key));
        }
        return every;
    }
}
