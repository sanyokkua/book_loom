package ua.bookloom.pipeline.qa;

import static ua.bookloom.pipeline.qa.CheckName.REFUSAL;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * The refusal hard gate: an apology, a disclaimer or a comment about the task must never reach the book, whatever
 * its confidence score (design D8). Fails a target whose display text is empty while its source's is not, or whose
 * trimmed display text opens with a refusal phrase of English, the source language or the target language — unless
 * the source's own display text already opens with a listed phrase (a book's own "Translation:" line is not a
 * refusal).
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RefusalGate {

    private static final double PASS_MARGIN = 1.0;
    private static final char CURLY_APOSTROPHE = '’';
    private static final char STRAIGHT_APOSTROPHE = '\'';

    static CheckResult run(final SoftCheckInput input) {
        Objects.requireNonNull(input, "input");
        final String source = normalize(input.sourceDisplayText());
        final String target = normalize(input.targetDisplayText());
        if (target.isEmpty() && !source.isEmpty()) {
            return CheckResult.hardGateFailed(REFUSAL, "target display text is empty while the source's is not");
        }
        final List<String> phrases = phrasesFor(input);
        if (startsWithAnyPhrase(source, phrases)) {
            return CheckResult.pass(REFUSAL, PASS_MARGIN);
        }
        if (startsWithAnyPhrase(target, phrases)) {
            return CheckResult.hardGateFailed(REFUSAL, "target opens with a refusal phrase");
        }
        return CheckResult.pass(REFUSAL, PASS_MARGIN);
    }

    /** English's phrases plus the source and target languages' own, each contributing none when uncatalogued. */
    private static List<String> phrasesFor(final SoftCheckInput input) {
        return Stream.of(
                        RefusalPhrases.EN.phrases(),
                        RefusalPhrases.phrasesFor(input.sourceLanguage()),
                        RefusalPhrases.phrasesFor(input.targetLanguage()))
                .flatMap(List::stream)
                .toList();
    }

    private static boolean startsWithAnyPhrase(final String text, final List<String> phrases) {
        return phrases.stream().anyMatch(phrase -> matchesPhrase(text, phrase));
    }

    /** {@code phrase} matches only when {@code text}'s end, or a non-letter/digit character, follows it. */
    private static boolean matchesPhrase(final String text, final String phrase) {
        if (text.length() < phrase.length()) {
            return false;
        }
        final String prefix = text.substring(0, phrase.length()).toLowerCase(Locale.ROOT);
        if (!prefix.equals(phrase.toLowerCase(Locale.ROOT))) {
            return false;
        }
        return text.length() == phrase.length() || !Character.isLetterOrDigit(text.charAt(phrase.length()));
    }

    private static String normalize(final String text) {
        return text.strip().replace(CURLY_APOSTROPHE, STRAIGHT_APOSTROPHE);
    }
}
