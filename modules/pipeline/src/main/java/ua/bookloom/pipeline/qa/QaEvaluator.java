package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.QaFinding;
import ua.bookloom.pipeline.checks.WordValidator;

/**
 * Runs every quality-gate check for one segment's restored candidate: the refusal gate this package owns, the
 * deterministic text checks (blocking ones fail like a hard gate, before any reviewer), the five soft checks, and the placeholder/protected-span hard-gate results a caller computed elsewhere.
 */
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class QaEvaluator {

    /**
     * Evaluates one segment's restored candidate against every hard gate and soft check.
     *
     * @param givenHardGates the placeholder and protected-span gate results already computed for this segment (e.g.
     *     a failed {@link CheckName#PLACEHOLDER} result built with {@link CheckResult#hardGateFailed}); may be
     *     empty when every prior gate passed
     * @param input the segment's display texts, languages and policies, shared with the refusal gate and the five
     *     soft checks
     * @return the segment's hard-gate and soft outcomes, its blended confidence, its findings, and whether a soft
     *     check failed outright
     */
    public static QaResult evaluate(final List<CheckResult> givenHardGates, final SoftCheckInput input) {
        return evaluate(givenHardGates, input, WordValidator.none());
    }

    /**
     * Evaluates one candidate as {@link #evaluate(List, SoftCheckInput)} does, with a word validator whose doubts
     * become one low {@code unknown-word} note.
     *
     * @param givenHardGates the gate results already computed for this segment
     * @param input the segment's display texts, languages and policies
     * @param words the run's word validator; never null
     * @return the segment's outcome
     */
    public static QaResult evaluate(
            final List<CheckResult> givenHardGates, final SoftCheckInput input, final WordValidator words) {
        Objects.requireNonNull(givenHardGates, "givenHardGates");
        Objects.requireNonNull(input, "input");
        final CheckResult refusal = RefusalGate.run(input);
        final List<CheckResult> hardGates = Stream.of(
                        givenHardGates.stream(), Stream.of(refusal), TextCheckGates.run(input, words).stream())
                .flatMap(results -> results)
                .toList();
        final List<CheckResult> soft = SoftChecks.run(input);
        return new QaResult(
                hardGates,
                soft,
                Confidence.blend(soft),
                findings(hardGates, soft),
                soft.stream().anyMatch(CheckResult::blocking));
    }

    /**
     * Runs only the deterministic text checks, the gender check and the word validator on one segment, as a run's gate
     * does before any reviewer reads it; the final audit asks again with them.
     *
     * @param input the segment's display texts, languages and policies
     * @param words the word validator; never null
     * @return one result per finding, blocking ones as failed hard gates and soft ones as passed results carrying a
     *     low finding; empty for a kept foreign passage and for a clean text
     */
    public static List<CheckResult> textChecks(final SoftCheckInput input, final WordValidator words) {
        return TextCheckGates.run(input, words);
    }

    private static List<QaFinding> findings(final List<CheckResult> hardGates, final List<CheckResult> soft) {
        return Stream.concat(hardGates.stream(), soft.stream())
                .map(CheckResult::finding)
                .filter(Objects::nonNull)
                .toList();
    }
}
