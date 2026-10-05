package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.api.project.NamePolicy;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.Tokens;
import ua.bookloom.pipeline.prompt.CallFrame;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.LockedRendering;
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
     * @param segment the segment being evaluated, read here only for its declared language
     * @param maskedSource the segment's masked source
     * @param maskedCandidate the candidate as the model wrote it, protected-span tokens still in it, so a hidden name
     *     never counts toward the script, echo or length checks
     * @param maskedForm the candidate with every protected span restored and the document's own tokens still in
     *     place, where the glossary check looks for each locked rendering; each token reads as one space there, so a
     *     footnote marker never glues the word before it to the digit after it
     * @param settings the chunk's languages, policies and glossary terms
     * @param lockedRenderings the locked glossary terms present in this segment
     * @return the candidate's hard-gate and soft outcome
     */
    static QaResult evaluate(
            final List<CheckResult> givenHardGates,
            final Segment segment,
            final String maskedSource,
            final String maskedCandidate,
            final String maskedForm,
            final LoopSettings settings,
            final List<LockedRendering> lockedRenderings) {
        Objects.requireNonNull(settings, "settings");
        return evaluate(
                givenHardGates,
                segment,
                maskedSource,
                maskedCandidate,
                maskedForm,
                settings.frame(),
                settings.namePolicy(),
                settings.glossaryTerms(),
                lockedRenderings);
    }

    /**
     * Evaluates one candidate target outside a chunk's quality loop, where no review mode or dial applies — the
     * checks read only the languages, the policies and the glossary.
     *
     * @param givenHardGates the hard-gate failures the gate call already found; empty when the gate passed
     * @param segment the segment being evaluated, read here only for its declared language
     * @param maskedSource the segment's masked source
     * @param maskedCandidate the candidate as the model wrote it
     * @param maskedForm the candidate with every protected span restored and the document's own tokens in place
     * @param frame the languages and the foreign-passage policy
     * @param namePolicy the Book Brief's name policy
     * @param glossaryTerms every glossary term, read by the name removal of the script and echo checks
     * @param lockedRenderings the locked glossary terms present in this segment
     * @return the candidate's hard-gate and soft outcome
     */
    public static QaResult evaluate(
            final List<CheckResult> givenHardGates,
            final Segment segment,
            final String maskedSource,
            final String maskedCandidate,
            final String maskedForm,
            final CallFrame frame,
            final NamePolicy namePolicy,
            final List<String> glossaryTerms,
            final List<LockedRendering> lockedRenderings) {
        Objects.requireNonNull(givenHardGates, "givenHardGates");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(maskedSource, "maskedSource");
        Objects.requireNonNull(maskedCandidate, "maskedCandidate");
        Objects.requireNonNull(maskedForm, "maskedForm");
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(namePolicy, "namePolicy");
        Objects.requireNonNull(glossaryTerms, "glossaryTerms");
        Objects.requireNonNull(lockedRenderings, "lockedRenderings");
        final SoftCheckInput input = new SoftCheckInput(
                DisplayText.of(maskedSource),
                DisplayText.of(maskedCandidate),
                Tokens.replace(maskedForm, " "),
                frame.sourceLanguage(),
                frame.targetLanguage(),
                frame.foreignPassagePolicy(),
                namePolicy,
                segment.declaredLanguage(),
                glossaryTerms,
                lockedRenderings,
                frame.narrator());
        return QaEvaluator.evaluate(givenHardGates, input);
    }
}
