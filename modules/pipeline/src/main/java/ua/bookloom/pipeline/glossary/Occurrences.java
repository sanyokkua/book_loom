package ua.bookloom.pipeline.glossary;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.Tokens;

/**
 * Finds the capitalised words and runs of one segment that do not start a sentence. Each placeholder token reads as
 * one space, so {@code Hale⟦g3⟧Street} is the run {@code Hale Street}; {@code DisplayText} would glue it into one
 * word. A lone capital letter (the English pronoun {@code I}) is never a name.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class Occurrences {

    /** English titles whose full stop does not end a sentence; a title is never a name itself. */
    static final Set<String> HONORIFICS = Set.of("Mr", "Mrs", "Ms", "Dr", "St", "Prof");

    private static final int MAX_RUN_WORDS = 3;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern SPACES = Pattern.compile("\\p{Zs}+");
    private static final Pattern SENTENCE_GAP =
            Pattern.compile("[^\\p{L}\\p{N}]*[.!?…][\\p{Pf}\\p{Pe}\"']*\\s+[\\p{Ps}\\p{Pi}\"']*");
    private static final Pattern SENTENCE_TAIL = Pattern.compile("[\\s\\p{Ps}\\p{Pi}\"']+$");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /** One name found: the term and the sentence that holds this occurrence. */
    record Occurrence(String term, String sentence) {}

    private record Word(int start, int end, boolean capitalised, boolean initial, int sentence) {}

    static List<Occurrence> in(final String masked) {
        final String text = Tokens.replace(masked, " ");
        final List<Integer> sentenceStarts = new ArrayList<>();
        final List<Word> words = words(text, sentenceStarts);
        final List<String> sentences = sentences(text, sentenceStarts);
        final List<Occurrence> found = new ArrayList<>();
        int index = 0;
        while (index < words.size()) {
            final Word word = words.get(index);
            if (!word.capitalised() || word.initial()) {
                index++;
                continue;
            }
            final int runEnd = runEnd(text, words, index);
            if (runEnd == index + 1 && word.end() - word.start() == 1) {
                index = runEnd;
                continue;
            }
            found.add(new Occurrence(term(text, words, index, runEnd), sentences.get(word.sentence())));
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
            final boolean honorific = isHonorific(text, matcher.start(), matcher.end());
            final boolean capitalised = !honorific && Character.isUpperCase(text.codePointAt(matcher.start()));
            words.add(new Word(matcher.start(), matcher.end(), capitalised, initial, sentenceStarts.size() - 1));
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
        return !afterHonorific
                && SENTENCE_GAP.matcher(text.substring(previousEnd, start)).matches();
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
        return sentences;
    }

    private static int runEnd(final String text, final List<Word> words, final int first) {
        int end = first + 1;
        while (end < words.size()
                && end - first < MAX_RUN_WORDS
                && joinsRun(text, words.get(end - 1), words.get(end))) {
            end++;
        }
        return end;
    }

    private static boolean joinsRun(final String text, final Word previous, final Word next) {
        return next.capitalised()
                && SPACES.matcher(text.substring(previous.end(), next.start())).matches();
    }

    private static String term(final String text, final List<Word> words, final int from, final int to) {
        final List<String> parts = new ArrayList<>();
        for (int i = from; i < to; i++) {
            parts.add(text.substring(words.get(i).start(), words.get(i).end()));
        }
        return String.join(" ", parts);
    }
}
