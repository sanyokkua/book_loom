package ua.bookloom.util.text;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * What a name scan must never propose, and how likely an entry already in the glossary is to be one of those. A real
 * run on a published novel proposed {@code T-shirt}, {@code Yellow Pages}, {@code CROYDON} and a publisher's name:
 * the scan counts capitalised words, and the book's printing notes, an everyday compound with a capital initial and
 * a directory's name all look like names to it. The rules are shape and vocabulary only, so they need no model.
 *
 * <p>The vocabulary is English because the printing notes of the books measured are; the shape rules hold in every
 * language with capitals.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JunkRules {

    /** A score at or above this is never proposed by a scan. */
    public static final int DROP_SCORE = 2;

    /** A score at or above this is shown to the person as likely junk. */
    public static final int MARK_SCORE = 1;

    private static final int STRONG = 2;
    private static final int WEAK = 1;
    private static final int SHORT_WORD_LETTERS = 2;

    /** The longest line still read as a printing note; a paragraph of story that mentions one is not dropped. */
    private static final int MAX_NOTE_WORDS = 25;

    /** The last word of a publisher, an imprint or a directory, which no character or place is called. */
    private static final Set<String> NOTE_LAST_WORDS = Set.of(
            "books",
            "press",
            "publishers",
            "publishing",
            "pages",
            "magazine",
            "inc",
            "ltd",
            "llc",
            "edition",
            "copyright",
            "isbn");

    /** Whole terms that are a publisher, a directory or printing text, whatever their words. */
    private static final Set<String> NOTE_TERMS = Set.of(
            "random house",
            "penguin",
            "harpercollins",
            "simon schuster",
            "new york times",
            "all rights reserved",
            "library of congress");

    private static final Pattern PUNCTUATION = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern DIGIT = Pattern.compile("\\p{N}");
    private static final Pattern INITIAL_COMPOUND = Pattern.compile("^\\p{Lu}[-‐‑]\\p{Ll}\\p{L}*$");
    private static final Pattern LOWER_AFTER_HYPHEN = Pattern.compile("^\\p{Lu}\\p{L}+[-‐‑]\\p{Ll}\\p{L}*$");

    /** A line of a book's own printing notes: rights, ISBN, imprint, web address, credit line. */
    private static final Pattern PRINTING_NOTE = Pattern.compile("(?iu)©|\\bcopyright\\b|\\ball rights reserved\\b"
            + "|\\bisbn\\b|\\bfirst (published|edition|printing)\\b|\\blibrary of congress\\b"
            + "|\\b(published|printed|distributed|typeset|illustrated|cover design) (by|in|for)\\b"
            + "|\\bwww\\.|https?://|\\b[\\w.-]+@[\\w-]+\\.\\w+|^(written |translated )?by\\s+\\S+(\\s+\\S+){0,2}$");

    /**
     * How likely a term is not a name, from its shape and vocabulary alone.
     *
     * @param term the term as written; never null
     * @return zero when nothing marks it, otherwise a score where {@link #DROP_SCORE} and above is junk a scan must
     *     not propose and {@link #MARK_SCORE} and above is worth a second look
     */
    public static int likelihood(final String term) {
        Objects.requireNonNull(term, "term");
        final String[] words = WHITESPACE.split(term.strip());
        int score = 0;
        if (isAllCaps(words)) {
            score += STRONG;
        }
        if (isPrintingNoteTerm(term)) {
            score += STRONG;
        }
        if (words.length == 1 && INITIAL_COMPOUND.matcher(words[0]).matches()) {
            score += STRONG;
        }
        if (words.length == 1 && LOWER_AFTER_HYPHEN.matcher(words[0]).matches()) {
            score += WEAK;
        }
        if (DIGIT.matcher(term).find()) {
            score += WEAK;
        }
        if (words.length == 1 && letterCount(words[0]) <= SHORT_WORD_LETTERS) {
            score += WEAK;
        }
        return score;
    }

    /**
     * Whether a scan must leave the term out.
     *
     * @param term the term as written; never null
     * @return {@code true} if its {@link #likelihood} is at least {@link #DROP_SCORE}, {@code false} otherwise
     */
    public static boolean isJunk(final String term) {
        return likelihood(term) >= DROP_SCORE;
    }

    /**
     * Whether a line of running text is the book's own printing note, which holds the author's and the publisher's
     * names rather than the story's.
     *
     * @param line one line of a segment's text; never null
     * @return {@code true} for a short line that reads as rights, imprint, address or credit text, {@code false}
     *     otherwise
     */
    public static boolean isPrintingNote(final String line) {
        Objects.requireNonNull(line, "line");
        return !line.isBlank()
                && WHITESPACE.split(line.strip()).length <= MAX_NOTE_WORDS
                && PRINTING_NOTE.matcher(line.strip()).find();
    }

    // Every word has at least two letters and none is lower case: CROYDON, NASA, but not I or A.
    private static boolean isAllCaps(final String[] words) {
        for (final String word : words) {
            final boolean allUpper =
                    word.codePoints().filter(Character::isLetter).allMatch(Character::isUpperCase);
            if (letterCount(word) < SHORT_WORD_LETTERS || !allUpper) {
                return false;
            }
        }
        return true;
    }

    private static int letterCount(final String word) {
        return (int) word.codePoints().filter(Character::isLetter).count();
    }

    private static boolean isPrintingNoteTerm(final String term) {
        final String plain = PUNCTUATION
                .matcher(term.toLowerCase(Locale.ROOT))
                .replaceAll(" ")
                .strip();
        final String last = plain.substring(plain.lastIndexOf(' ') + 1);
        return NOTE_TERMS.contains(plain) || (plain.contains(" ") && NOTE_LAST_WORDS.contains(last));
    }
}
