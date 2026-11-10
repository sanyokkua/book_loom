package ua.bookloom.pipeline.lexicon;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Finds a key term in a text. {@link #occursIn} and {@link #matches} are for reading what a run proved (a hyphen
 * separates words, so a stuttered {@code Z-Zerubin} still names {@code Zerubin}): a whole word or phrase in any
 * case, an English plural or possessive tolerated. {@link #isNamedIn} is for choosing what a prompt lists, and is
 * stricter: a word joined to another by a hyphen is one word, so {@code Mura} is not found inside {@code Kito-Mura};
 * a term that opens with a capital is found only with a capital (a name is not the common word it spells); and a term
 * that ends in a non-Latin letter keeps its stem and tolerates a short ending, because declension changes the end of a
 * word there. A term of fewer than three letters tolerates no ending ({@code Mr} is not {@code Mrs}). Words of a
 * multi-word term may be separated by any run of spaces.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TermMatch {

    private static final String HYPHENS = "-\u2010\u2011";
    private static final String NOT_AFTER_LETTER = "(?<![\\p{L}\\p{N}])";
    private static final String NOT_AFTER_JOINED = "(?<![\\p{L}\\p{N}][" + HYPHENS + "])";
    private static final String ENDING = "(?:['\u2019\u02bc]?s|es)?";
    private static final String NOT_BEFORE_LETTER = "(?![\\p{L}\\p{N}])";
    private static final String NOT_BEFORE_JOINED = "(?![" + HYPHENS + "][\\p{L}\\p{N}])";
    private static final int MIN_STEM = 3;
    private static final int MAX_ENDING = 3;
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern LATIN_END = Pattern.compile("\\p{IsLatin}[\\p{M}\\p{Punct}]*$");
    private static final Map<String, Pattern> LOOSE = new ConcurrentHashMap<>();
    private static final Map<String, Pattern> STRICT = new ConcurrentHashMap<>();

    /**
     * Whether a text names the term, a hyphen separating words.
     *
     * @param term the non-blank key term
     * @param text the non-null text to search, tokens already removed
     * @return {@code true} if the term occurs as a whole word or phrase, {@code false} otherwise
     */
    public static boolean occursIn(final String term, final String text) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(text, "text");
        return !term.isBlank() && loose(term).matcher(text).find();
    }

    /**
     * Whether a text names any form of a word, as {@link #occursIn(String, String)} reads one form.
     *
     * @param forms the forms of one word (its term and its other stored forms); never null, blank ones ignored
     * @param text the non-null text to search, tokens already removed
     * @return {@code true} if any form occurs as a whole word or phrase, {@code false} otherwise
     */
    public static boolean occursIn(final Collection<String> forms, final String text) {
        Objects.requireNonNull(forms, "forms");
        Objects.requireNonNull(text, "text");
        return forms.stream().anyMatch(form -> occursIn(form, text));
    }

    /**
     * The words or phrases of a text that name any form of a word, as written.
     *
     * @param forms the forms of one word; never null, blank ones ignored
     * @param text the non-null text to search, tokens already removed
     * @return each match in text order; never null, empty when none
     */
    public static List<String> matches(final Collection<String> forms, final String text) {
        Objects.requireNonNull(forms, "forms");
        Objects.requireNonNull(text, "text");
        return forms.stream()
                .filter(form -> !form.isBlank())
                .flatMap(form -> loose(form).matcher(text).results())
                .map(MatchResult::start)
                .distinct()
                .sorted()
                .map(start -> wordAt(forms, text, start))
                .toList();
    }

    // The longest form's match at a start, so a match found by two forms is returned once.
    private static String wordAt(final Collection<String> forms, final String text, final int start) {
        return forms.stream()
                .filter(form -> !form.isBlank())
                .map(form -> loose(form).matcher(text))
                .filter(matcher -> matcher.find(start) && matcher.start() == start)
                .map(MatchResult::group)
                .max(Comparator.comparingInt(String::length))
                .orElseThrow();
    }

    /**
     * Whether a text names the term as a prompt should read it: hyphen-joined words are one word, a capitalised term
     * needs its capital, and a non-Latin term tolerates a declined ending.
     *
     * @param term the key term; a blank term is named nowhere
     * @param text the non-null text to search, tokens already removed
     * @return {@code true} if the text names the term, {@code false} otherwise
     */
    public static boolean isNamedIn(final String term, final String text) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(text, "text");
        return !term.isBlank() && strict(term).matcher(text).find();
    }

    /**
     * Where a text first names the term, read as {@link #isNamedIn} reads it, so entries can be listed in the order a
     * passage names them.
     *
     * @param term the key term; a blank term is named nowhere
     * @param text the non-null text to search, tokens already removed
     * @return the index of the first match, or -1 when the text does not name the term
     */
    public static int namedAt(final String term, final String text) {
        Objects.requireNonNull(term, "term");
        Objects.requireNonNull(text, "text");
        if (term.isBlank()) {
            return -1;
        }
        final Matcher matcher = strict(term).matcher(text);
        return matcher.find() ? matcher.start() : -1;
    }

    /**
     * How many places of a text name any of the terms, read as {@link #isNamedIn} reads one, so the window budgets
     * can drop the entry a passage names least.
     *
     * @param terms the non-null forms of one entry; a blank one is named nowhere
     * @param text the non-null text to search, tokens already removed
     * @return the number of distinct places named, each counted once however many forms match there
     */
    public static int namedCount(final Collection<String> terms, final String text) {
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(text, "text");
        return (int) terms.stream()
                .filter(term -> !term.isBlank())
                .flatMap(term -> strict(term).matcher(text).results())
                .map(MatchResult::start)
                .distinct()
                .count();
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
                : loose(term).matcher(text).results().map(MatchResult::group).toList();
    }

    private static Pattern loose(final String term) {
        return LOOSE.computeIfAbsent(term.strip(), key -> {
            final String words = String.join(
                    "\\s+", SPACES.splitAsStream(key).map(Pattern::quote).toList());
            return Pattern.compile(
                    NOT_AFTER_LETTER + words + endingFor(key) + NOT_BEFORE_LETTER,
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        });
    }

    private static Pattern strict(final String term) {
        return STRICT.computeIfAbsent(
                term.strip(),
                key -> Pattern.compile(
                        NOT_AFTER_LETTER + NOT_AFTER_JOINED + body(key) + NOT_BEFORE_LETTER + NOT_BEFORE_JOINED,
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
    }

    private static String body(final String key) {
        final boolean latin = LATIN_END.matcher(key).find();
        final List<String> words =
                SPACES.splitAsStream(latin ? key : stemOf(key)).toList();
        final String first =
                Character.isUpperCase(key.codePointAt(0)) ? capitalised(words.get(0)) : quoted(words.get(0));
        final String rest =
                words.stream().skip(1).map(word -> "\\s+" + quoted(word)).collect(Collectors.joining());
        return first + rest + (latin ? endingFor(key) : "\\p{L}{0," + MAX_ENDING + "}");
    }

    // A term shorter than MIN_STEM letters takes no ending: "Mr" must not be found inside "Mrs".
    private static String endingFor(final String key) {
        return key.codePointCount(0, key.length()) < MIN_STEM ? "" : ENDING;
    }

    private static String quoted(final String word) {
        return word.isEmpty() ? "" : Pattern.quote(word);
    }

    private static String capitalised(final String word) {
        final int split = word.offsetByCodePoints(0, 1);
        return "(?-i:" + Pattern.quote(word.substring(0, split)) + ")" + quoted(word.substring(split));
    }

    /** A long enough non-Latin term loses its last letter, so a declined form keeps matching. */
    private static String stemOf(final String key) {
        return key.codePointCount(0, key.length()) > MIN_STEM ? key.substring(0, key.length() - 1) : key;
    }
}
