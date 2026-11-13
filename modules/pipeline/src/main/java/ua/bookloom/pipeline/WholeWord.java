package ua.bookloom.pipeline;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The one whole-word matcher: a term matches only where no letter or digit touches it on either side, and case
 * counts. Not {@code \b}: since JDK 19 that boundary is ASCII-only, so it never matches around a Cyrillic, Greek or
 * Han name.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WholeWord {

    private static final String NOT_AFTER_WORD_CHARACTER = "(?<![\\p{L}\\p{N}])";
    private static final String NOT_BEFORE_WORD_CHARACTER = "(?![\\p{L}\\p{N}])";
    /**
     * How many compiled patterns are kept. A book's glossary has a few hundred terms, but the cache is shared by every
     * run of the session and is also asked for restored spans, so without a bound it would grow with every book read.
     */
    static final int MAX_PATTERNS = 4_096;

    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    /**
     * The compiled pattern for one term; one instance per distinct term, because a book's terms repeat on every
     * segment.
     *
     * @param term the literal text to find; never null, not interpreted as a regular expression
     * @return the pattern matching {@code term} as a whole word
     */
    public static Pattern pattern(final String term) {
        Objects.requireNonNull(term, "term");
        if (PATTERNS.size() >= MAX_PATTERNS && !PATTERNS.containsKey(term)) {
            // Starting over is cheap: the terms in use are compiled again on their next use.
            PATTERNS.clear();
        }
        return PATTERNS.computeIfAbsent(
                term,
                key -> Pattern.compile(NOT_AFTER_WORD_CHARACTER + Pattern.quote(key) + NOT_BEFORE_WORD_CHARACTER));
    }

    /**
     * One pattern matching any of several terms as a whole word, the longest first where two start at one place, so
     * each match is one of the terms and a replacement made from the matches never touches its own output. Not cached:
     * a set of terms is matched once.
     *
     * @param terms the non-empty literal texts to find; none interpreted as a regular expression
     * @return the pattern; its whole match is one of {@code terms}
     */
    public static Pattern anyOf(final Collection<String> terms) {
        Objects.requireNonNull(terms, "terms");
        if (terms.isEmpty()) {
            throw new IllegalArgumentException("terms must not be empty");
        }
        final String alternatives = terms.stream()
                .sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        return Pattern.compile(NOT_AFTER_WORD_CHARACTER + "(?:" + alternatives + ")" + NOT_BEFORE_WORD_CHARACTER);
    }

    /** How many compiled patterns the cache holds now; never more than {@link #MAX_PATTERNS}. */
    static int cached() {
        return PATTERNS.size();
    }
}
