package ua.bookloom.pipeline.checks;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.prompt.LanguageRules;

/** How many negation words a text holds, by the language file's {@code negationWords} list (design D12). */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NegationCount {

    /**
     * Counts the negation words of a text.
     *
     * @param text the non-null display text
     * @param languageTag the non-null language tag whose {@code negationWords} are counted
     * @return the number of whole words, compared in lower case, that the language lists as negations; zero when it
     *     lists none
     */
    public static int of(final String text, final String languageTag) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(languageTag, "languageTag");
        final Set<String> negations = LanguageRules.bundled().negationWords(languageTag);
        if (negations.isEmpty()) {
            return 0;
        }
        return (int) Words.WORD
                .matcher(text)
                .results()
                .filter(word -> negations.contains(word.group().toLowerCase(Locale.ROOT)))
                .count();
    }
}
