package ua.bookloom.pipeline.lexicon;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Reads a target text as the words the {@link CooccurrenceLearner} counts: each word of three letters or more with its
 * stem, and whether it is capitalised away from a sentence start, which is how a name looks.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class TargetWords {

    /** How many leading letters of a lower-cased word are its stem. */
    static final int STEM_LENGTH = 4;

    private static final int MIN_LETTERS = 3;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{M}]+(?:['’ʼ-][\\p{L}\\p{M}]+)*");
    private static final Pattern GAP_END = Pattern.compile("[\\s\"“”«»'‘’—–-]+$");
    private static final String SENTENCE_ENDS = ".!?…:";

    /** A target word in a segment: its lower-cased surface form and the stem it counts under. */
    record Word(String surface, String stem) {}

    // A word is a candidate unless it is capitalised away from a sentence start, which is how a name looks.
    static List<Word> wordsOf(final String text) {
        return tokensOf(text).stream()
                .filter(token -> !token.name())
                .map(Token::word)
                .toList();
    }

    // The capitalised words, at a sentence start or not: the candidates for the rendering of a name.
    static List<Word> namesOf(final String text) {
        return tokensOf(text).stream()
                .filter(Token::capitalised)
                .map(Token::word)
                .toList();
    }

    static List<Word> namesAwayFromSentenceStart(final String text) {
        return tokensOf(text).stream().filter(Token::name).map(Token::word).toList();
    }

    static Set<String> stemsOf(final List<Word> found) {
        return found.stream().map(Word::stem).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private record Token(Word word, boolean capitalised, boolean name) {}

    private static List<Token> tokensOf(final String text) {
        final List<Token> tokens = new ArrayList<>();
        final Matcher matcher = WORD.matcher(text);
        int previousEnd = 0;
        boolean first = true;
        while (matcher.find()) {
            final String gap = GAP_END.matcher(text.substring(previousEnd, matcher.start()))
                    .replaceFirst("");
            final boolean sentenceStart =
                    gap.isEmpty() ? first : SENTENCE_ENDS.indexOf(gap.charAt(gap.length() - 1)) >= 0;
            previousEnd = matcher.end();
            first = false;
            final String word = matcher.group();
            final boolean capitalised = Character.isUpperCase(word.codePointAt(0));
            final String surface = word.toLowerCase(Locale.ROOT);
            if (surface.length() >= MIN_LETTERS) {
                final Word found = new Word(surface, surface.substring(0, Math.min(STEM_LENGTH, surface.length())));
                tokens.add(new Token(found, capitalised, capitalised && !sentenceStart));
            }
        }
        return tokens;
    }
}
