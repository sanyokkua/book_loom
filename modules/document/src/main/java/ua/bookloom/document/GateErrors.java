package ua.bookloom.document;

import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.AppError;
import ua.bookloom.api.ErrorCode;
import ua.bookloom.api.SafeDetails;
import ua.bookloom.document.mask.GateOutcome;
import ua.bookloom.document.mask.GateRule;

/** The {@code validation} error a refused placeholder gate answers, one message per rule the target broke. */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GateErrors {

    private static final String TITLE = "This translation could not be restored";

    /**
     * Builds the rendered details once and logs <em>that</em>, rather than the raw token lists. The observed list
     * is scanned out of a provider response, so its size and its tokens' lengths are the model's choice, not the
     * system's; {@link SafeDetails#withPlaceholderMultiset} is where that side is bounded, and logging its output
     * is what keeps the same bound on the log line.
     */
    static AppError of(final GateOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        final String details = SafeDetails.empty()
                .withPlaceholderMultiset(outcome.expected(), outcome.observed())
                .render();
        log.warn("Refused a translated segment: placeholder rule {} broken: {}", outcome.failedRule(), details);
        return AppError.of(
                ErrorCode.validation,
                TITLE,
                messageFor(Objects.requireNonNullElse(outcome.failedRule(), GateRule.MULTISET)),
                details,
                null);
    }

    /** No placement of the missing tokens passes the gate; logged by the caller, which knows the segment. */
    static AppError unrepairable() {
        return AppError.of(
                ErrorCode.validation,
                TITLE,
                "The translated text's formatting placeholders could not be put back without a model: no position"
                        + " for the missing ones keeps the formatting around the same words.");
    }

    private static String messageFor(final GateRule rule) {
        return switch (rule) {
            case MULTISET ->
                "The translated text's formatting placeholders do not match the original segment's — one or more"
                        + " were dropped, duplicated, or invented. Nothing was restored.";
            case STRAY_BRACKET ->
                "The translated text holds a broken formatting placeholder — a bracket left over from a moved, split"
                        + " or misspelt placeholder would reach the book as text. Nothing was restored.";
            case PAIR_SPAN ->
                "The translated text moved a piece of formatting onto other words — it now wraps a much smaller or"
                        + " larger part of the passage than in the original. Nothing was restored.";
            case TEXT_OUTSIDE_PAIRS ->
                "The translated text put all of its words inside one piece of formatting that wrapped only part of"
                        + " the original — the formatting would spread over the whole passage. Nothing was restored.";
            case PAIR_ORDER, EMPTIED_PAIR, LINE_BREAK ->
                "The translated text moved, swapped or emptied the formatting around its words — a pair of"
                        + " placeholders no longer wraps the same text. Nothing was restored.";
        };
    }
}
