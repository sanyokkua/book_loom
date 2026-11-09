package ua.bookloom.pipeline.qa;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The capital-letter counts behind the {@code sentence-case} and {@code mid-capital} vetoes of {@link NeverWorse}.
 * A capital in the middle of a sentence is a name, and a name is the glossary's business, so the second count is
 * bounded by the source's own.
 */
// Checkstyle's HideUtilityClassConstructor parses source text before Lombok's annotation processor runs (ADR-0024).
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CaseVetoes {

    private static final Pattern WORD = Pattern.compile("\\p{L}[\\p{L}\\p{M}'’ʼ-]*");

    static long lowerStarts(final String text) {
        return SentenceStarts.of(text).stream()
                .filter(start -> Character.isLowerCase(text.codePointAt(start)))
                .count();
    }

    static long midCapitals(final String text) {
        final Set<Integer> sentenceStarts = new HashSet<>(SentenceStarts.of(text));
        return WORD.matcher(text)
                .results()
                .filter(word -> !sentenceStarts.contains(word.start()))
                .filter(word -> Character.isUpperCase(text.codePointAt(word.start())))
                .count();
    }
}
