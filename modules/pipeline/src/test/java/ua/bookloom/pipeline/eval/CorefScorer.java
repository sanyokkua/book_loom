package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.eval.CorefReport.Outcome;
import ua.bookloom.pipeline.eval.CorefWindow.Pronoun;
import ua.bookloom.pipeline.eval.CorefWindow.Referent;

/** Scores a {@link CorefReader} against the gold of the coreference windows. */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CorefScorer {

    /**
     * Asks the reader about each window and scores its claims. A window is wrong when any of its votes is wrong,
     * right when it has a vote and none is wrong, and an abstention otherwise.
     *
     * @param windows the windows, in order
     * @param reader the model, or a script standing in for it
     * @return one row per window
     */
    static CorefReport score(final List<CorefWindow> windows, final CorefReader reader) {
        Objects.requireNonNull(windows, "windows");
        Objects.requireNonNull(reader, "reader");
        return new CorefReport(
                windows.stream().map(window -> scoreWindow(window, reader)).toList());
    }

    private static CorefReport.Row scoreWindow(final CorefWindow window, final CorefReader reader) {
        final String text = window.text();
        final List<CorefClaim> votes = reader.read(window.target(), text).stream()
                .filter(CorefClaim::refersToTerm)
                .filter(claim -> !claim.quote().isBlank() && text.contains(claim.quote()))
                .toList();
        final boolean anyWrong = votes.stream().anyMatch(claim -> !isRight(claim, window));
        final Outcome outcome =
                anyWrong ? Outcome.WRONG_VOTE : votes.isEmpty() ? Outcome.ABSTENTION : Outcome.RIGHT_REFERENT;
        return new CorefReport.Row(window.id(), outcome);
    }

    /** Right only when the quote fits at least one gold pronoun and every pronoun it fits is the target's. */
    private static boolean isRight(final CorefClaim claim, final CorefWindow window) {
        final String text = window.text();
        final List<Pronoun> matched = window.pronouns().stream()
                .filter(pronoun -> fits(claim.quote(), pronoun, text))
                .toList();
        return !matched.isEmpty() && matched.stream().allMatch(pronoun -> pronoun.referent() == Referent.TARGET);
    }

    /**
     * A claim fits a gold pronoun when it holds the pronoun word as a whole word and one place where the claim stands
     * in the window text overlaps the place of the gold snippet, so "he" inside "She", or a "he" in another
     * sentence, is not the gold "he".
     */
    private static boolean fits(final String claim, final Pronoun gold, final String text) {
        final int goldStart = text.indexOf(gold.quote());
        if (goldStart < 0 || !hasWord(claim, gold.pronoun())) {
            return false;
        }
        final int goldEnd = goldStart + gold.quote().length();
        for (int at = text.indexOf(claim); at >= 0; at = text.indexOf(claim, at + 1)) {
            if (isWholeAt(text, at, claim.length()) && at < goldEnd && goldStart < at + claim.length()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasWord(final String text, final String word) {
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}])")
                .matcher(text)
                .find();
    }

    /** Whether the stretch starts and ends on word edges wherever its own first and last characters are word characters. */
    private static boolean isWholeAt(final String text, final int start, final int length) {
        final int end = start + length;
        final boolean startOk = !isWordChar(text.charAt(start)) || start == 0 || !isWordChar(text.charAt(start - 1));
        final boolean endOk =
                !isWordChar(text.charAt(end - 1)) || end == text.length() || !isWordChar(text.charAt(end));
        return startOk && endOk;
    }

    private static boolean isWordChar(final char c) {
        return Character.isLetterOrDigit(c);
    }
}
