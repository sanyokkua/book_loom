package ua.bookloom.pipeline.reviewer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.pipeline.Tokens;

/**
 * Verifies one reviewer edit in code instead of asking the model again: the quote is a unique substring of the
 * candidate after normalisation, the replacement keeps the {@code ⟦gN⟧} multiset and order of the whole text, the
 * deterministic checks pass on the edited text, and the set of blocking checks does not grow. The reviewer proposes;
 * only an edit that passes all of these changes a word of the book.
 */
@Slf4j
public final class EditVerifier {

    /**
     * Verifies one edit against the candidate it quotes.
     *
     * @param candidate the whole candidate in masked form; never null
     * @param edit the edit as the reviewer wrote it; never null
     * @param checker runs the deterministic checks on a whole candidate; never null
     * @param baseline the names of the checks that already block the candidate, which an edit may keep or shrink but
     *     never grow; never null
     * @return {@link Verification.Verified} with the edited text, {@link Verification.Ignored} for a quote the
     *     candidate does not hold, or {@link Verification.Failed} naming why a quoted edit was refused
     */
    public Verification verify(
            final String candidate, final ReviewEdit edit, final CandidateChecker checker, final Set<String> baseline) {
        return verify(candidate, edit, checker, baseline, List.of());
    }

    /**
     * Verifies one edit as {@link #verify(String, ReviewEdit, CandidateChecker, Set)} does, knowing the glossary
     * renderings of the chunk, which a terminology edit may use.
     *
     * @param renderings the glossary renderings (targets) of the chunk; never null
     * @return the verification, as above
     */
    public Verification verify(
            final String candidate,
            final ReviewEdit edit,
            final CandidateChecker checker,
            final Set<String> baseline,
            final List<String> renderings) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(edit, "edit");
        Objects.requireNonNull(checker, "checker");
        Objects.requireNonNull(baseline, "baseline");
        final NormalisedText text = NormalisedText.of(candidate);
        final NormalisedText quote = NormalisedText.of(edit.quote());
        final int occurrences = text.count(quote);
        log.debug("Verifying edit criterion={} occurrences={}", edit.criterion().wire(), occurrences);
        if (occurrences == 0) {
            return new Verification.Ignored("the quote is not in the candidate");
        }
        if (occurrences > 1) {
            return failed(Verification.FailureReason.AMBIGUOUS_QUOTE);
        }
        if (!CriterionFit.fits(edit, candidate, renderings)) {
            return new Verification.Ignored(
                    "the change does not fit its criterion " + edit.criterion().wire());
        }
        final String edited = text.replace(candidate, quote, edit.replacement());
        if (edited.equals(candidate)) {
            return new Verification.Ignored("the edit changes nothing");
        }
        return checkEdited(candidate, edited, checker, baseline);
    }

    /**
     * Whether a quote still occurs in a text, matched the way {@link #verify} matches it.
     *
     * @param text the whole text; never null
     * @param quote the quote; never null
     * @return {@code true} when the quote is found at least once after normalisation
     */
    public static boolean occursIn(final String text, final String quote) {
        return NormalisedText.of(text).count(NormalisedText.of(quote)) > 0;
    }

    private static Verification checkEdited(
            final String candidate, final String edited, final CandidateChecker checker, final Set<String> baseline) {
        if (!Tokens.inOrder(edited).equals(Tokens.inOrder(candidate))) {
            return failed(Verification.FailureReason.TOKENS_CHANGED);
        }
        final Optional<Set<String>> blockers = checker.blockersOf(edited);
        if (blockers.isEmpty()) {
            return failed(Verification.FailureReason.CHECKS_REFUSED);
        }
        if (!baseline.containsAll(blockers.get())) {
            log.debug("Edit refused: blockers grew from {} to {}", baseline, blockers.get());
            return failed(Verification.FailureReason.BLOCKERS_GREW);
        }
        return new Verification.Verified(edited);
    }

    private static Verification failed(final Verification.FailureReason reason) {
        log.debug("Edit refused reason={}", reason);
        return new Verification.Failed(reason);
    }
}
