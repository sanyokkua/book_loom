package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ua.bookloom.api.Result;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.prompt.ModelCalls;
import ua.bookloom.pipeline.qa.QaResult;
import ua.bookloom.pipeline.reviewer.EditApplier;
import ua.bookloom.pipeline.reviewer.ReviewItem;

/**
 * Lets a caller outside the loop resolve a reviewer's answer about one draft with the run's own {@link ReviewResolver}
 * — the verified edits, the directed fix after a refused edit and the rewrite rule — instead of a copy of it.
 */
@Slf4j
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReviewJudge {

    /**
     * What the run would make of one reviewed draft.
     *
     * @param maskedText the masked text the segment ends with
     * @param qa the evaluation of that text
     * @param findings what the reviewer left on the segment
     * @param rounds repair rounds used: one when an edit, a rewrite or a directed fix changed the segment
     * @param verifiedBlockersLeft issues the reviewer evidenced with a quote the app found that nothing resolved
     * @param accepted whether the acceptance rule takes the end state
     */
    public record Judged(
            String maskedText,
            QaResult qa,
            List<QaFinding> findings,
            int rounds,
            int verifiedBlockersLeft,
            boolean accepted) {

        /** Copies the findings. */
        public Judged {
            Objects.requireNonNull(maskedText, "maskedText");
            Objects.requireNonNull(qa, "qa");
            findings = List.copyOf(findings);
        }
    }

    /**
     * Resolves the reviewer's answer for one draft that passed the checks.
     *
     * @param outcome the non-null draft the reviewer read
     * @param initialQa the non-null evaluation of that draft
     * @param item the non-null reviewer's answer for the draft's segment
     * @param settings the non-null loop settings of its chunk
     * @param gate the non-null gate the edited text is restored through
     * @param applier the non-null verified edit applier
     * @param directedFix the non-null directed fix a refused edit is given once
     * @param calls the non-null seam the directed fix is sent through
     * @return the resolution, or the error a directed-fix call answered
     */
    public static Result<Judged> resolve(
            final DraftOutcome.Drafted outcome,
            final QaResult initialQa,
            final ReviewItem item,
            final LoopSettings settings,
            final GateFunction gate,
            final EditApplier applier,
            final DirectedFix directedFix,
            final ModelCalls calls) {
        Objects.requireNonNull(settings, "settings");
        final ReviewResolver resolver =
                new ReviewResolver(applier, directedFix, new RoundEvaluator(gate, settings), settings, calls);
        return resolver.resolve(outcome, initialQa, item).map(resolution -> {
            final boolean accepted = AcceptanceRule.accepts(resolution.qa(), resolution.verifiedBlockersLeft());
            log.debug(
                    "Judged a reviewed draft segmentId={} accepted={}",
                    outcome.segment().id(),
                    accepted);
            return new Judged(
                    resolution.maskedText(),
                    resolution.qa(),
                    resolution.findings(),
                    resolution.rounds(),
                    resolution.verifiedBlockersLeft(),
                    accepted);
        });
    }
}
