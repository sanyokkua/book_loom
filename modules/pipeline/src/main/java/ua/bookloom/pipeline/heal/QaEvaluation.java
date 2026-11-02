package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.QaEvaluator;
import ua.bookloom.pipeline.qa.QaResult;
import ua.bookloom.pipeline.qa.SoftCheckInput;

/**
 * Builds the {@link SoftCheckInput} the quality loop's own evaluations share, so the initial draft evaluation, every
 * self-heal round and backward revision's re-render build it the same way instead of each repeating the field list.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QaEvaluation {

    /**
     * Evaluates one candidate target against every hard gate and soft check.
     *
     * @param givenHardGates the hard-gate failures the gate call already found for this candidate; empty when
     *     the gate passed
     * @param subject the segment being evaluated, its masked source and the locked glossary terms present in it
     * @param maskedCandidate the candidate as the model wrote it, protected-span tokens still in it, so a hidden name
     *     never counts toward the script, echo or length checks
     * @param maskedForm the candidate with every protected span restored and the document's own tokens still in
     *     place, where the glossary check looks for each locked rendering; each token reads as one space there, so a
     *     footnote marker never glues the word before it to the digit after it
     * @param context the languages, policies and glossary lines the soft checks read
     * @return the candidate's hard-gate and soft outcome
     */
    public static QaResult evaluate(
            final List<CheckResult> givenHardGates,
            final DraftSubject subject,
            final String maskedCandidate,
            final String maskedForm,
            final SoftContext context) {
        Objects.requireNonNull(givenHardGates, "givenHardGates");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(maskedCandidate, "maskedCandidate");
        Objects.requireNonNull(maskedForm, "maskedForm");
        Objects.requireNonNull(context, "context");
        final CallFrame frame = context.frame();
        final SoftCheckInput input = new SoftCheckInput(
                DisplayText.of(subject.maskedSource()),
                DisplayText.of(maskedCandidate),
                Tokens.replace(maskedForm, " "),
                frame.sourceLanguage(),
                frame.targetLanguage(),
                frame.foreignPassagePolicy(),
                context.namePolicy(),
                subject.segment().declaredLanguage(),
                context.glossaryTerms(),
                subject.lockedRenderings(),
                frame.narrator(),
                context.glossaryPairs(),
                context.characters(),
                context.unitRole());
        return QaEvaluator.evaluate(givenHardGates, input, frame.wordValidator());
    }
}
