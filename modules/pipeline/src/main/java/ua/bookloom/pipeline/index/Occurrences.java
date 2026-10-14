package ua.bookloom.pipeline.index;

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
 * <p>An apostrophe or a hyphen between letters keeps one word ({@code Don’t}, {@code O'Brien}, {@code El-Barun}), but
 * a trailing possessive does not belong to the name ({@code Ashdown’s} is {@code Ashdown}). A sentence starts after
 * a full stop, a question or exclamation mark or an ellipsis, and also after a dash or a quotation mark, since in
 * fiction a capital there is as likely to open speech as to be a name.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Occurrences {

    /** English titles whose full stop does not end a sentence; a title is never a name itself. */
    private static final Set<String> HONORIFICS = Set.of("Mr", "Mrs", "Ms", "Dr", "St", "Prof");

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
    public record Occurrence(String term, String sentence) {}

    /**
     * One word of a segment.
     *
     * @param text the word as written, without a trailing possessive
     * @param start where it starts in the segment's text
     * @param end where it ends, after a trailing possessive
     * @param possessive whether it carries a trailing possessive, which ends a name run
     * @param capitalised whether it starts with a capital and is not a title such as {@code Mr.}
     * @param initial whether it starts a sentence
     * @param sentence the index of its sentence
     */
    public record Word(
            String text, int start, int end, boolean possessive, boolean capitalised, boolean initial, int sentence) {

        /** The key a word's counts are kept under. */
        public String key() {
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
    public record Read(String text, List<Word> words, List<String> sentences) {}

    /**
     * The key a word is counted and looked up under: lower case, with every apostrophe read as {@code '}.
     *
     * @param word the word as written; never null
     * @return the key
     */
    public static String keyOf(final String word) {
        return APOSTROPHES.matcher(word.toLowerCase(Locale.ROOT)).replaceAll("'");
    }

    /**
     * Reads a segment's masked text into its words and sentences.
     *
     * @param masked the segment's masked text; never null
     * @return the read; never null
     */
    public static Read read(final String masked) {
        final String text = Tokens.replace(masked, " ");
        final List<Integer> sentenceStarts = new ArrayList<>();
        final List<Word> words = words(text, sentenceStarts);
        return new Read(text, List.copyOf(words), sentences(text, sentenceStarts));
    }

    /**
     * The runs of a read segment: a word the test accepts, followed by up to two more accepted words separated from it
     * by spaces only; a possessive ends a run, so {@code Ember’s Zurich} is two names.
     *
     * @param read the segment
     * @param nameLike whether a word may be (part of) a name
     * @return the runs in order; never null
     */
    public static List<Occurrence> runs(final Read read, final Predicate<Word> nameLike) {
        return spans(read, nameLike).stream()
                .map(span -> new Occurrence(
                        term(read.words(), span.from(), span.to()),
                        read.sentences().get(read.words().get(span.from()).sentence())))
                .toList();
    }

    /**
     * The names a segment writes as two runs joined by a joining word ({@code Tower of Arlen}), with at most one
     * determiner after it ({@code Keeper of the Grey Gate}): the joining word and the determiner in lower case, the
     * first run not ending in a possessive, everything in one sentence and separated by spaces only. The two runs are
     * still found on their own by {@link #runs}.
     *
     * @param read the segment
     * @param nameLike whether a word may be (part of) a name
     * @param joiners the lower-case joining words of the language; empty finds nothing
     * @param determiners the lower-case determiners that may follow a joining word
     * @return the joined names in order, each written with single spaces; never null
     */
    public static List<Occurrence> joinedRuns(
            final Read read, final Predicate<Word> nameLike, final Set<String> joiners, final Set<String> determiners) {
        final List<Span> spans = spans(read, nameLike);
        final List<Occurrence> found = new ArrayList<>();
        for (int i = 0; i + 1 < spans.size() && !joiners.isEmpty(); i++) {
            final Span first = spans.get(i);
            final Span second = spans.get(i + 1);
            if (isJoined(read, first, second, joiners, determiners)) {
                found.add(new Occurrence(
                        term(read.words(), first.from(), second.to()),
                        read.sentences().get(read.words().get(first.from()).sentence())));
            }
        }
        return found;
    }

    /** A run's words, {@code from} inclusive and {@code to} exclusive. */
    private record Span(int from, int to) {}

    private static List<Span> spans(final Read read, final Predicate<Word> nameLike) {
        final List<Word> words = read.words();
        final List<Span> found = new ArrayList<>();
        int index = 0;
        while (index < words.size()) {
            final Word word = words.get(index);
            if (!nameLike.test(word)) {
                index++;
                continue;
            }
            final int runEnd = runEnd(read.text(), words, index, nameLike);
            if (runEnd > index + 1 || word.text().codePointCount(0, word.text().length()) > 1) {
                found.add(new Span(index, runEnd));
            }
            index = runEnd;
        }
        return found;
    }

    // One joining word, then at most one determiner, all lower case, in the first run's sentence, spaces between.
    private static boolean isJoined(
            final Read read,
            final Span first,
            final Span second,
            final Set<String> joiners,
            final Set<String> determiners) {
        final List<Word> words = read.words();
        final int between = second.from() - first.to();
        if (between < 1 || between > 2 || words.get(first.to() - 1).possessive()) {
            return false;
        }
        final Word joiner = words.get(first.to());
        final boolean determinerFits = between == 1 || isDeterminer(words.get(first.to() + 1), determiners);
        final boolean wordsFit = !joiner.capitalised() && joiners.contains(joiner.key()) && determinerFits;
        final boolean oneSentence =
                words.get(second.from()).sentence() == words.get(first.from()).sentence();
        return wordsFit && oneSentence && spacesOnly(read.text(), words, first.to() - 1, second.from());
    }

    private static boolean isDeterminer(final Word word, final Set<String> determiners) {
        return !word.capitalised() && determiners.contains(word.key());
    }

    private static boolean spacesOnly(final String text, final List<Word> words, final int from, final int to) {
        for (int i = from; i < to; i++) {
            if (!SPACES.matcher(
                            text.substring(words.get(i).end(), words.get(i + 1).start()))
                    .matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the word at {@code index} is a capital away from a sentence start and not beside another capitalised
     * word, which would make it a part of a name ({@code Great Hall}, {@code Old Bailey}) and say nothing about the
     * common word it also is.
     *
     * @param read the segment the word belongs to; never null
     * @param index the word's position in {@code read.words()}
     * @return {@code true} if the word stands alone, {@code false} otherwise
     */
    public static boolean isStandalone(final Read read, final int index) {
        final Word word = read.words().get(index);
        return word.capitalised() && !word.initial() && !hasCapitalNeighbour(read, index);
    }

    private static boolean hasCapitalNeighbour(final Read read, final int index) {
        final List<Word> words = read.words();
        final Word word = words.get(index);
        final boolean before = index > 0
                && words.get(index - 1).capitalised()
                && read.text()
                        .substring(words.get(index - 1).end(), word.start())
                        .isBlank();
        final boolean after = index + 1 < words.size()
                && words.get(index + 1).capitalised()
                && read.text()
                        .substring(word.end(), words.get(index + 1).start())
                        .isBlank();
        return before || after;
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
                    matcher.group(1),
                    matcher.start(),
                    matcher.end(),
                    matcher.end() > matcher.end(1),
                    capitalised,
                    initial,
                    sentenceStarts.size() - 1));
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
                && !words.get(end - 1).possessive()
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
