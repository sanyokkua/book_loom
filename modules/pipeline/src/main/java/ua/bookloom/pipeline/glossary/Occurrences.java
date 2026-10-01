package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.Tokens;

/**
 * Reads the words of one segment and finds its capitalised runs. Each placeholder token reads as one space, so
 * {@code Hale⟦g3⟧Street} is the run {@code Hale Street}; {@code DisplayText} would glue it into one word. A lone
 * capital letter (the English pronoun {@code I}) is never a name.
 *
 * <p>An apostrophe or a hyphen between letters keeps one word ({@code Don’t}, {@code O'Brien}, {@code Al-Arish}), but
 * a trailing possessive does not belong to the name ({@code Lovelace’s} is {@code Lovelace}). A sentence starts after
 * a full stop, a question or exclamation mark or an ellipsis, and also after a dash or a quotation mark, since in
 * fiction a capital there is as likely to open speech as to be a name.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Occurrences {

    /** English titles whose full stop does not end a sentence; a title is never a name itself. */
    static final Set<String> HONORIFICS = Set.of("Mr", "Mrs", "Ms", "Dr", "St", "Prof");

    private static final int MAX_RUN_WORDS = 3;
    private static final String LETTERS = "[\\p{L}\\p{M}\\p{N}]+";
    private static final String NOT_A_LETTER = "(?![\\p{L}\\p{M}\\p{N}])";
    private static final Pattern WORD = Pattern.compile("(" + LETTERS + "(?:['’ʼ](?![sS]" + NOT_A_LETTER + ")" + LETTERS
            + "|[-‐‑]" + LETTERS + ")*)(?:['’ʼ][sS]" + NOT_A_LETTER + ")?");
    private static final Pattern APOSTROPHES = Pattern.compile("[’ʼ]");
    private static final Pattern SPACES = Pattern.compile("\\p{Zs}+");
    private static final Pattern SENTENCE_GAP =
            Pattern.compile("[^\\p{L}\\p{N}]*[.!?…][\\p{Pf}\\p{Pe}\"']*\\s+[\\p{Ps}\\p{Pi}\"']*");
    private static final Pattern SPEECH_GAP = Pattern.compile(".*[—―\\p{Pi}\\p{Pf}\"'«»„].*", Pattern.DOTALL);
    private static final Pattern SENTENCE_TAIL = Pattern.compile("[\\s\\p{Ps}\\p{Pi}\"']+$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** One run found: the term and the sentence that holds this occurrence. */
    record Occurrence(String term, String sentence) {}

    /**
     * One word of a segment.
     *
     * @param text the word as written, without a trailing possessive
     * @param start where it starts in the segment's text
     * @param end where it ends, after a trailing possessive
     * @param capitalised whether it starts with a capital and is not a title such as {@code Mr.}
     * @param initial whether it starts a sentence
     * @param sentence the index of its sentence
     */
    record Word(String text, int start, int end, boolean capitalised, boolean initial, int sentence) {

        /** The key a word's counts are kept under. */
        String key() {
            return keyOf(text);
        }
    }

    /**
     * A segment read into words and sentences.
     *
     * @param text the segment's text with each placeholder token read as a space
     * @param words its words in order
     * @param sentences its sentences, whitespace collapsed, by index
     */
    record Read(String text, List<Word> words, List<String> sentences) {}

    /**
     * The key a word is counted and looked up under: lower case, with every apostrophe read as {@code '}.
     *
     * @param word the word as written; never null
     * @return the key
     */
    static String keyOf(final String word) {
        return APOSTROPHES.matcher(word.toLowerCase(Locale.ROOT)).replaceAll("'");
    }

    static Read read(final String masked) {
        final String text = Tokens.replace(masked, " ");
        final List<Integer> sentenceStarts = new ArrayList<>();
        final List<Word> words = words(text, sentenceStarts);
        return new Read(text, List.copyOf(words), sentences(text, sentenceStarts));
    }

    /**
     * The runs of a read segment: a word the test accepts, followed by up to two more accepted words separated from it
     * by spaces only.
     *
     * @param read the segment
     * @param nameLike whether a word may be (part of) a name
     * @return the runs in order; never null
     */
    static List<Occurrence> runs(final Read read, final Predicate<Word> nameLike) {
        final List<Word> words = read.words();
        final List<Occurrence> found = new ArrayList<>();
        int index = 0;
        while (index < words.size()) {
            final Word word = words.get(index);
            if (!nameLike.test(word)) {
                index++;
                continue;
            }
            final int runEnd = runEnd(read.text(), words, index, nameLike);
            if (runEnd > index + 1 || word.text().codePointCount(0, word.text().length()) > 1) {
                found.add(new Occurrence(
                        term(words, index, runEnd), read.sentences().get(word.sentence())));
            }
            index = runEnd;
        }
        return found;
    }

    private static List<Word> words(final String text, final List<Integer> sentenceStarts) {
        final List<Word> words = new ArrayList<>();
        final Matcher matcher = WORD.matcher(text);
        int previousEnd = -1;
        boolean previousHonorific = false;
        while (matcher.find()) {
            final boolean initial = startsSentence(text, previousEnd, matcher.start(), previousHonorific);
            if (initial) {
                sentenceStarts.add(matcher.start());
            }
            final boolean honorific = isHonorific(text, matcher.start(), matcher.end(1));
            final boolean capitalised = !honorific && Character.isUpperCase(text.codePointAt(matcher.start()));
            words.add(new Word(
                    matcher.group(1), matcher.start(), matcher.end(), capitalised, initial, sentenceStarts.size() - 1));
            previousEnd = matcher.end();
            previousHonorific = honorific;
        }
        return words;
    }

    private static boolean startsSentence(
            final String text, final int previousEnd, final int start, final boolean afterHonorific) {
        if (previousEnd < 0) {
            return true;
        }
        if (afterHonorific) {
            return false;
        }
        final String gap = text.substring(previousEnd, start);
        return SENTENCE_GAP.matcher(gap).matches() || SPEECH_GAP.matcher(gap).matches();
    }

    private static boolean isHonorific(final String text, final int start, final int end) {
        return end < text.length() && text.charAt(end) == '.' && HONORIFICS.contains(text.substring(start, end));
    }

    private static List<String> sentences(final String text, final List<Integer> starts) {
        final List<String> sentences = new ArrayList<>();
        for (int i = 0; i < starts.size(); i++) {
            final int end = i + 1 < starts.size() ? starts.get(i + 1) : text.length();
            final String raw =
                    SENTENCE_TAIL.matcher(text.substring(starts.get(i), end)).replaceFirst("");
            sentences.add(WHITESPACE.matcher(raw).replaceAll(" "));
        }
        return List.copyOf(sentences);
    }

    private static int runEnd(
            final String text, final List<Word> words, final int first, final Predicate<Word> nameLike) {
        int end = first + 1;
        while (end < words.size()
                && end - first < MAX_RUN_WORDS
                && nameLike.test(words.get(end))
                && SPACES.matcher(text.substring(
                                words.get(end - 1).end(), words.get(end).start()))
                        .matches()) {
            end++;
        }
        return end;
    }

    private static String term(final List<Word> words, final int from, final int to) {
        final List<String> parts = new ArrayList<>();
        for (int i = from; i < to; i++) {
            parts.add(words.get(i).text());
        }
        return String.join(" ", parts);
    }
}
