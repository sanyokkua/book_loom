package ua.bookloom.document.epub;

import java.util.Arrays;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

/**
 * Derives a creator's new sort key from the translated name, so a translated book still shelves under the author's
 * surname instead of losing the key. The direction is the source's own: a key written {@code Last, First} gives a
 * {@code Last, First} key, and a key with no comma is not a surname-first key, so nothing is derived from it.
 */
@Slf4j
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FileAsDerivation {

    private static final Set<Character.UnicodeScript> ALPHABETIC_SCRIPTS = Set.of(
            Character.UnicodeScript.LATIN,
            Character.UnicodeScript.CYRILLIC,
            Character.UnicodeScript.GREEK,
            Character.UnicodeScript.ARMENIAN,
            Character.UnicodeScript.GEORGIAN);
    private static final int MIN_WORDS = 2;

    /**
     * Builds the new key.
     *
     * @param sourceKey the key the source carried for the creator, for example {@code Gibson, William}; never null
     * @param translatedName the creator's translated text; never null
     * @return the {@code Last, First} key of the translated name, or null when the source key was not surname-first,
     *     the name has fewer than two words, or its letters are not in an alphabetic script
     */
    static @Nullable String derive(final String sourceKey, final String translatedName) {
        final String[] words = translatedName.trim().split("\\s+");
        if (!sourceKey.contains(",") || words.length < MIN_WORDS || !isAlphabetic(translatedName)) {
            log.debug("no file-as derived: sourceKey surnameFirst={} words={}", sourceKey.contains(","), words.length);
            return null;
        }
        final String last = words[words.length - 1];
        final String first = String.join(" ", Arrays.copyOf(words, words.length - 1));
        log.debug("file-as derived from the translated author: {}, {}", last, first);
        return last + ", " + first;
    }

    private static boolean isAlphabetic(final String name) {
        return name.codePoints()
                .filter(Character::isLetter)
                .allMatch(cp -> ALPHABETIC_SCRIPTS.contains(Character.UnicodeScript.of(cp)));
    }
}
