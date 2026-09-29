package ua.bookloom.pipeline;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
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
        return PATTERNS.computeIfAbsent(
                term,
                key -> Pattern.compile(NOT_AFTER_WORD_CHARACTER + Pattern.quote(key) + NOT_BEFORE_WORD_CHARACTER));
    }
}
