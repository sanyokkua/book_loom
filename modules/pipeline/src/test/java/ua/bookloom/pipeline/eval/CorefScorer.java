package ua.bookloom.pipeline.eval;

import java.util.List;
import java.util.Objects;
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
        final List<Pronoun> matched = window.pronouns().stream()
                .filter(pronoun -> fits(claim.quote(), pronoun.quote()))
                .toList();
        return !matched.isEmpty() && matched.stream().allMatch(pronoun -> pronoun.referent() == Referent.TARGET);
    }

    private static boolean fits(final String claimQuote, final String goldQuote) {
        return goldQuote.contains(claimQuote) || claimQuote.contains(goldQuote);
    }
}
