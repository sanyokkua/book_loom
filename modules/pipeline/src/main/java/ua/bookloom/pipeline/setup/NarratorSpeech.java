package ua.bookloom.pipeline.setup;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Whether a quote is the narrator's own speech (15i.B4). A name or a word of address the narrator says ("Mind the
 * step, Lucy," I said) names the person spoken to, so it tells nothing of the narrator's gender; only the narrator's
 * own introduction ("I'm Lucy") names the narrator. The speaker is read from the attribution beside the quoted speech
 * that holds the quote: the narrator's subject pronoun in the words just after the closing mark, up to the next comma
 * or full stop, or in the same sentence before the opening mark. Only double quote marks are read; a book that marks
 * speech with single quotes or dashes keeps every vote, as before.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class NarratorSpeech {

    private static final char MARK = '"';
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern NOT_A_WORD = Pattern.compile("[^\\p{L}\\p{M}']+");
    private static final Pattern LEAD = Pattern.compile("^[\\s,.;:!?-]+");
    private static final Pattern AFTER_END = Pattern.compile("[.!?,;:\"\\n]");
    private static final String SENTENCE_ENDS = ".!?\"";
    // An introduction puts the name at most this many words after the pronoun: "I'm Lucy", "I am Lucy".
    private static final int INTRODUCTION_REACH = 2;

    /**
     * Whether the narrator speaks the quote to someone else.
     *
     * @param passages the window's passages the quote was found in
     * @param quote the quote, already found in the passages
     * @param name the name the model read from the quote; blank for a word of address
     * @param pronouns the source language's first-person subject pronouns; empty when the language has none listed,
     *     then nothing is read as the narrator's
     * @return {@code true} if the narrator says it and it is not the narrator's own introduction, {@code false}
     *     otherwise
     */
    static boolean isToAnother(
            final List<String> passages, final String quote, final String name, final Set<String> pronouns) {
        Objects.requireNonNull(passages, "passages");
        final Set<String> lower =
                pronouns.stream().map(word -> word.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        if (lower.isEmpty() || quote.isBlank()) {
            return false;
        }
        final String part = normalized(quote).replaceAll("^[\\s\".,;:!?-]+|[\\s\".,;:!?-]+$", "");
        final boolean saidByNarrator = passages.stream()
                .flatMap(passage -> Arrays.stream(passage.split("\\R")))
                .map(NarratorSpeech::normalized)
                .anyMatch(paragraph -> isSaidBy(paragraph, part, lower));
        final boolean introduces = !name.isBlank() && introduces(words(quote), words(name), lower);
        log.debug("Narrator speech: said by the narrator {}, an introduction {}", saidByNarrator, introduces);
        return saidByNarrator && !introduces;
    }

    private static boolean isSaidBy(final String paragraph, final String part, final Set<String> pronouns) {
        final int at = part.isEmpty() ? -1 : paragraph.indexOf(part);
        if (at < 0) {
            return false;
        }
        final long marksBefore =
                paragraph.substring(0, at).chars().filter(c -> c == MARK).count();
        if (marksBefore % 2 == 0) {
            return false;
        }
        final int open = paragraph.lastIndexOf(MARK, at);
        final int close = paragraph.indexOf(MARK, at + part.length());
        final String before = paragraph.substring(sentenceStart(paragraph, open), open);
        final String after = close < 0 ? "" : attribution(paragraph.substring(close + 1));
        log.trace("Narrator speech attribution before '{}' after '{}'", before, after);
        return hasPronoun(before, pronouns) || hasPronoun(after, pronouns);
    }

    private static int sentenceStart(final String paragraph, final int open) {
        int start = open;
        while (start > 0 && SENTENCE_ENDS.indexOf(paragraph.charAt(start - 1)) < 0) {
            start--;
        }
        return start;
    }

    private static String attribution(final String rest) {
        final String trimmed = LEAD.matcher(rest).replaceFirst("");
        final Matcher end = AFTER_END.matcher(trimmed);
        return end.find() ? trimmed.substring(0, end.start()) : trimmed;
    }

    private static boolean hasPronoun(final String text, final Set<String> pronouns) {
        return words(text).stream().anyMatch(word -> pronouns.contains(word) || isContraction(word, pronouns));
    }

    // "I'm", "I'd": the pronoun joined to its verb.
    private static boolean isContraction(final String word, final Set<String> pronouns) {
        final int apostrophe = word.indexOf('\'');
        return apostrophe > 0 && pronouns.contains(word.substring(0, apostrophe));
    }

    private static boolean introduces(
            final List<String> quoteWords, final List<String> nameWords, final Set<String> pronouns) {
        if (nameWords.isEmpty()) {
            return false;
        }
        for (int at = 0; at < quoteWords.size(); at++) {
            final String word = quoteWords.get(at);
            if (pronouns.contains(word) || isContraction(word, pronouns)) {
                final int reach = Math.min(quoteWords.size(), at + 1 + INTRODUCTION_REACH);
                if (quoteWords.subList(at + 1, reach).contains(nameWords.getFirst())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> words(final String text) {
        return Arrays.stream(NOT_A_WORD.split(normalized(text)))
                .map(word -> word.replaceAll("^'+|'+$", ""))
                .filter(word -> !word.isEmpty())
                .toList();
    }

    private static String normalized(final String text) {
        final String marks =
                text.replaceAll("[‘’‚‛′`]", "'").replaceAll("[“”„‟″«»]", "\"").replaceAll("[‐‑‒–—―]", "-");
        return SPACES.matcher(marks).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
