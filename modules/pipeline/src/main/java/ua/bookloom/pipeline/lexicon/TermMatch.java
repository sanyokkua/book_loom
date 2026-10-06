package ua.bookloom.pipeline.lexicon;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Finds a key term in source text: a whole word or phrase ignoring case, with an English plural or possessive tolerated
 * so {@code masters} and {@code master's} still name {@code master}. Words of a multi-word term may be separated by any
 * run of spaces.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TermMatch {

    private static final String NOT_AFTER_LETTER = "(?<![\\p{L}\\p{N}])";
    private static final String ENDING = "(?:['’ʼ]?s|es)?";
    private static final String NOT_BEFORE_LETTER = "(?![\\p{L}\\p{N}])";
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Map<String, Pattern> PATTERNS = new ConcurrentHashMap<>();

    /**
     * Whether a text names the term.
     *
     * @param term the non-blank key term
     * @param text the non-null text to search, tokens already removed
     * @return {@code true} if the term occurs as a whole word or phrase, {@code false} otherwise
     */
    public static boolean occursIn(final String term, final String text) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(text, "text");
        return !term.isBlank() && pattern(term).matcher(text).find();
    }

    /**
     * The words or phrases of a text that name the term, as written.
     *
     * @param term the non-blank key term
     * @param text the non-null text to search, tokens already removed
     * @return each match in text order, a plural or possessive ending included; never null, empty when none
     */
    public static List<String> matches(final String term, final String text) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(text, "text");
        return term.isBlank()
                ? List.of()
                : pattern(term).matcher(text).results().map(MatchResult::group).toList();
    }

    private static Pattern pattern(final String term) {
        return PATTERNS.computeIfAbsent(term.strip(), key -> {
            final String words = String.join(
                    "\\s+", SPACES.splitAsStream(key).map(Pattern::quote).toList());
            return Pattern.compile(
                    NOT_AFTER_LETTER + words + ENDING + NOT_BEFORE_LETTER,
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        });
    }
}
