package ua.bookloom.pipeline.heal;

import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.document.Segment;
import ua.bookloom.pipeline.DisplayText;
import ua.bookloom.pipeline.qa.CheckResult;
import ua.bookloom.pipeline.qa.LockedRendering;
import ua.bookloom.pipeline.qa.QaEvaluator;
import ua.bookloom.pipeline.qa.QaResult;
import ua.bookloom.pipeline.qa.SoftCheckInput;

/**
 * Builds the {@link SoftCheckInput} the quality loop's own evaluations share, so the initial draft evaluation and
 * every self-heal round build it the same way instead of each repeating the field list.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class QaEvaluation {

    /**
     * Evaluates one candidate target against every hard gate and soft check.
     *
     * @param givenHardGates the hard-gate failures the gate call already found for this candidate; empty when
     *     the gate passed
     * @param segment the segment being evaluated, read here only for its declared language
     * @param maskedSource the segment's masked source
     * @param maskedCandidate the candidate's masked form — protected spans restored, the document's own tokens still
     *     in place
     * @param settings the chunk's languages, policies and glossary terms
     * @param lockedRenderings the locked terms and kept foreign runs masked in this segment
     * @return the candidate's hard-gate and soft outcome
     */
    static QaResult evaluate(
            final List<CheckResult> givenHardGates,
            final Segment segment,
            final String maskedSource,
            final String maskedCandidate,
            final LoopSettings settings,
            final List<LockedRendering> lockedRenderings) {
        Objects.requireNonNull(givenHardGates, "givenHardGates");
        Objects.requireNonNull(segment, "segment");
        Objects.requireNonNull(maskedSource, "maskedSource");
        Objects.requireNonNull(maskedCandidate, "maskedCandidate");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(lockedRenderings, "lockedRenderings");
        final SoftCheckInput input = new SoftCheckInput(
                DisplayText.of(maskedSource),
                DisplayText.of(maskedCandidate),
                settings.frame().sourceLanguage(),
                settings.frame().targetLanguage(),
                settings.frame().foreignPassagePolicy(),
                settings.namePolicy(),
                segment.declaredLanguage(),
                settings.glossaryTerms(),
                lockedRenderings);
        return QaEvaluator.evaluate(givenHardGates, input);
    }
}
