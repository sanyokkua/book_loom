package ua.bookloom.pipeline.glossary;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Whether a rendering starts the way the source name transliterates (15i.N6): word by word, the opening letters of the
 * source word map by the target language's letter table to the opening letters of the rendering's word. A table, not a
 * dictionary: it refuses {@code Карло} for {@code Arlo} and cannot judge the rest of a word.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TransliterationCheck {

    private static final Pattern WORD_BREAK = Pattern.compile("[\\s-]+");
    private static final int LONGEST_KEY = 2;

    /**
     * Checks a rendering against its source name.
     *
     * @param term the source name; never null
     * @param rendering the suggested rendering; never null
     * @param table the target language's letter table; never null, empty when the language has none
     * @return {@code true} if every word of the rendering opens as the matching source word transliterates, or if the
     *     table cannot judge a word; {@code false} if a word opens otherwise or the word counts differ
     */
    public static boolean holds(final String term, final String rendering, final Map<String, List<String>> table) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(rendering, "rendering");
        Objects.requireNonNull(table, "table");
        if (table.isEmpty()) {
            return true;
        }
        final List<String> source = wordsOf(term);
        final List<String> target = wordsOf(rendering);
        if (source.size() != target.size()) {
            log.debug("Letter table: the name has {} words, the rendering {}", source.size(), target.size());
            log.trace("Letter table: '{}' against '{}'", term, rendering);
            return false;
        }
        for (int index = 0; index < source.size(); index++) {
            if (!opens(source.get(index), target.get(index), table)) {
                log.debug("Letter table: word {} of the rendering does not open as the name's does", index + 1);
                log.trace("Letter table: '{}' does not open as '{}' does", rendering, term);
                return false;
            }
        }
        return true;
    }

    private static boolean opens(
            final String sourceWord, final String targetWord, final Map<String, List<String>> table) {
        final String source = sourceWord.toLowerCase(Locale.ROOT);
        for (int length = Math.min(LONGEST_KEY, source.length()); length >= 1; length--) {
            final List<String> allowed = table.get(source.substring(0, length));
            if (allowed != null) {
                return allowed.contains(targetWord
                        .substring(0, targetWord.offsetByCodePoints(0, 1))
                        .toLowerCase(Locale.ROOT));
            }
        }
        return true;
    }

    // The words of a name, split at spaces and hyphens, each without the quotes or marks before its first letter.
    private static List<String> wordsOf(final String name) {
        return WORD_BREAK
                .splitAsStream(name.strip())
                .map(TransliterationCheck::fromFirstLetter)
                .filter(word -> !word.isEmpty())
                .toList();
    }

    private static String fromFirstLetter(final String word) {
        int start = 0;
        while (start < word.length() && !Character.isLetterOrDigit(word.codePointAt(start))) {
            start += Character.charCount(word.codePointAt(start));
        }
        return word.substring(start);
    }
}
