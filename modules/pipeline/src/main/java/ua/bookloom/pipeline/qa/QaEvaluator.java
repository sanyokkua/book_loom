package ua.bookloom.pipeline.qa;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ua.bookloom.api.project.QaFinding;

/**
 * Runs every quality-gate check for one segment's restored candidate: the refusal gate this package owns, the five
 * soft checks, and the placeholder/protected-span hard-gate results a caller computed elsewhere.
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
        Objects.requireNonNull(givenHardGates, "givenHardGates");
        Objects.requireNonNull(input, "input");
        final CheckResult refusal = RefusalGate.run(input);
        final List<CheckResult> hardGates =
                Stream.concat(givenHardGates.stream(), Stream.of(refusal)).toList();
        final List<CheckResult> soft = SoftChecks.run(input);
        return new QaResult(
                hardGates,
                soft,
                Confidence.blend(soft),
                findings(hardGates, soft),
                soft.stream().anyMatch(CheckResult::blocking));
    }

    private static List<QaFinding> findings(final List<CheckResult> hardGates, final List<CheckResult> soft) {
        return Stream.concat(hardGates.stream(), soft.stream())
                .map(CheckResult::finding)
                .filter(Objects::nonNull)
                .toList();
    }
}
