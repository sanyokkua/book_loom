package ua.bookloom.document.inspect;

import com.ibm.icu.text.BreakIterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ua.bookloom.api.document.Segment;
import ua.bookloom.util.lang.LanguageTags;

/**
 * Counts the words in a book's body segments (task 4.5), the way the statistics/import cards report them: ICU's
 * word {@link BreakIterator}, over the segment's masked text with every {@code ⟦gN⟧} placeholder removed first, so
 * a protected inline tag or a locked term contributes no word of its own.
 *
 * <p>ICU rather than a whitespace split because a `BreakIterator` segments correctly for scripts that do not use
 * spaces between words (design.md D11); the book's own normalized declared language picks which language's rules
 * apply, falling back to English when it declares none.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs,
// so it cannot see the private constructor @NoArgsConstructor generates below; suppressed per the escape
// hatch checkstyle.xml documents for exactly this case (java-coding-style.md, ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WordCounter {

    private static final Pattern PLACEHOLDER_TOKEN = Pattern.compile("⟦g\\d+⟧");
    private static final String DEFAULT_LANGUAGE_TAG = "en";

    /**
     * Counts the words across {@code segments}' masked text.
     *
     * @param segments the body segments to count, in any order; never null
     * @param declaredLanguage the book's own declared language, or {@code null} when it declares none
     * @return the total word count; never negative
     */
    public static int count(List<Segment> segments, @Nullable String declaredLanguage) {
        Objects.requireNonNull(segments, "segments");
        final Locale locale = localeOf(declaredLanguage);
        final BreakIterator iterator = BreakIterator.getWordInstance(locale);
        int total = 0;
        for (final Segment segment : segments) {
            total += countWords(iterator, stripPlaceholders(segment.masked()));
        }
        return total;
    }

    private static Locale localeOf(@Nullable String declaredLanguage) {
        final String tag = LanguageTags.normalize(declaredLanguage).orElse(DEFAULT_LANGUAGE_TAG);
        return Locale.forLanguageTag(tag);
    }

    private static String stripPlaceholders(String masked) {
        return PLACEHOLDER_TOKEN.matcher(masked).replaceAll("");
    }

    private static int countWords(BreakIterator iterator, String text) {
        iterator.setText(text);
        int count = 0;
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            if (holdsLetterOrDigit(text, start, end)) {
                count++;
            }
        }
        return count;
    }

    private static boolean holdsLetterOrDigit(String text, int start, int end) {
        for (int i = start; i < end; i++) {
            if (Character.isLetterOrDigit(text.codePointAt(i))) {
                return true;
            }
        }
        return false;
    }
}
