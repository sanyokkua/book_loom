package ua.bookloom.pipeline.prompt;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The words of another language that a target language's file replaces, from its {@code foreignWords} key
 * ({@code тоже>теж}), so the foreign-word check is chosen by data and never by a hard-coded tag.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ForeignWords {

    private static final char SEPARATOR = '>';

    /**
     * The replacement list of a target language.
     *
     * @param targetTag the non-null target language tag
     * @return lower-case foreign word to its replacement; never null, empty when the language lists none
     */
    public static Map<String, String> of(final String targetTag) {
        final Map<String, String> words = new LinkedHashMap<>();
        for (final String entry :
                LanguageRules.bundled().wordsOf(Objects.requireNonNull(targetTag, "targetTag"), "foreignWords")) {
            final int at = entry.indexOf(SEPARATOR);
            if (at > 0 && at < entry.length() - 1) {
                words.put(entry.substring(0, at).toLowerCase(Locale.ROOT), entry.substring(at + 1));
            }
        }
        return Map.copyOf(words);
    }
}
